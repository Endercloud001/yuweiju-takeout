package com.codeying.service.impl;

import com.codeying.entity.*;
import com.codeying.exception.OrderBusinessException;
import com.codeying.mapper.ShoppingCartMapper;
import com.codeying.service.*;
import com.codeying.vo.user.ShoppingCartItemVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartRecoveryTest {
    ShoppingCart row(long id, boolean meal) {
        var row = new ShoppingCart(); row.setId(id); row.setUserId(7L);
        if (meal) row.setSetmealId(id); else row.setDishId(id);
        row.setName("historical"); row.setImage("old.png"); row.setDishFlavor("mild");
        row.setNumber(3); row.setAmount(new BigDecimal("0.01")); return row;
    }
    @Test void readsRetainEveryInvalidDishAndSetmealWithoutWritingOrTrustingSnapshots() {
        var dishes=mock(DishService.class); var meals=mock(SetmealService.class); var mapper=mock(ShoppingCartMapper.class);
        var service=new ShoppingCartServiceImpl(dishes,meals,mock(AnalysisObservationService.class));
        ReflectionTestUtils.setField(service,"baseMapper",mapper);
        var stoppedDish=new Dish(); stoppedDish.setStatus(0); stoppedDish.setPrice(BigDecimal.TEN);
        var stoppedMeal=new Setmeal(); stoppedMeal.setStatus(0); stoppedMeal.setPrice(BigDecimal.TEN);
        var badPrice=new Dish(); badPrice.setStatus(1); badPrice.setPrice(new BigDecimal("-1"));
        var good=new Dish(); good.setStatus(1); good.setPrice(new BigDecimal("18.375"));
        when(dishes.getById(1L)).thenReturn(good); when(dishes.getById(2L)).thenReturn(stoppedDish);
        when(meals.getById(4L)).thenReturn(stoppedMeal); when(dishes.getById(6L)).thenReturn(badPrice);
        var stored=List.of(row(1,false),row(2,false),row(3,false),row(4,true),row(5,true),row(6,false));
        when(mapper.findByUser(7L)).thenReturn(stored);
        var result=service.listForUser(7L); assertEquals(6,result.size());
        for(int i=0;i<result.size();i++) {
            var read=(ShoppingCartItemVO)result.get(i); var old=stored.get(i);
            assertEquals(old.getId(),read.getId()); assertEquals(old.getNumber(),read.getNumber());
            assertEquals(old.getDishId(),read.getDishId()); assertEquals(old.getSetmealId(),read.getSetmealId());
            assertEquals("old.png",read.getImage()); assertEquals("historical",read.getName()); assertEquals("mild",read.getDishFlavor());
            assertEquals(i==0,read.isAvailable());
            if(i==0) assertEquals(new BigDecimal("18.38"),read.getAmount());
            else { assertNull(read.getAmount()); assertNotNull(read.getUnavailableReason()); }
            assertEquals(new BigDecimal("0.01"),old.getAmount());
        }
        verify(mapper).findByUser(7L); verifyNoMoreInteractions(mapper);
        assertThrows(OrderBusinessException.class,()->new OrderChargingPolicy(dishes,meals).price(7L,result));
    }
    @Test void ownershipErrorsAreNotToleratedAndSubmitRechecksAfterRead() {
        var dishes=mock(DishService.class); var policy=new OrderChargingPolicy(dishes,mock(SetmealService.class));
        var good=new Dish();good.setStatus(1);good.setPrice(BigDecimal.TEN);when(dishes.getById(1L)).thenReturn(good);
        var row=row(1,false); var read=policy.inspect(7L,List.of(row)); assertEquals(BigDecimal.TEN.setScale(2),read.get(0).getAmount());
        good.setStatus(0); assertThrows(OrderBusinessException.class,()->policy.price(7L,read));
        row.setUserId(8L); assertThrows(OrderBusinessException.class,()->policy.inspect(7L,List.of(row)));
    }
}
