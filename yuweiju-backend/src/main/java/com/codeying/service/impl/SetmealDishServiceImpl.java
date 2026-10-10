package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.SetmealDish;
import com.codeying.mapper.SetmealDishMapper;
import com.codeying.service.SetmealDishService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 套餐菜品关联业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class SetmealDishServiceImpl extends ServiceImpl<SetmealDishMapper, SetmealDish> implements SetmealDishService {

    private final SetmealDishMapper catalogMapper;

    public SetmealDishServiceImpl(SetmealDishMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public Long countByDishes(List<Long> dishIds) {
        return catalogMapper.countByDishes(dishIds);
    }

    @Override
    public List<SetmealDish> listBySetmeal(Long setmealId) {
        return catalogMapper.listBySetmeal(setmealId);
    }

    @Override
    public int deleteBySetmeals(List<Long> setmealIds) {
        return catalogMapper.deleteBySetmeals(setmealIds);
    }
}
