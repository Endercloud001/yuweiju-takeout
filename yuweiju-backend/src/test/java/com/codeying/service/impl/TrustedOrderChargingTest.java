package com.codeying.service.impl;

import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.entity.*;
import com.codeying.exception.OrderBusinessException;
import com.codeying.mapper.OrdersMapper;
import com.codeying.properties.*;
import com.codeying.service.*;
import com.codeying.utils.BaiduMapUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ordinary service seam tests; SQL rollback is verified separately by Issue10Probe. */
class TrustedOrderChargingTest {
    final OrdersService orders = mock(OrdersService.class);
    final OrderDetailService details = mock(OrderDetailService.class);
    final ShoppingCartService carts = mock(ShoppingCartService.class);
    final AddressBookService addresses = mock(AddressBookService.class);
    final DishService dishes = mock(DishService.class);
    final SetmealService meals = mock(SetmealService.class);
    final UserService users = mock(UserService.class);
    final OrdersApplicationServiceImpl service = new OrdersApplicationServiceImpl(orders, details,
            dishes, meals, carts, addresses, users,
            mock(ShopProperties.class), mock(BaiduMapProperties.class), mock(BaiduMapUtil.class),
            mock(StringRedisTemplate.class), mock(AnalysisObservationService.class), mock(OrderRiskService.class), mock(OrdersMapper.class));

    ShoppingCart item(String price, Integer count) {
        var c = new ShoppingCart(); c.setId(1L); c.setUserId(7L); c.setDishId(10L);
        c.setAmount(price == null ? null : new BigDecimal(price)); c.setNumber(count); return c;
    }
    OrdersSubmitDTO body() {
        var b = new OrdersSubmitDTO(); b.setAddressBookId(8L); b.setPackAmount(2); b.setPayMethod(1);
        b.setAmount(new BigDecimal("44")); return b;
    }
    void catalog(String price) {
        var dish = new Dish(); dish.setId(10L); dish.setStatus(1); dish.setPrice(price == null ? null : new BigDecimal(price));
        when(dishes.getById(10L)).thenReturn(dish);
        var meal = new Setmeal(); meal.setId(11L); meal.setStatus(1); meal.setPrice(dish.getPrice());
        when(meals.getById(11L)).thenReturn(meal);
    }
    void prepare(ShoppingCart... items) {
        catalog("18.00");
        when(carts.listForUser(7L)).thenReturn(List.of(items));
        var a = new AddressBook(); a.setId(8L); a.setUserId(7L); when(addresses.findOwned(7L,8L)).thenReturn(a);
        var u = new User(); u.setId(7L); when(users.getById(7L)).thenReturn(u);
        when(orders.save(any(Orders.class))).thenAnswer(call -> { call.<Orders>getArgument(0).setId(9L); return true; });
        when(details.saveBatch(anyCollection())).thenReturn(true);
        when(carts.clearForUser(7L)).thenReturn(items.length);
    }
    @Test void actualTwo18SubmitMustReturn40RatherThan38() {
        prepare(item("18.00",2));
        assertEquals(new BigDecimal("40.00"), service.submit(7L,body()).getOrderAmount());
        var saved = ArgumentCaptor.forClass(Orders.class); verify(orders).save(saved.capture());
        assertEquals(new BigDecimal("40.00"), saved.getValue().getAmount()); assertEquals(2,saved.getValue().getPackAmount());
    }

