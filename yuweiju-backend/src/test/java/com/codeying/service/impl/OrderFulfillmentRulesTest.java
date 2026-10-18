package com.codeying.service.impl;

import com.codeying.dto.user.order.OrdersPaymentDTO;
import com.codeying.entity.Orders;
import com.codeying.entity.User;
import com.codeying.exception.OrderBusinessException;
import com.codeying.mapper.OrdersMapper;
import com.codeying.properties.BaiduMapProperties;
import com.codeying.properties.ShopProperties;
import com.codeying.service.*;
import com.codeying.utils.BaiduMapUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ordinary rule tests; real SQL/transaction proof is the isolated Issue11Probe. */
class OrderFulfillmentRulesTest {
    OrdersMapper mapper = mock(OrdersMapper.class);
    OrdersService stored = mock(OrdersService.class);
    OrderRiskService risk = mock(OrderRiskService.class);
    UserService users = mock(UserService.class);
    ShoppingCartService carts = mock(ShoppingCartService.class);
    OrderDetailService details = mock(OrderDetailService.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ShopStatusService shopStatus = mock(ShopStatusService.class);
    OrdersApplicationService service;

    @BeforeEach void setup() {
        when(shopStatus.getStatus()).thenReturn(1);
        service = new OrdersApplicationServiceImpl(stored, details, mock(DishService.class),
                mock(SetmealService.class), carts, mock(AddressBookService.class), users,
                new ShopProperties(), new BaiduMapProperties(), mock(BaiduMapUtil.class),
                redis, mock(AnalysisObservationService.class), risk, mapper, shopStatus);
    }
    Orders order(int status) {
        Orders o = new Orders(); o.setId(11L); o.setUserId(21L); o.setStatus(status);
        o.setPayStatus(status == 1 ? 0 : 1);
        when(stored.getById(11L)).thenReturn(o);
        when(mapper.findOwned(21L,11L)).thenReturn(o);
        when(mapper.findByNumberOwned(21L,"N11")).thenReturn(o);
        return o;
    }
    @Test void closedShopAllowsOldMockPaymentCancellationAndFulfilment() {
        when(shopStatus.getStatus()).thenReturn(0);
        order(1); User user = new User(); user.setOpenid("fixture"); when(users.getById(21L)).thenReturn(user);
        var payment = new OrdersPaymentDTO(); payment.setOrderNumber("N11");
        when(mapper.payPending(eq(11L),eq(21L),any())).thenReturn(1);
        assertEquals("mock", service.paymentMock(21L,payment).getPaySign());
        for (int status : new int[]{1,2}) {
            order(status); when(mapper.transitionFrom(any(),eq(status),eq(21L))).thenReturn(1);
            service.cancelByUser(21L,11L);
        }
        List<Consumer<Long>> entries = List.of(service::confirm,service::deliver,service::complete);
        for (int i=0; i<entries.size(); i++) {
            int status = i+2; order(status);
            when(mapper.transitionFrom(any(),eq(status),isNull())).thenReturn(1);
            entries.get(i).accept(11L);
        }
        verifyNoInteractions(shopStatus);
    }
    @Test void reusableEntriesRejectUnusableIdentifiers() {
        List<Consumer<Long>> entries = List.of(service::markPaid, service::confirm, service::deliver,
                service::complete, id -> service.reject(id,"reason"), id -> service.cancelByAdmin(id,"reason"),
                id -> service.cancelByUser(21L,id), id -> service.repetition(21L,id), id -> service.reminder(21L,id));
        for (var entry : entries) for (Long id : new Long[]{null,0L,-1L})
            assertThrows(OrderBusinessException.class, () -> entry.accept(id));
        verifyNoInteractions(mapper, stored, carts, risk, redis);
    }
    @Test void reasonValidationLivesInReusableService() {
        for (String reason : new String[]{null,""," \t "}) {
            assertThrows(OrderBusinessException.class, () -> service.reject(11L,reason));
            assertThrows(OrderBusinessException.class, () -> service.cancelByAdmin(11L,reason));
        }
        verifyNoInteractions(stored,mapper);
    }
    @Test void paymentMissingOrCancelledCannotBeBypassed() {
        assertThrows(OrderBusinessException.class, () -> service.markPaid(11L));
        order(6); assertThrows(OrderBusinessException.class, () -> service.markPaid(11L));
        verify(mapper,never()).payPending(any(),any(),any()); verifyNoInteractions(risk);
    }
    @Test void paidRetryAtEveryProgressedStateDoesNotWriteOrScore() {
        for (int state : new int[]{2,3,4,5}) {order(state); service.markPaid(11L);}
        verify(mapper,never()).payPending(any(),any(),any()); verifyNoInteractions(risk);
    }
    @Test void inconsistentUnpaidProgressedStateFails() {
        order(3).setPayStatus(0);
        assertThrows(OrderBusinessException.class, () -> service.markPaid(11L));
        verifyNoInteractions(risk);
    }
    @Test void paymentConflictAndCoreFailureDoNotScore() {
        order(1); when(mapper.payPending(eq(11L),isNull(),any())).thenReturn(0);
        assertThrows(OrderBusinessException.class, () -> service.markPaid(11L));
        RuntimeException failure = new IllegalStateException("SQL fixture");
        when(mapper.payPending(eq(11L),isNull(),any())).thenThrow(failure);
        assertSame(failure,assertThrows(RuntimeException.class, () -> service.markPaid(11L)));
        verifyNoInteractions(risk);
    }
    @Test void paymentOwnerBoundAndOptionalRiskFailureDegrades() {
        order(1); User u = new User(); u.setOpenid("fixture"); when(users.getById(21L)).thenReturn(u);
        OrdersPaymentDTO dto = new OrdersPaymentDTO(); dto.setOrderNumber(" N11 ");
        when(mapper.payPending(eq(11L),eq(21L),any())).thenReturn(1);
        doThrow(new IllegalStateException("optional")).when(risk).scoreOrder(11L,"paid");
        assertEquals("mock",service.paymentMock(21L,dto).getPaySign());
        verify(mapper).payPending(eq(11L),eq(21L),any());
    }
    @Test void administratorTransitionsFailOnStaleWrite() {
        List<Consumer<Long>> entries=List.of(service::confirm,id->service.reject(id,"reason"),
                id->service.cancelByAdmin(id,"reason"),service::deliver,service::complete);
        int[] states={2,2,3,3,4};
        for (int i=0;i<entries.size();i++) {
            order(states[i]); var entry=entries.get(i);
            when(mapper.transitionFrom(any(),eq(states[i]),isNull())).thenReturn(0);
            assertThrows(OrderBusinessException.class,()->entry.accept(11L));
        }
    }
    @Test void userCancellationBindsOwnerAndExactObservedState() {
        order(2); when(mapper.transitionFrom(any(),eq(2),eq(21L))).thenReturn(1);
        service.cancelByUser(21L,11L);
        verify(mapper).transitionFrom(argThat(o -> o.getStatus()==6 && o.getCancelTime()!=null
                && "用户取消订单".equals(o.getCancelReason()) && o.getPayStatus()==null),eq(2),eq(21L));
    }
    @Test void reminderOwnershipAndStateCheckedBeforeRedis() {
        assertThrows(OrderBusinessException.class,()->service.reminder(21L,11L));
        for(int s : new int[]{1,3,4,5,6}) {order(s);assertThrows(OrderBusinessException.class,()->service.reminder(21L,11L));}
        verifyNoInteractions(redis);
    }
    @Test void repetitionMissingOwnerDoesNotClearCart() {
        assertThrows(OrderBusinessException.class,()->service.repetition(21L,11L));
        verifyNoInteractions(carts,details);
    }
}
