package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.assembler.DishAssembler;
import com.codeying.common.page.PageData;
import com.codeying.dto.admin.dish.DishDTO;
import com.codeying.dto.admin.dish.DishFlavorDTO;
import com.codeying.dto.admin.dish.DishPageQuery;
import com.codeying.entity.Category;
import com.codeying.entity.Dish;
import com.codeying.entity.DishFlavor;
import com.codeying.entity.SetmealDish;
import com.codeying.exception.DishBusinessException;
import com.codeying.service.CategoryService;
import com.codeying.service.DishApplicationService;
import com.codeying.service.DishFlavorService;
import com.codeying.service.DishService;
import com.codeying.service.SetmealDishService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 菜品应用服务实现。
 *
 * @author Endercloud
 */
@Service
public class DishApplicationServiceImpl implements DishApplicationService {

    private final DishService dishService;
    private final DishFlavorService dishFlavorService;
    private final CategoryService categoryService;
    private final SetmealDishService setmealDishService;

    public DishApplicationServiceImpl(
            DishService dishService,
            DishFlavorService dishFlavorService,
            CategoryService categoryService,
            SetmealDishService setmealDishService
    ) {
        this.dishService = dishService;
        this.dishFlavorService = dishFlavorService;
        this.categoryService = categoryService;
        this.setmealDishService = setmealDishService;
    }

    @Override
    @Transactional
    public void create(Long adminId, DishDTO body) {
        if (adminId == null || body == null) throw new DishBusinessException("参数错误");
        if (!StringUtils.hasText(body.getName()) || body.getCategoryId() == null || body.getPrice() == null) {
            throw new DishBusinessException("参数错误");
        }
        Dish entity = new Dish();
        entity.setName(body.getName().trim());
        entity.setCategoryId(body.getCategoryId());
        entity.setPrice(body.getPrice());
        entity.setImage(body.getImage());
        entity.setDescription(body.getDescription());
        entity.setStatus(body.getStatus() == null ? 0 : body.getStatus());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setCreateUser(adminId);
        entity.setUpdateUser(adminId);
        try {
            dishService.save(entity);
        } catch (DuplicateKeyException e) {
            throw new DishBusinessException("菜品名称已存在");
        }
        saveFlavors(entity.getId(), body.getFlavors());
    }

    @Override
    @Transactional
    public void update(Long adminId, DishDTO body) {
        if (adminId == null || body == null || body.getId() == null) throw new DishBusinessException("参数错误");
        Dish exists = dishService.getById(body.getId());
        if (exists == null) throw new DishBusinessException("菜品不存在");

        Dish entity = new Dish();
        entity.setId(body.getId());
        if (StringUtils.hasText(body.getName())) entity.setName(body.getName().trim());
        entity.setCategoryId(body.getCategoryId());
        entity.setPrice(body.getPrice());
        entity.setImage(body.getImage());
        entity.setDescription(body.getDescription());
        if (body.getStatus() != null) entity.setStatus(body.getStatus());
        entity.setUpdateTime(new Date());
        entity.setUpdateUser(adminId);
        try {
            dishService.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new DishBusinessException("菜品名称已存在");
        }
        saveFlavors(body.getId(), body.getFlavors());
    }

    @Override
    @Transactional
    public void delete(Long adminId, String ids) {
        if (adminId == null || !StringUtils.hasText(ids)) throw new DishBusinessException("参数错误");
        String[] parts = ids.split(",");
        List<Long> idList = new ArrayList<>();
        for (String p : parts) {
            if (!StringUtils.hasText(p)) continue;
            try {
                idList.add(Long.valueOf(p.trim()));
            } catch (Exception ignored) {
            }
        }
        if (idList.isEmpty()) throw new DishBusinessException("参数错误");

        long inSetmeal = setmealDishService.count(new QueryWrapper<SetmealDish>().in("dish_id", idList));
        if (inSetmeal > 0) throw new DishBusinessException("菜品已关联套餐，无法删除");

        dishFlavorService.remove(new QueryWrapper<DishFlavor>().in("dish_id", idList));
        dishService.removeByIds(idList);
    }

