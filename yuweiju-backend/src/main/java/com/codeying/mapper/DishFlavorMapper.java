package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.DishFlavor;
import java.util.List;

/**
 * 菜品口味 Mapper。
 *
 * @author Endercloud
 */
public interface DishFlavorMapper extends BaseMapper<DishFlavor> {
    /** 读取指定菜品的口味，保持原查询未指定顺序的语义。 */
    default List<DishFlavor> listByDish(Long dishId) {
        return selectList(new QueryWrapper<DishFlavor>()
                .eq("dish_id", dishId));
    }

    /** 删除指定菜品的全部口味；空 ID 集合不执行删除。 */
    default int deleteByDishes(List<Long> dishIds) {
        if (dishIds.isEmpty()) return 0;
        return delete(new QueryWrapper<DishFlavor>()
                .in("dish_id", dishIds));
    }
}
