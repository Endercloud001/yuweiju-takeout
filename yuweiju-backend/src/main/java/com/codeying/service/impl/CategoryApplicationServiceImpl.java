package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.common.page.PageData;
import com.codeying.entity.Category;
import com.codeying.exception.CategoryBusinessException;
import com.codeying.dto.admin.category.CategoryDTO;
import com.codeying.dto.admin.category.CategoryPageQuery;
import com.codeying.service.CategoryApplicationService;
import com.codeying.service.CategoryService;
import com.codeying.service.DishService;
import com.codeying.service.SetmealService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 分类应用服务实现。
 *
 * @author Endercloud
 */
@Service
public class CategoryApplicationServiceImpl implements CategoryApplicationService {

    private final CategoryService categoryService;
    private final DishService dishService;
    private final SetmealService setmealService;

    public CategoryApplicationServiceImpl(CategoryService categoryService, DishService dishService, SetmealService setmealService) {
        this.categoryService = categoryService;
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    @Override
    @Transactional
    public void create(Long adminId, CategoryDTO body) {
        if (adminId == null || body == null) throw new CategoryBusinessException("参数错误");
        if (!StringUtils.hasText(body.getName()) || body.getType() == null || body.getSort() == null) throw new CategoryBusinessException("参数错误");
        if (!isValidType(body.getType())) throw new CategoryBusinessException("参数错误");
        Date now = new Date();
        Category entity = new Category();
        entity.setName(body.getName().trim());
        entity.setType(body.getType());
        entity.setSort(body.getSort());
        entity.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUser(adminId);
        entity.setUpdateUser(adminId);
        try {
            categoryService.save(entity);
        } catch (DuplicateKeyException e) {
            throw new CategoryBusinessException("分类名称已存在", e);
        }
    }

    @Override
    @Transactional
    public void update(Long adminId, CategoryDTO body) {
        if (adminId == null || body == null || body.getId() == null) throw new CategoryBusinessException("参数错误");
        Category exists = categoryService.getById(body.getId());
        if (exists == null) throw new CategoryBusinessException("分类不存在");
        Category update = new Category();
        update.setId(body.getId());
        if (StringUtils.hasText(body.getName())) update.setName(body.getName().trim());
        if (body.getSort() != null) update.setSort(body.getSort());
        if (body.getType() != null) {
            if (!isValidType(body.getType())) throw new CategoryBusinessException("参数错误");
            update.setType(body.getType());
        }
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        try {
            categoryService.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new CategoryBusinessException("分类名称已存在", e);
        }
    }

    @Override
    public void setStatus(Long adminId, Integer status, Long id) {
        if (adminId == null || status == null || id == null) throw new CategoryBusinessException("参数错误");
        Category exists = categoryService.getById(id);
        if (exists == null) throw new CategoryBusinessException("分类不存在");
        Category update = new Category();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        categoryService.updateById(update);
    }

    @Override
    @Transactional
    public void delete(Long adminId, Long id) {
        if (adminId == null || id == null) throw new CategoryBusinessException("参数错误");
        Category category = categoryService.getById(id);
        if (category == null) throw new CategoryBusinessException("分类不存在");
        if (Integer.valueOf(1).equals(category.getType())) {
            if (dishService.countByCategory(id) > 0) {
                throw new CategoryBusinessException("当前分类下存在菜品，无法删除");
            }
        } else if (Integer.valueOf(2).equals(category.getType())) {
            if (setmealService.countByCategory(id) > 0) {
                throw new CategoryBusinessException("当前分类下存在套餐，无法删除");
            }
        }
        categoryService.removeById(id);
    }

    @Override
    public List<Category> list(Integer type) {
        return categoryService.listByType(type);
    }

    private boolean isValidType(Integer type) {
        return type != null && (type == 1 || type == 2);
    }

    @Override
    public PageData<Category> page(Integer page, Integer pageSize, String name, Integer type) {
        if (page == null || pageSize == null || page <= 0 || pageSize <= 0) throw new CategoryBusinessException("参数错误");
        IPage<Category> result = categoryService.pageCatalog(new Page<>(page, pageSize), name, type);
        PageData<Category> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(result.getRecords());
        return data;
    }

    @Override
    public PageData<Category> page(CategoryPageQuery query) {
        if (query == null || query.getPage() == null || query.getPageSize() == null || query.getPage() <= 0 || query.getPageSize() <= 0) {
            throw new CategoryBusinessException("参数错误");
        }
        return page(query.getPage(), query.getPageSize(), query.getName(), query.getType());
    }
}
