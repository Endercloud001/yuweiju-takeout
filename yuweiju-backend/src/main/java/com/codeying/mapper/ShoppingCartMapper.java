package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.ShoppingCart;
import java.util.List;

/** User-scoped cart persistence. */
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {
    default List<ShoppingCart> findByUser(Long userId) {
        return selectList(new QueryWrapper<ShoppingCart>().eq("user_id", userId)
                .orderByAsc("create_time").orderByAsc("id"));
    }
    default int deleteByUser(Long userId) {
        return delete(new QueryWrapper<ShoppingCart>().eq("user_id", userId));
    }
    default ShoppingCart findItem(Long userId, Long dishId, Long setmealId, String flavor) {
        QueryWrapper<ShoppingCart> query = new QueryWrapper<ShoppingCart>().eq("user_id", userId);
        if (dishId != null) {
            query.eq("dish_id", dishId);
            if (flavor == null) query.and(w -> w.isNull("dish_flavor").or().eq("dish_flavor", ""));
            else query.eq("dish_flavor", flavor);
        } else query.eq("setmeal_id", setmealId);
        return selectOne(query.last("limit 1"));
    }
}
