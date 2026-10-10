package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Dish;
import com.codeying.mapper.DishMapper;
import com.codeying.service.DishService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 菜品业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    private final DishMapper catalogMapper;

    public DishServiceImpl(DishMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public Long countByCategory(Long categoryId) {
        return catalogMapper.countByCategory(categoryId);
    }

    @Override
    public List<Dish> listCatalog(Long categoryId, Integer status) {
        return catalogMapper.listCatalog(categoryId, status);
    }

    @Override
    public IPage<Dish> pageCatalog(IPage<Dish> page, String name, Long categoryId, Integer status) {
        return catalogMapper.pageCatalog(page, name, categoryId, status);
    }
}
