package com.codeying.service.impl;

import com.codeying.entity.Dish;
import com.codeying.entity.DishFlavor;
import com.codeying.service.AddressBookService;
import com.codeying.service.AiModelService;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.CategoryService;
import com.codeying.service.DishFlavorService;
import com.codeying.service.DishService;
import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * AiAssistantIntentRoutingTest?
 *
 * @author Endercloud
 */

class AiAssistantIntentRoutingTest {

    @Test
    void shouldRouteAddressBookByRule() throws Exception {
        AiAssistantApplicationServiceImpl service = buildService((sys, user) -> "");
        String intent = invokeResolveIntentByRule(service, "\u6211\u5E0C\u671B\u4FEE\u6539\u5730\u5740\u7C3F\u4FE1\u606F");
        Assertions.assertEquals("address_book_manage", intent);
    }

    @Test
    void shouldRouteCartByRule() throws Exception {
        AiAssistantApplicationServiceImpl service = buildService((sys, user) -> "");
        String intent = invokeResolveIntentByRule(service, "\u8BF7\u5E2E\u6211\u6E05\u7A7A\u8D2D\u7269\u8F66");
        Assertions.assertEquals("cart_manage", intent);
    }

    @Test
    void shouldRouteHotDishQuestionToRecommend() throws Exception {
        AiAssistantApplicationServiceImpl service = buildService((sys, user) -> "");
        String intent = invokeResolveIntentByRule(service, "\u6700\u8FD1\u6709\u4EC0\u4E48\u70ED\u95E8\u83DC\u5417");
        Assertions.assertEquals("recommend_dish", intent);
    }

    @Test
    void shouldPreferHeatAnalysisCandidatesForHotQuestion() throws Exception {
        AnalysisApplicationService analysisService = Mockito.mock(AnalysisApplicationService.class);
        AnalysisObservationService analysisObservationService = Mockito.mock(AnalysisObservationService.class);
        DishService dishService = Mockito.mock(DishService.class);
        DishFlavorService dishFlavorService = Mockito.mock(DishFlavorService.class);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        AddressBookService addressBookService = Mockito.mock(AddressBookService.class);
        AiModelService aiModelService = (sys, user) -> "invalid json";

        DishHeatPredictionVO top1 = new DishHeatPredictionVO();
        top1.setDishId(28L);
        DishHeatPredictionVO top2 = new DishHeatPredictionVO();
        top2.setDishId(23L);
        when(analysisService.listHeatPredictions(any(LocalDate.class), eq(10))).thenReturn(List.of(top1, top2));
        when(analysisService.listRecommendedDishIdsForUser(eq(1L), eq(10))).thenReturn(List.of(9L, 25L));
        when(dishService.listByIds(eq(List.of(28L, 23L)))).thenReturn(List.of(
                buildDish(28L, "蜜汁叉烧"),
                buildDish(23L, "凉拌三丝")
        ));
        when(dishFlavorService.list(Mockito.<Wrapper<DishFlavor>>any())).thenReturn(List.of());
        when(categoryService.listByIds(any())).thenReturn(List.of());
        when(addressBookService.getOne(any())).thenReturn(null);

        AiAssistantApplicationServiceImpl service = new AiAssistantApplicationServiceImpl(
                null,
                null,
                categoryService,
                dishService,
                dishFlavorService,
                addressBookService,
                null,
                null,
                null,
                null,
                analysisService,
                analysisObservationService,
                aiModelService,
                new ObjectMapper()
        );

        AiAssistantSendReplyVO reply = invokeBuildRecommendReply(service, 1L, "给我推荐3道最近热门的菜。");

        Assertions.assertFalse(CollectionUtils.isEmpty(reply.getDishes()));
        Assertions.assertEquals(List.of(28L, 23L),
                reply.getDishes().stream().map(d -> Long.valueOf(d.getDishId())).toList());
        verify(analysisService, never()).listRecommendedDishIdsForUser(eq(1L), eq(10));
    }

    @Test
    void shouldFallbackToOutOfScopeWhenModelConfidenceLow() throws Exception {
        AiAssistantApplicationServiceImpl service = buildService((sys, user) -> "{\"intent\":\"manual_service\",\"confidence\":0.31}");
        String intent = invokeResolveIntentByRuleThenModel(service, "\u6211\u60F3\u770B\u770B\u4ECA\u5929\u65B0\u95FB");
        Assertions.assertEquals("out_of_scope", intent);
    }

    @Test
    void shouldUseModelIntentWhenConfidenceHigh() throws Exception {
        AiAssistantApplicationServiceImpl service = buildService((sys, user) -> "{\"intent\":\"manual_service\",\"confidence\":0.95}");
        String intent = invokeResolveIntentByRuleThenModel(service, "\u6211\u60F3\u8054\u7CFB\u4EBA\u5DE5\u5BA2\u670D");
        Assertions.assertEquals("manual_service", intent);
    }
/**
 * buildService?
 * @param aiModelService aiModelService
 * @return ????
 */

    private AiAssistantApplicationServiceImpl buildService(AiModelService aiModelService) {
        return new AiAssistantApplicationServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                aiModelService,
                new ObjectMapper()
        );
    }

    private AiAssistantSendReplyVO invokeBuildRecommendReply(
            AiAssistantApplicationServiceImpl service,
            Long userId,
            String text
    ) throws Exception {
        Method method = AiAssistantApplicationServiceImpl.class.getDeclaredMethod(
                "buildRecommendReply",
                Long.class,
                String.class,
                java.util.Set.class,
                boolean.class
        );
        method.setAccessible(true);
        return (AiAssistantSendReplyVO) method.invoke(service, userId, text, java.util.Collections.emptySet(), false);
    }
/**
 * buildDish?
 * @param id id
 * @param name name
 * @return ????
 */

    private Dish buildDish(Long id, String name) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setName(name);
        dish.setStatus(1);
        dish.setCategoryId(1L);
        dish.setPrice(BigDecimal.TEN);
        return dish;
    }
/**
 * invokeResolveIntentByRule?
 * @param service service
 * @param text text
 * @return ????
 * @throws Exception ??
 */

    private String invokeResolveIntentByRule(AiAssistantApplicationServiceImpl service, String text) throws Exception {
        Method method = AiAssistantApplicationServiceImpl.class.getDeclaredMethod("resolveIntentByRule", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, text);
    }
/**
 * invokeResolveIntentByRuleThenModel?
 * @param service service
 * @param text text
 * @return ????
 * @throws Exception ??
 */

    private String invokeResolveIntentByRuleThenModel(AiAssistantApplicationServiceImpl service, String text) throws Exception {
        Method method = AiAssistantApplicationServiceImpl.class.getDeclaredMethod("resolveIntentByRuleThenModel", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, text);
    }
}
