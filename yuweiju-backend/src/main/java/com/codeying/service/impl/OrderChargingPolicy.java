package com.codeying.service.impl;

import com.codeying.entity.ShoppingCart;
import com.codeying.exception.OrderBusinessException;
import com.codeying.service.DishService;
import com.codeying.service.SetmealService;
import org.springframework.beans.BeanUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Shared new-order pricing used by cart reads and order submission; never writes cart snapshots. */
final class OrderChargingPolicy {
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    private static final BigDecimal DELIVERY = new BigDecimal("2.00");
    private final DishService dishes;
    private final SetmealService setmeals;

    OrderChargingPolicy(DishService dishes, SetmealService setmeals) {
        this.dishes = dishes;
        this.setmeals = setmeals;
    }

    record Charge(List<ShoppingCart> items, int quantity, BigDecimal total) {}

    Charge price(Long userId, List<ShoppingCart> carts) {
        if (userId == null || userId <= 0) throw new OrderBusinessException("用户信息异常");
        if (carts == null) throw new OrderBusinessException("购物车异常");
        List<ShoppingCart> items = new ArrayList<>(carts.size());
        BigDecimal goods = new BigDecimal("0.00");
        long quantity = 0;
        for (ShoppingCart cart : carts) {
            if (cart == null || !userId.equals(cart.getUserId())) throw new OrderBusinessException("购物车归属异常");
            if (cart.getNumber() == null || cart.getNumber() <= 0) throw new OrderBusinessException("商品数量无效");
            quantity += cart.getNumber().longValue();
            if (quantity > Integer.MAX_VALUE) throw new OrderBusinessException("商品数量超出范围");
            Long dishId = cart.getDishId(), setmealId = cart.getSetmealId();
            if ((dishId == null) == (setmealId == null) || (dishId != null && dishId <= 0) || (setmealId != null && setmealId <= 0)) {
                throw new OrderBusinessException("商品身份无效");
            }
            BigDecimal rawPrice;
            if (dishId != null) {
                var dish = dishes.getById(dishId);
                if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) throw new OrderBusinessException("商品不可售");
                rawPrice = dish.getPrice();
            } else {
                var setmeal = setmeals.getById(setmealId);
                if (setmeal == null || !Integer.valueOf(1).equals(setmeal.getStatus())) throw new OrderBusinessException("商品不可售");
                rawPrice = setmeal.getPrice();
            }
            BigDecimal unit = money(rawPrice);
            goods = money(goods.add(unit.multiply(BigDecimal.valueOf(cart.getNumber()))));
            ShoppingCart priced = new ShoppingCart();
            BeanUtils.copyProperties(cart, priced);
            priced.setAmount(unit);
            items.add(priced);
        }
        BigDecimal total = items.isEmpty() ? goods : money(goods.add(BigDecimal.valueOf(quantity)).add(DELIVERY));
        return new Charge(items, (int) quantity, total);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_MONEY) > 0) throw new OrderBusinessException("商品或订单金额无效或超出范围");
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_MONEY) > 0) throw new OrderBusinessException("订单金额超出范围");
        return rounded;
    }
}
