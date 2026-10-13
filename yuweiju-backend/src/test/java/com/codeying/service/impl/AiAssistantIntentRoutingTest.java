package com.codeying.service.impl;

import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** All original routing cases exercised through the public send use case. */
class AiAssistantIntentRoutingTest {
    @Test void shouldRouteAddressBookByRule() {
        var f = new AiAssistantTestFixture(); var reply = f.send("我希望修改地址簿信息");
        assertTrue(reply.getReply().contains("地址管理")); assertTrue(reply.getDishes().isEmpty()); verifyNoInteractions(f.model);
    }
    @Test void shouldRouteCartByRule() {
        var f = new AiAssistantTestFixture(); var reply = f.send("请帮我清空购物车");
        assertTrue(reply.getReply().contains("购物车")); assertTrue(reply.getDishes().isEmpty()); verifyNoInteractions(f.model);
    }
    @Test void shouldRouteHotDishQuestionToRecommend() {
        var f = new AiAssistantTestFixture(); var reply = f.send("最近有什么热门菜吗");
        assertFalse(reply.getDishes().isEmpty()); verify(f.model, times(1)).chat(anyString(), eq("最近有什么热门菜吗"));
    }
    @Test void shouldPreferHeatAnalysisCandidatesForHotQuestion() {
        var f = new AiAssistantTestFixture();
        var top1 = new DishHeatPredictionVO(); top1.setDishId(28L);
        var top2 = new DishHeatPredictionVO(); top2.setDishId(23L);
        when(f.analysis.listHeatPredictions(any(LocalDate.class), eq(10))).thenReturn(List.of(top1, top2));
        var reply = f.send("给我推荐3道最近热门的菜。");
        assertEquals(List.of(28L,23L), reply.getDishes().stream().map(d -> d.getDishId()).toList());
        verify(f.analysis, never()).listRecommendedDishIdsForUser(1L,10);
    }
    @Test void shouldFallbackToOutOfScopeWhenModelConfidenceLow() {
        var f = new AiAssistantTestFixture();
        when(f.model.chat(anyString(), anyString())).thenReturn("{\"intent\":\"manual_service\",\"confidence\":0.31}");
        assertTrue(f.send("我想看看今天新闻").getReply().contains("主要支持"));
        verify(f.model, times(1)).chat(anyString(), anyString());
    }
    @Test void shouldUseModelIntentWhenConfidenceHigh() {
        var f = new AiAssistantTestFixture();
        when(f.model.chat(anyString(), anyString())).thenReturn("{\"intent\":\"manual_service\",\"confidence\":0.95}");
        assertTrue(f.send("找个工作人员聊聊").getReply().contains("客服消息"));
        verify(f.model, times(1)).chat(anyString(), anyString());
    }
}
