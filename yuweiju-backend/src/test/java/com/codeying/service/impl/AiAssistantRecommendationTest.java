package com.codeying.service.impl;

import com.codeying.entity.*;
import com.codeying.exception.BusinessException;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAssistantRecommendationTest {
    static void retry(AiAssistantSendReplyVO reply) {
        assertTrue(reply.getReply().contains("重试")); assertTrue(reply.getDishes().isEmpty());
        assertNull(reply.getAction()); assertNull(reply.getPayload());
    }
    @Test void legitimateFencedJsonUsesServerCardsAndDeduplicatesInOrder() {
        var f = new AiAssistantTestFixture();
        when(f.model.chat(anyString(), anyString())).thenReturn("```json\n" + AiAssistantTestFixture.valid("23,28,23").replace("\"dishId\":23", "\"dishId\":23,\"name\":\"untrusted\",\"price\":0") + "\n```");
        var reply = f.send("推荐菜品");
        assertEquals(List.of(23L,28L), reply.getDishes().stream().map(d -> d.getDishId()).toList());
        assertEquals("凉拌三丝", reply.getDishes().get(0).getName());
        assertEquals(java.math.BigDecimal.TEN, reply.getDishes().get(0).getPrice());
        assertEquals("/server/image.png", reply.getDishes().get(0).getImageUrl());
        assertEquals("suggest_dish", reply.getAction());
    }
    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "[]", "{\"reply\":1}",
            "{\"reply\":\"ok\",\"dishes\":[],\"action\":\"suggest_dish\",\"payload\":null}",
            "{\"reply\":\"ok\",\"dishes\":[{\"dishId\":28}],\"action\":\"cart_manage\",\"payload\":null}",
            "{\"reply\":\"ok\",\"dishes\":[{\"dishId\":28}],\"action\":\"suggest_dish\",\"payload\":{}}",
            "{\"reply\":\"\",\"dishes\":[{\"dishId\":28}],\"action\":\"suggest_dish\",\"payload\":null}"})
    void malformedFieldsNeverFillCandidates(String content) throws Exception {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(), anyString())).thenReturn(content);
        retry(f.send("推荐菜品"));
        var captured = ArgumentCaptor.forClass(AiAssistantMessage.class); verify(f.messages,times(2)).insert(captured.capture());
        var stored = captured.getAllValues().get(1); var meta = new ObjectMapper().readTree(stored.getMetadata());
        assertEquals(0, meta.path("dishCount").intValue()); assertTrue(meta.path("dishIds").isEmpty()); assertTrue(meta.path("action").isNull());
        assertFalse(stored.getContent().contains("蜜汁叉烧"));
        verify(f.sessions).updateReplyState(eq(71L),eq(1L),anyString(),isNull(),any());
    }
    @ParameterizedTest @ValueSource(strings = {"999", "28,999", "28.0", "28.7", "\"28\"", "true", "null", "-28", "0", "9223372036854775808", ""})
    void invalidChoicesRejectWholeRecommendation(String ids) {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(), anyString())).thenReturn(AiAssistantTestFixture.valid(ids));
        retry(f.send("推荐菜品"));
    }
    @Test void oversizedPersistedReplyIsRetryRatherThanSqlFailure() {
        var f = new AiAssistantTestFixture();
        when(f.model.chat(anyString(), anyString())).thenReturn(AiAssistantTestFixture.valid("28").replace("为您推荐菜品。", "字".repeat(2001)));
        retry(f.send("推荐菜品"));
        retry(f.send("一键推荐"));
    }
    @Test void legitimateUnicodeReplyFitsActualCharacterColumn() {
        var f = new AiAssistantTestFixture();
        when(f.model.chat(anyString(), anyString())).thenReturn(AiAssistantTestFixture.valid("28").replace("为您推荐菜品。", "🍜".repeat(1001)));
        assertFalse(f.send("推荐菜品").getDishes().isEmpty());
    }
    @Test void validatesAllSelectionsBeforeOneDishLimit() {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(), anyString())).thenReturn(AiAssistantTestFixture.valid("28,999"));
        retry(f.send("推荐一道菜"));
    }
    @Test void stoppedDuringModelCallRejectsWholeReply() {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(), anyString())).thenAnswer(call -> {
            f.catalog.get(1).setStatus(0); return AiAssistantTestFixture.valid("28,23");
        });
        retry(f.send("推荐菜品"));
    }
    @Test void noOnSaleCandidatesTellsTruthWithoutCallingModel() {
        var f = new AiAssistantTestFixture(); f.catalog.clear(); var reply=f.send("推荐菜品");
        assertTrue(reply.getReply().contains("没有可推荐")); assertTrue(reply.getDishes().isEmpty()); assertNull(reply.getAction()); verifyNoInteractions(f.model);
    }
    @Test void noHistoryOneKeyKeepsOrdinaryRecommendationButTellsTruth() {
        var f = new AiAssistantTestFixture(); var reply=f.send("一键推荐");
        assertFalse(reply.getDishes().isEmpty()); assertTrue(reply.getReply().contains("暂无足够可用的历史偏好"));
    }
    @Test void weatherNullOrFailureDoesNotBlockValidRecommendation() {
        var f = new AiAssistantTestFixture(); var a = new AddressBook(); a.setCityName("fixture-city"); a.setDetail("fixture-detail");
        when(f.addresses.findDefaultByUser(1L)).thenReturn(a);
        assertFalse(f.send("推荐菜品").getDishes().isEmpty());
        when(f.weather.getWeatherSummary(anyString())).thenThrow(new IllegalStateException("private detail"));
        assertFalse(f.send("推荐菜品").getDishes().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"garbage", "{\"intent\":\"recommend_dish\",\"confidence\":\"1\"}", "{\"intent\":\"unknown\",\"confidence\":1}","{\"intent\":\"recommend_dish\",\"confidence\":2}"})
    void invalidIntentDoesNotInvokeRecommendation(String output) {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(),anyString())).thenReturn(output);
        retry(f.send("随便聊点别的")); verify(f.model,times(1)).chat(anyString(),anyString()); verifyNoInteractions(f.dishes);
    }
    @Test void intentTransportFailureIsFriendlyRetry() {
        var f = new AiAssistantTestFixture(); when(f.model.chat(anyString(),anyString())).thenThrow(new IllegalStateException("fixture"));
        retry(f.send("随便聊点别的")); verify(f.model,times(1)).chat(anyString(),anyString());
    }
    @Test void ownerAndManualBoundariesRemainAtReusableService() {
        var f = new AiAssistantTestFixture(); retry(f.service.sendMessage(2L,f.body("推荐菜品")));
        assertThrows(BusinessException.class,()->f.service.listUserMessages(2L,71L,null,50));
        assertThrows(BusinessException.class,()->f.service.listUserMessages(null,71L,null,50));
        assertThrows(BusinessException.class,()->f.service.listUserMessages(1L,999L,null,50));
        assertFalse(f.service.isUserSession(2L,71L)); verifyNoInteractions(f.messages,f.model);
        var guide=f.send("人工客服"); assertTrue(guide.getReply().contains("客服消息")); assertTrue(guide.getDishes().isEmpty()); assertNull(guide.getAction()); verifyNoInteractions(f.model);
    }
    @Test void closedOldOrSpoofedSenderCannotWriteOrInvokeModel() {
        var f = new AiAssistantTestFixture(); f.session.setStatus(2); retry(f.send("推荐菜品"));
        f.session.setStatus(1); f.session.setCreateTime(new Date(0)); retry(f.send("推荐菜品"));
        f.session.setCreateTime(new Date()); var body=f.body("推荐菜品"); body.setSenderType(2); retry(f.service.sendMessage(1L,body));
        verifyNoInteractions(f.messages,f.model);
    }
}
