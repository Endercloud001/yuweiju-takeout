package com.codeying.service.impl;

import com.codeying.entity.CustomerServiceMessage;
import com.codeying.entity.CustomerServiceSession;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.CustomerServiceMessageMapper;
import com.codeying.mapper.CustomerServiceSessionMapper;
import com.codeying.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rule checks only; real proxy/SQL rollback is established by Issue15Probe. */
class CustomerServiceRulesTest {
    final CustomerServiceSessionMapper sessions = mock(CustomerServiceSessionMapper.class);
    final CustomerServiceMessageMapper messages = mock(CustomerServiceMessageMapper.class);
    final CustomerServiceApplicationServiceImpl service = new CustomerServiceApplicationServiceImpl(sessions, messages, mock(UserService.class));
    CustomerServiceSession session(int status) {
        var s = new CustomerServiceSession(); s.setId(10L); s.setUserId(1L); s.setStatus(status); return s;
    }
    CustomerServiceMessage message(long id) {
        var m = new CustomerServiceMessage(); m.setId(id); m.setSessionId(10L); m.setContent("fixture"); return m;
    }
    void error(String expected, Runnable action) { assertEquals(expected, assertThrows(BusinessException.class, action::run).getMessage()); }

    @Test void userHistoryChecksOwnerAndAllowsClosedHistory() {
        when(sessions.selectById(10L)).thenReturn(session(2));
        error("无权限", () -> service.listUserMessages(2L, 10L, null, null));
        error("无权限", () -> service.listUserMessages(null, 10L, null, null));
        error("无权限", () -> service.listUserMessages(1L, 99L, null, null));
        verifyNoInteractions(messages);
        when(messages.findHistory(10L, null, 50)).thenReturn(List.of());
        assertTrue(service.listUserMessages(1L, 10L, null, null).isEmpty());
    }
    @Test void historyNormalizesReuseLimitsAndReturnsAscendingCursorPage() {
        when(messages.findHistory(10L, 7L, 50)).thenReturn(List.of(message(6), message(4)));
        assertEquals(List.of(4L, 6L), service.listAdminMessages(10L, 7L, 0).stream().map(m -> m.getId()).toList());
        service.listAdminMessages(10L, null, -1); verify(messages).findHistory(10L, null, 50);
        service.listAdminMessages(10L, null, 999); verify(messages).findHistory(10L, null, 200);
        assertTrue(service.listAdminMessages(null, null, null).isEmpty());
    }
    @Test void sendsRejectInvalidClosedAndForeignWithoutWrites() {
        error("参数错误", () -> service.storeUserMessage(1L, null, "text"));
        error("参数错误", () -> service.storeAdminMessage(1L, 10L, " \n "));
        error("会话不存在或已关闭", () -> service.storeUserMessage(1L, 99L, "text"));
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(2));
        error("会话不存在或已关闭", () -> service.storeUserMessage(1L, 10L, "text"));
        error("会话不存在或已关闭", () -> service.storeAdminMessage(1L, 10L, "text"));
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(1));
        error("无权限", () -> service.storeUserMessage(2L, 10L, "text"));
        error("未登录", () -> service.storeAdminMessage(null, 10L, "text"));
        verifyNoInteractions(messages); verify(sessions, never()).touchOpenSession(any(), any());
    }
    @Test void trimToEmptyControlContentCannotPersistThroughReuse() {
        for (String content : List.of("\u0000", "\u001f", " \u0000\u001f ")) {
            error("参数错误", () -> service.storeUserMessage(1L, 10L, content));
            error("参数错误", () -> service.storeAdminMessage(1L, 10L, content));
        }
        verifyNoInteractions(sessions, messages);
    }

    @Test void sendsUseTrustedIdentityTrimAndOneTimestamp() {
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(1));
        when(messages.insert(any(CustomerServiceMessage.class))).thenAnswer(call -> { call.getArgument(0, CustomerServiceMessage.class).setId(20L); return 1; });
        when(sessions.touchOpenSession(eq(10L), any())).thenReturn(1);
        var delivery = service.storeUserMessage(1L, 10L, "  text \n");
        assertEquals(1L, delivery.userId()); assertEquals(1L, delivery.message().getSenderId());
        assertEquals(1, delivery.message().getSenderType()); assertEquals("text", delivery.message().getContent());
        var now = ArgumentCaptor.forClass(Date.class); verify(sessions).touchOpenSession(eq(10L), now.capture());
        assertEquals(now.getValue(), delivery.message().getCreateTime());
        assertEquals(2, service.storeAdminMessage(7L, 10L, "reply").message().getSenderType());
    }
    @Test void failedAffectedRowsCannotReturnSuccessfulDelivery() {
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(1));
        error("消息发送失败，请重试", () -> service.storeUserMessage(1L, 10L, "text"));
        verify(sessions, never()).touchOpenSession(any(), any());
        when(messages.insert(any(CustomerServiceMessage.class))).thenReturn(1);
        error("会话不存在或已关闭", () -> service.storeAdminMessage(7L, 10L, "text"));
    }
    @Test void openReusesLatestAndCloseIsIdempotentAndTrimsReason() {
        error("未登录", () -> service.openSession(null));
        when(sessions.findLatestOpenByUser(1L)).thenReturn(session(1));
        assertEquals(10L, service.openSession(1L).getId()); verify(sessions, never()).insert(any(CustomerServiceSession.class));
        service.closeSession(null, null); service.closeSession(99L, null);
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(2)); service.closeSession(10L, "x");
        verify(sessions, never()).closeOpenSession(any(), any(), any());
        when(sessions.findByIdForUpdate(10L)).thenReturn(session(1));
        when(sessions.closeOpenSession(eq(10L), any(), eq("done"))).thenReturn(1);
        service.closeSession(10L, " done "); verify(sessions).closeOpenSession(eq(10L), any(), eq("done"));
    }
}
