package com.codeying.service.impl;

import com.codeying.dto.common.ai_assistant.AiAssistantMessageSendDTO;
import com.codeying.entity.*;
import com.codeying.mapper.*;
import com.codeying.service.*;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ordinary public-use-case fixtures; no private method or SQL string assertions. */
final class AiAssistantTestFixture {
    final AiAssistantSessionMapper sessions = mock(AiAssistantSessionMapper.class);
    final AiAssistantMessageMapper messages = mock(AiAssistantMessageMapper.class);
    final DishMapper dishes = mock(DishMapper.class);
    final DishFlavorMapper flavors = mock(DishFlavorMapper.class);
    final CategoryMapper categories = mock(CategoryMapper.class);
    final AddressBookMapper addresses = mock(AddressBookMapper.class);
    final OrdersMapper orders = mock(OrdersMapper.class);
    final OrderDetailMapper details = mock(OrderDetailMapper.class);
    final UserMapper users = mock(UserMapper.class);
    final AiWeatherService weather = mock(AiWeatherService.class);
    final AnalysisApplicationService analysis = mock(AnalysisApplicationService.class);
    final AnalysisObservationService observation = mock(AnalysisObservationService.class);
    final AiModelService model = mock(AiModelService.class);
    final AiAssistantSession session = new AiAssistantSession();
    List<Dish> catalog = new ArrayList<>(List.of(dish(28L, "蜜汁叉烧"), dish(23L, "凉拌三丝")));
    final AiAssistantApplicationServiceImpl service;

    AiAssistantTestFixture() {
        session.setId(71L); session.setUserId(1L); session.setStatus(AiAssistantSession.STATUS_OPEN); session.setCreateTime(new Date());
        when(sessions.selectById(71L)).thenReturn(session);
        when(sessions.updateReplyState(anyLong(), anyLong(), anyString(), nullable(String.class), any())).thenReturn(1);
        when(dishes.listOnSaleCandidates(200)).thenAnswer(call -> catalog.stream().filter(d -> d.getStatus() == 1).toList());
        when(dishes.listOnSaleByIds(anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return catalog.stream().filter(d -> d.getStatus() == 1 && ids.contains(d.getId())).toList();
        });
        when(dishes.listOnSaleForReply(anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return catalog.stream().filter(d -> d.getStatus() == 1 && ids.contains(d.getId())).toList();
        });
        when(model.chat(anyString(), anyString())).thenReturn(valid("28,23"));
        service = new AiAssistantApplicationServiceImpl(sessions, messages, categories, dishes, flavors, addresses,
                orders, details, users, weather, analysis, observation, model, new ObjectMapper());
    }

    AiAssistantSendReplyVO send(String content) { return service.sendMessage(1L, body(content)); }
    AiAssistantMessageSendDTO body(String content) {
        var body = new AiAssistantMessageSendDTO(); body.setSessionId(71L); body.setContent(content); return body;
    }
    static Dish dish(long id, String name) {
        Dish dish = new Dish(); dish.setId(id); dish.setName(name); dish.setStatus(1); dish.setCategoryId(1L);
        dish.setPrice(BigDecimal.TEN); dish.setImage("/server/image.png"); return dish;
    }
    static String valid(String ids) {
        String values = Arrays.stream(ids.split(",")).filter(s -> !s.isEmpty()).map(s -> "{\"dishId\":" + s + "}")
                .reduce((a,b) -> a + "," + b).orElse("");
        return "{\"reply\":\"为您推荐菜品。\",\"dishes\":[" + values + "],\"action\":\"suggest_dish\",\"payload\":null}";
    }
}
