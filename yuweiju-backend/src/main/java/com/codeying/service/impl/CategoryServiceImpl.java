package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Category;
import com.codeying.mapper.CategoryMapper;
import com.codeying.service.CategoryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 分类业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper catalogMapper;

    public CategoryServiceImpl(CategoryMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public List<Category> listByType(Integer type) {
        return catalogMapper.listByType(type);
    }

    @Override
    public List<Category> listEnabled(Integer type) {
        return catalogMapper.listEnabled(type);
    }

    @Override
    public IPage<Category> pageCatalog(IPage<Category> page, String name, Integer type) {
        return catalogMapper.pageCatalog(page, name, type);
    }
}
