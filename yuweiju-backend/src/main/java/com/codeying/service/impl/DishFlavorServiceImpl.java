package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.DishFlavor;
import com.codeying.mapper.DishFlavorMapper;
import com.codeying.service.DishFlavorService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 菜品口味业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class DishFlavorServiceImpl extends ServiceImpl<DishFlavorMapper, DishFlavor> implements DishFlavorService {

    private final DishFlavorMapper catalogMapper;

    public DishFlavorServiceImpl(DishFlavorMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public List<DishFlavor> listByDish(Long dishId) {
        return catalogMapper.listByDish(dishId);
    }

    @Override
    public int deleteByDishes(List<Long> dishIds) {
        return catalogMapper.deleteByDishes(dishIds);
    }
}
