package com.codeying.websocket;

import com.codeying.config.SpringContextHolder;
import com.codeying.exception.BusinessException;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTransportTest {
    Session connection(long uid, RemoteEndpoint.Basic remote) {
        var session = mock(Session.class); var props = new HashMap<String, Object>(); props.put("uid", uid);
        when(session.getUserProperties()).thenReturn(props); when(session.getBasicRemote()).thenReturn(remote); return session;
    }
    @Test void coreFailureKeepsTypingLockSuccessfulSendReleasesDespiteTransportFailure() throws Exception {
        var service = mock(CustomerServiceApplicationService.class); var remote = mock(RemoteEndpoint.Basic.class);
        var owner = connection(1, remote); var other = connection(2, mock(RemoteEndpoint.Basic.class));
        var server = new CustomerServiceWebSocketServer();
        try (MockedStatic<SpringContextHolder> context = mockStatic(SpringContextHolder.class)) {
            context.when(() -> SpringContextHolder.getBean(CustomerServiceApplicationService.class)).thenReturn(service);
            context.when(() -> SpringContextHolder.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            try {
                server.onMessage("{\"type\":\"TYPING_LOCK\",\"sessionId\":10}", owner, "admin");
                when(service.storeAdminMessage(1L, 10L, "text")).thenThrow(new BusinessException("消息发送失败，请重试"));
                server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\"text\"}", owner, "admin");
                server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\"text\"}", other, "admin");
                verify(service, never()).storeAdminMessage(eq(2L), any(), any());
                verify(other.getBasicRemote()).sendText(contains("当前会话已有其他客服在回复"));
                var message = new CustomerServiceMessageVO(); message.setId(20L); message.setSessionId(10L);
                doReturn(new CustomerServiceApplicationService.MessageDelivery(message, 9L)).when(service).storeAdminMessage(1L, 10L, "text");
                var adminsField = CustomerServiceWebSocketServer.class.getDeclaredField("ADMIN_SESSIONS");
                adminsField.setAccessible(true);
                @SuppressWarnings("unchecked") var admins = (java.util.Set<Session>) adminsField.get(null);
                when(owner.isOpen()).thenReturn(true); admins.add(owner);
                // A registered recipient now fails delivery; the real SQL probe verifies retained commit.
                doThrow(new IOException("synthetic sensitive detail")).when(remote).sendText(anyString());
                assertDoesNotThrow(() -> server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\"text\"}", owner, "admin"));
                when(service.storeAdminMessage(2L, 10L, "text")).thenReturn(new CustomerServiceApplicationService.MessageDelivery(message, 9L));
                server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\"text\"}", other, "admin");
                verify(service).storeAdminMessage(2L, 10L, "text");
                server.onMessage("{\"type\":\"TYPING_LOCK\",\"sessionId\":10}", owner, "admin");
                server.onClose(owner, "admin");
                server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\"text\"}", other, "admin");
                verify(service, times(2)).storeAdminMessage(2L, 10L, "text");
            } finally { server.onClose(owner, "admin"); server.onClose(other, "admin"); }
        }
    }

    @Test void userFrameUsesAuthenticatedPropertyAndInvalidFramesStayFriendly() throws Exception {
        var service = mock(CustomerServiceApplicationService.class); var remote = mock(RemoteEndpoint.Basic.class);
        var user = connection(5, remote); var server = new CustomerServiceWebSocketServer();
        try (MockedStatic<SpringContextHolder> context = mockStatic(SpringContextHolder.class)) {
            context.when(() -> SpringContextHolder.getBean(CustomerServiceApplicationService.class)).thenReturn(service);
            context.when(() -> SpringContextHolder.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            when(service.storeUserMessage(5L, 10L, "text")).thenThrow(new BusinessException("无权限"));
            server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\" text \"}", user, "user");
            verify(service).storeUserMessage(5L, 10L, "text"); verify(remote).sendText(contains("无权限"));
            server.onMessage("private malformed payload", user, "user"); verify(remote).sendText(contains("消息格式错误"));
            server.onMessage("{\"type\":\"CHAT_SEND\",\"sessionId\":10,\"content\":\" \"}", user, "user"); verify(remote).sendText(contains("参数错误"));
            server.onMessage("{\"type\":\"TYPING_LOCK\",\"sessionId\":10}", user, "user"); verify(remote, times(2)).sendText(contains("无权限"));
        }
    }
}
