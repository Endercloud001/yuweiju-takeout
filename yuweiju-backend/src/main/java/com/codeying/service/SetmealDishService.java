package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.SetmealDish;
import java.util.List;

/**
 * 套餐菜品关联业务服务。
 *
 * @author Endercloud
 */
public interface SetmealDishService extends IService<SetmealDish> {
    /** 统计指定菜品的套餐关联数；空 ID 集合返回零。 */
    Long countByDishes(List<Long> dishIds);

    /** 读取指定套餐关联明细，保持 id 升序。 */
    List<SetmealDish> listBySetmeal(Long setmealId);

    /** 删除指定套餐的关联明细；空 ID 集合不执行删除。 */
    int deleteBySetmeals(List<Long> setmealIds);
}
