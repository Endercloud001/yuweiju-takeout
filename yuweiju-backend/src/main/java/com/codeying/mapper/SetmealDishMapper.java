package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.SetmealDish;
import java.util.List;

/**
 * 套餐菜品关联 Mapper。
 *
 * @author Endercloud
 */
public interface SetmealDishMapper extends BaseMapper<SetmealDish> {
    /** 统计指定菜品的套餐关联数；空 ID 集合返回零。 */
    default Long countByDishes(List<Long> dishIds) {
        if (dishIds.isEmpty()) return 0L;
        return selectCount(new QueryWrapper<SetmealDish>()
                .in("dish_id", dishIds));
    }

    /** 读取指定套餐关联明细，保持 id 升序。 */
    default List<SetmealDish> listBySetmeal(Long setmealId) {
        return selectList(new QueryWrapper<SetmealDish>()
                .eq("setmeal_id", setmealId)
                .orderByAsc("id"));
    }

    /** 删除指定套餐的关联明细；空 ID 集合不执行删除。 */
    default int deleteBySetmeals(List<Long> setmealIds) {
        if (setmealIds.isEmpty()) return 0;
        return delete(new QueryWrapper<SetmealDish>()
                .in("setmeal_id", setmealIds));
    }
}
