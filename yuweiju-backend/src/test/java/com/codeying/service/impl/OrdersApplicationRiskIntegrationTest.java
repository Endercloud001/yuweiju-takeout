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
import com.codeying.mapper.OrdersMapper;
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

    private final OrdersMapper ordersMapper = Mockito.mock(OrdersMapper.class);
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
                orderRiskService, ordersMapper
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
        Mockito.when(ordersMapper.selectAdminConditionPage(any(), any(), any(), any(), any())).thenReturn(page);
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

        Mockito.verify(ordersMapper).selectAdminConditionPage(any(), Mockito.eq(query),
                Mockito.isNull(), Mockito.isNull(), Mockito.eq("HIGH"));
    }

    private Orders row() {
        Orders row = new Orders();
        row.setId(5001L);
        row.setNumber("ORD-5001");
        row.setAmount(new BigDecimal("66.60"));
        Mockito.when(ordersService.getById(5001L)).thenReturn(row);
        Mockito.when(orderDetailService.list(any(QueryWrapper.class))).thenReturn(List.of());
        return row;
    }

    private void unavailable(OrderVO vo) {
        Assertions.assertEquals("UNAVAILABLE", vo.getRiskLevel());
        Assertions.assertNull(vo.getRiskScore());
        Assertions.assertNull(vo.getModelVersion());
        Assertions.assertNotNull(vo.getRiskReasons());
        Assertions.assertEquals(new BigDecimal("66.60"), vo.getAmount());
    }

    @Test
    void missingRiskAndLookupFailureKeepDetailReadable() {
        row();
        Mockito.when(orderRiskService.findLatestByOrderIds(any())).thenReturn(Map.of());
        unavailable(service.adminOrderDetail(5001L));
        Mockito.when(orderRiskService.findLatestByOrderIds(any())).thenThrow(new IllegalStateException("optional"));
        unavailable(service.adminOrderDetail(5001L));
    }

    @Test
    void assemblyFailureClearsOptionalFields() {
        row();
        OrderRiskResult risk = new OrderRiskResult();
        risk.setRiskScore(80);
        risk.setRiskLevel("HIGH");
        Mockito.when(orderRiskService.findLatestByOrderIds(any())).thenReturn(Map.of(5001L, risk));
        Mockito.when(orderRiskService.summarizeReason(any())).thenThrow(new IllegalStateException("assembly"));
        unavailable(service.adminOrderDetail(5001L));
    }

    @Test
    void optionalPageFailureAndEmptyPage() {
        Orders row = row();
        Page<Orders> page = new Page<>(1, 10);
        page.setRecords(List.of(row)); page.setTotal(1);
        Mockito.when(ordersMapper.selectAdminConditionPage(any(), any(), any(), any(), any())).thenReturn(page);
        Mockito.when(orderRiskService.findLatestByOrderIds(any())).thenThrow(new IllegalStateException("optional"));
        OrderConditionQuery query = new OrderConditionQuery(); query.setPage(1); query.setPageSize(10);
        unavailable(service.adminConditionSearch(query).getRecords().get(0));
        Mockito.clearInvocations(orderRiskService);
        page.setRecords(List.of());
        Assertions.assertTrue(service.adminConditionSearch(query).getRecords().isEmpty());
        Mockito.verifyNoInteractions(orderRiskService);
    }

    @Test
    void coreAndFilterFailuresPropagateWithoutRetry() {
        RuntimeException failure = new IllegalStateException("core");
        Mockito.when(ordersService.getById(5001L)).thenThrow(failure);
        Assertions.assertSame(failure, Assertions.assertThrows(RuntimeException.class, () -> service.adminOrderDetail(5001L)));
        OrderConditionQuery query = new OrderConditionQuery(); query.setPage(1); query.setPageSize(10); query.setRiskLevel("HIGH");
        Mockito.when(ordersMapper.selectAdminConditionPage(any(), any(), any(), any(), any())).thenThrow(failure);
        Assertions.assertSame(failure, Assertions.assertThrows(RuntimeException.class, () -> service.adminConditionSearch(query)));
        Mockito.verify(ordersMapper, Mockito.times(1)).selectAdminConditionPage(any(), any(), any(), any(), any());
        Mockito.verifyNoInteractions(orderRiskService);
    }
}