    @Test void oneAndMixedUseQuantityPackingAndIgnoreMissingOrTamperedClientClaims() {
        prepare(item("0.01",1));
        var b = body(); b.setPackAmount(null); b.setAmount(null);
        assertEquals(new BigDecimal("21.00"),service.submit(7L,b).getOrderAmount());
        var meal = item("99999999.99",1); meal.setDishId(null); meal.setSetmealId(11L);
        prepare(item(null,1),meal); b.setPackAmount(Integer.MIN_VALUE); b.setAmount(new BigDecimal("-44.44"));
        assertEquals(new BigDecimal("40.00"),service.submit(7L,b).getOrderAmount());
    }
    @Test void trustedPriceUsesExplicitPerUnitHalfUpAndDetailsStoreRoundedCurrentUnit() {
        prepare(item("1.00",2)); catalog("18.375");
        assertEquals(new BigDecimal("40.76"),service.submit(7L,body()).getOrderAmount());
        verify(details).saveBatch(argThat(rows -> rows.size() == 1 && rows.iterator().next().getAmount().equals(new BigDecimal("18.38"))));
    }
    @Test void centsDoNotLosePrecisionAndMaximumTotalFitsStorage() {
        prepare(item("0.00",3)); catalog("0.10");
        assertEquals(new BigDecimal("5.30"),service.submit(7L,body()).getOrderAmount());
        prepare(item("1.00",1)); catalog("99999996.99");
        assertEquals(new BigDecimal("99999999.99"),service.submit(7L,body()).getOrderAmount());
    }
    @Test void invalidCatalogPricesAndTotalOverflowNeverWriteOrClearCart() {
        prepare(item("18.00",1));
        for (String price : new String[]{null,"-0.01","99999999.991","100000000.00","99999999.99"}) {
            catalog(price);
            assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        }
        verifyNoInteractions(orders,details); verify(carts,never()).clearForUser(any());
    }
    @Test void invalidQuantityNullRowAndWrongCartOwnerNeverWrite() {
        for (Integer quantity : new Integer[]{null,0,-1,Integer.MAX_VALUE}) {
            prepare(item("18.00",quantity)); assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        }
        var wrong = item("18.00",1); wrong.setUserId(99L); prepare(wrong);
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        when(carts.listForUser(7L)).thenReturn(java.util.Arrays.asList((ShoppingCart)null));
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        verifyNoInteractions(orders,details); verify(carts,never()).clearForUser(any());
    }
    @Test void missingBothOrNonpositiveProductAndStoppedOrMissingCatalogRefused() {
        var cart = item("18.00",1); prepare(cart);
        cart.setDishId(null); assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        cart.setDishId(0L); assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        cart.setDishId(10L); cart.setSetmealId(11L); assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        cart.setSetmealId(null); when(dishes.getById(10L)).thenReturn(null);
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        var stopped = new Dish(); stopped.setStatus(0); when(dishes.getById(10L)).thenReturn(stopped);
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        verifyNoInteractions(orders,details); verify(carts,never()).clearForUser(any());
    }
    @Test void noUserOrAddressOrCartCannotCreateZeroChargeOrder() {
        prepare(item("18.00",1));
        assertThrows(OrderBusinessException.class,()->service.submit(null,body()));
        assertThrows(OrderBusinessException.class,()->service.submit(0L,body()));
        assertThrows(OrderBusinessException.class,()->service.submit(7L,null));
        var b=body(); b.setAddressBookId(null); assertThrows(OrderBusinessException.class,()->service.submit(7L,b));
        when(addresses.findOwned(7L,8L)).thenThrow(new OrderBusinessException("地址不存在"));
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        var owned = new AddressBook(); owned.setUserId(7L); owned.setId(8L);
        doReturn(owned).when(addresses).findOwned(7L,8L); when(users.getById(7L)).thenReturn(null);
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        when(carts.listForUser(7L)).thenReturn(List.of());
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        verifyNoInteractions(orders,details); verify(carts,never()).clearForUser(any());
    }
    @Test void totalQuantityIntegerOverflowRefusedBeforeMoneyConversion() {
        prepare(item("0",Integer.MAX_VALUE),item("0",1)); catalog("0.00");
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        verifyNoInteractions(orders,details); verify(carts,never()).clearForUser(any());
    }
    @Test void coreSaveFailureDoesNotClearCartOrContinueToDetails() {
        prepare(item("18.00",1)); when(orders.save(any(Orders.class))).thenReturn(false);
        assertThrows(OrderBusinessException.class,()->service.submit(7L,body()));
        verifyNoInteractions(details); verify(carts,never()).clearForUser(any());
    }
}
