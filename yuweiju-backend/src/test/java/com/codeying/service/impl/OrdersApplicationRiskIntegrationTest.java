package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.common.page.PageData;
import com.codeying.dto.admin.order.OrderConditionQuery;
import com.codeying.entity.OrderRiskResult;
import com.codeying.entity.Orders;
import com.codeying.properties.BaiduMapProperties;
import com.codeying.properties.ShopProperties;
import com.codeying.service.AddressBookService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.DishService;
import com.codeying.service.OrderDetailService;
import com.codeying.service.OrderRiskService;
import com.codeying.service.OrdersService;
import com.codeying.service.SetmealService;
import com.codeying.service.ShoppingCartService;
import com.codeying.service.UserService;
import com.codeying.vo.admin.order.OrderVO;
import com.codeying.utils.BaiduMapUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;

class OrdersApplicationRiskIntegrationTest {

    private final OrdersService ordersService = Mockito.mock(OrdersService.class);
    private final OrderDetailService orderDetailService = Mockito.mock(OrderDetailService.class);
    private final DishService dishService = Mockito.mock(DishService.class);
    private final SetmealService setmealService = Mockito.mock(SetmealService.class);
    private final ShoppingCartService shoppingCartService = Mockito.mock(ShoppingCartService.class);
    private final AddressBookService addressBookService = Mockito.mock(AddressBookService.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private final ShopProperties shopProperties = Mockito.mock(ShopProperties.class);
    private final BaiduMapProperties baiduMapProperties = Mockito.mock(BaiduMapProperties.class);
    private final BaiduMapUtil baiduMapUtil = Mockito.mock(BaiduMapUtil.class);
    private final StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final AnalysisObservationService analysisObservationService = Mockito.mock(AnalysisObservationService.class);
    private final OrderRiskService orderRiskService = Mockito.mock(OrderRiskService.class);

    private OrdersApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrdersApplicationServiceImpl(
                ordersService,
                orderDetailService,
                dishService,
                setmealService,
                shoppingCartService,
                addressBookService,
                userService,
                shopProperties,
                baiduMapProperties,
                baiduMapUtil,
                stringRedisTemplate,
                analysisObservationService,
                orderRiskService
        );
    }

    @Test
    void shouldReturnRiskFieldsAndKeepOriginalOrderFieldsWhenConditionSearch() {
        Orders row = new Orders();
        row.setId(5001L);
        row.setNumber("ORD-5001");
        row.setStatus(Orders.COMPLETED);
        row.setAmount(BigDecimal.valueOf(66.6));
        row.setOrderTime(new Date());

        Page<Orders> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        Mockito.when(ordersService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        Mockito.when(orderDetailService.list(any(QueryWrapper.class))).thenReturn(List.of());

        OrderRiskResult riskResult = new OrderRiskResult();
        riskResult.setOrderId(5001L);
        riskResult.setRiskLevel("HIGH");
        riskResult.setRiskScore(88);
        riskResult.setModelVersion("order-risk-20260426203624");
        Mockito.when(orderRiskService.findLatestByOrderIds(any())).thenReturn(Map.of(5001L, riskResult));
        Mockito.when(orderRiskService.summarizeReason(any(OrderRiskResult.class))).thenReturn("suspicious_remark");

        OrderConditionQuery query = new OrderConditionQuery();
        query.setPage(1);
        query.setPageSize(10);
        query.setRiskLevel("HIGH");
        query.setMinRiskScore(50);

        PageData<OrderVO> result = service.adminConditionSearch(query);

        Assertions.assertEquals(1L, result.getTotal());
        Assertions.assertEquals(1, result.getRecords().size());
        OrderVO vo = result.getRecords().get(0);
        Assertions.assertEquals("ORD-5001", vo.getNumber());
        Assertions.assertEquals(88, vo.getRiskScore());
        Assertions.assertEquals("HIGH", vo.getRiskLevel());
        Assertions.assertEquals("order-risk-20260426203624", vo.getModelVersion());
        Assertions.assertEquals("suspicious_remark", vo.getRiskReasons());

        ArgumentCaptor<QueryWrapper<Orders>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        Mockito.verify(ordersService).page(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getCustomSqlSegment();
        Assertions.assertTrue(segment.contains("order_risk_result"));
        Assertions.assertTrue(segment.contains("risk_level"));
        Assertions.assertTrue(segment.contains("risk_score"));
    }
}
