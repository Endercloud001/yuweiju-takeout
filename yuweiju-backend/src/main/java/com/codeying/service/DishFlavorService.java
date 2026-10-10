package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.DishFlavor;
import java.util.List;

/**
 * 菜品口味业务服务。
 *
 * @author Endercloud
 */
public interface DishFlavorService extends IService<DishFlavor> {
    /** 读取指定菜品的口味，保持原查询未指定顺序的语义。 */
    List<DishFlavor> listByDish(Long dishId);

    /** 删除指定菜品的全部口味；空 ID 集合不执行删除。 */
    int deleteByDishes(List<Long> dishIds);
}