    @Override
    public void setStatus(Long adminId, Integer status, Long id) {
        if (adminId == null || status == null || id == null) throw new DishBusinessException("参数错误");
        Dish exists = dishService.getById(id);
        if (exists == null) throw new DishBusinessException("菜品不存在");
        Dish update = new Dish();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        dishService.updateById(update);
    }

    @Override
    public com.codeying.vo.admin.dish.DishVO getById(Long id) {
        if (id == null) throw new DishBusinessException("参数错误");
        Dish dish = dishService.getById(id);
        if (dish == null) throw new DishBusinessException("菜品不存在");
        Category category = dish.getCategoryId() == null ? null : categoryService.getById(dish.getCategoryId());
        String categoryName = category == null ? null : category.getName();
        List<DishFlavor> flavors = dishFlavorService.list(new QueryWrapper<DishFlavor>().eq("dish_id", id));
        return DishAssembler.toAdminVO(dish, categoryName, flavors);
    }

    @Override
    public List<Dish> listByCategory(Long categoryId) {
        if (categoryId == null) throw new DishBusinessException("参数错误");
        QueryWrapper<Dish> wrapper = new QueryWrapper<>();
        wrapper.eq("category_id", categoryId);
        wrapper.orderByDesc("update_time").orderByDesc("id");
        return dishService.list(wrapper);
    }

    @Override
    public PageData<com.codeying.vo.admin.dish.DishPageVO> page(Integer page, Integer pageSize, String name, Long categoryId, Integer status) {
        if (page == null || pageSize == null || page <= 0 || pageSize <= 0) throw new DishBusinessException("参数错误");
        QueryWrapper<Dish> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(name)) wrapper.like("name", name.trim());
        if (categoryId != null) wrapper.eq("category_id", categoryId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("update_time").orderByDesc("id");
        IPage<Dish> result = dishService.page(new Page<>(page, pageSize), wrapper);
        List<com.codeying.vo.admin.dish.DishPageVO> records = new ArrayList<>();
        for (Dish d : result.getRecords()) {
            String categoryName = null;
            if (d.getCategoryId() != null) {
                Category category = categoryService.getById(d.getCategoryId());
                categoryName = category == null ? null : category.getName();
            }
            records.add(DishAssembler.toAdminPageVO(d, categoryName));
        }
        PageData<com.codeying.vo.admin.dish.DishPageVO> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(records);
        return data;
    }

    @Override
    public PageData<com.codeying.vo.admin.dish.DishPageVO> page(DishPageQuery query) {
        if (query == null) throw new DishBusinessException("参数错误");
        return page(query.getPage(), query.getPageSize(), query.getName(), query.getCategoryId(), query.getStatus());
    }

    @Override
    public List<com.codeying.vo.user.dish.DishVO> userList(Long categoryId) {
        if (categoryId == null) throw new DishBusinessException("参数错误");
        QueryWrapper<Dish> wrapper = new QueryWrapper<>();
        wrapper.eq("category_id", categoryId);
        wrapper.eq("status", 1);
        wrapper.orderByDesc("update_time").orderByDesc("id");
        List<Dish> dishes = dishService.list(wrapper);
        List<com.codeying.vo.user.dish.DishVO> result = new ArrayList<>();
        for (Dish d : dishes) {
            List<DishFlavor> flavors = dishFlavorService.list(new QueryWrapper<DishFlavor>().eq("dish_id", d.getId()));
            result.add(DishAssembler.toUserVO(d, flavors));
        }
        return result;
    }

    private void saveFlavors(Long dishId, List<DishFlavorDTO> flavors) {
        dishFlavorService.remove(new QueryWrapper<DishFlavor>().eq("dish_id", dishId));
        if (flavors == null || flavors.isEmpty()) return;
        List<DishFlavor> list = new ArrayList<>();
        for (DishFlavorDTO dto : flavors) {
            if (dto == null || !StringUtils.hasText(dto.getName())) continue;
            DishFlavor f = new DishFlavor();
            f.setDishId(dishId);
            f.setName(dto.getName());
            f.setValue(dto.getValue());
            list.add(f);
        }
        if (!list.isEmpty()) dishFlavorService.saveBatch(list);
    }
}
