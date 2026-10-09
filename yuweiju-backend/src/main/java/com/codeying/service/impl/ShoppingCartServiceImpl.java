package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.ShoppingCart;
import com.codeying.mapper.ShoppingCartMapper;
import com.codeying.service.*;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.exception.OrderBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Date;
import java.util.List;

@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart> implements ShoppingCartService {
    private final DishService dishes;
    private final SetmealService setmeals;
    private final AnalysisObservationService observation;

    public ShoppingCartServiceImpl(DishService dishes, SetmealService setmeals, AnalysisObservationService observation) {
        this.dishes = dishes;
        this.setmeals = setmeals;
        this.observation = observation;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new OrderBusinessException("未登录");
    }

    @Override
    public void requireSaleable(Long dishId, Long setmealId) {
        if ((dishId == null) == (setmealId == null)) throw new OrderBusinessException("参数错误");
        if (dishId != null) {
            var dish = dishes.getById(dishId);
            if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) throw new OrderBusinessException("商品不可售");
        } else {
            var setmeal = setmeals.getById(setmealId);
            if (setmeal == null || !Integer.valueOf(1).equals(setmeal.getStatus())) throw new OrderBusinessException("商品不可售");
        }
    }

    private String flavor(ShoppingCartChangeDTO body) {
        return StringUtils.hasText(body.getDishFlavor()) ? body.getDishFlavor().trim() : null;
    }

    @Override @Transactional
    public void addItem(Long userId, ShoppingCartChangeDTO body) {
        requireUser(userId);
        if (body == null) throw new OrderBusinessException("参数错误");
        requireSaleable(body.getDishId(), body.getSetmealId());
        var existing = baseMapper.findItem(userId, body.getDishId(), body.getSetmealId(), flavor(body));
        if (existing != null) {
            var update = new ShoppingCart();
            update.setId(existing.getId());
            update.setNumber(existing.getNumber() == null ? 2 : existing.getNumber() + 1);
            if (!updateById(update)) throw new OrderBusinessException("购物车写入失败");
        } else {
            var item = new ShoppingCart();
            item.setUserId(userId); item.setDishId(body.getDishId()); item.setSetmealId(body.getSetmealId());
            item.setDishFlavor(flavor(body)); item.setNumber(1); item.setCreateTime(new Date());
            if (body.getDishId() != null) {
                var dish = dishes.getById(body.getDishId());
                item.setName(dish.getName()); item.setImage(dish.getImage()); item.setAmount(dish.getPrice());
            } else {
                var setmeal = setmeals.getById(body.getSetmealId());
                item.setName(setmeal.getName()); item.setImage(setmeal.getImage()); item.setAmount(setmeal.getPrice());
            }
            if (!save(item)) throw new OrderBusinessException("购物车写入失败");
        }
        if (body.getDishId() != null) observation.recordRecommendationClick(userId, body.getDishId());
    }

    @Override @Transactional
    public void subtractItem(Long userId, ShoppingCartChangeDTO body) {
        requireUser(userId);
        if (body == null || (body.getDishId() == null) == (body.getSetmealId() == null)) throw new OrderBusinessException("参数错误");
        var item = baseMapper.findItem(userId, body.getDishId(), body.getSetmealId(), flavor(body));
        if (item == null) throw new OrderBusinessException("购物车中无此商品");
        if (item.getNumber() != null && item.getNumber() > 1) {
            var update = new ShoppingCart(); update.setId(item.getId()); update.setNumber(item.getNumber() - 1);
            if (!updateById(update)) throw new OrderBusinessException("购物车写入失败");
        } else if (!removeById(item.getId())) throw new OrderBusinessException("购物车写入失败");
    }

    @Override
    public List<ShoppingCart> listForUser(Long userId) { requireUser(userId); return baseMapper.findByUser(userId); }

    @Override @Transactional
    public int clearForUser(Long userId) { requireUser(userId); return baseMapper.deleteByUser(userId); }
}
