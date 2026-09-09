package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.assembler.SetmealAssembler;
import com.codeying.common.page.PageData;
import com.codeying.dto.admin.setmeal.SetmealDTO;
import com.codeying.dto.admin.setmeal.SetmealDishDTO;
import com.codeying.dto.admin.setmeal.SetmealPageQuery;
import com.codeying.entity.Category;
import com.codeying.entity.Dish;
import com.codeying.entity.Setmeal;
import com.codeying.entity.SetmealDish;
import com.codeying.exception.SetmealBusinessException;
import com.codeying.service.CategoryService;
import com.codeying.service.DishService;
import com.codeying.service.SetmealApplicationService;
import com.codeying.service.SetmealDishService;
import com.codeying.service.SetmealService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 套餐应用服务实现。
 *
 * @author Endercloud
 */
@Service
public class SetmealApplicationServiceImpl implements SetmealApplicationService {

    private final SetmealService setmealService;
    private final SetmealDishService setmealDishService;
    private final CategoryService categoryService;
    private final DishService dishService;

    public SetmealApplicationServiceImpl(SetmealService setmealService, SetmealDishService setmealDishService, CategoryService categoryService, DishService dishService) {
        this.setmealService = setmealService;
        this.setmealDishService = setmealDishService;
        this.categoryService = categoryService;
        this.dishService = dishService;
    }

    @Override
    @Transactional
    public void create(Long adminId, SetmealDTO body) {
        if (adminId == null || body == null) throw new SetmealBusinessException("参数错误");
        if (!StringUtils.hasText(body.getName()) || body.getCategoryId() == null || body.getPrice() == null) throw new SetmealBusinessException("参数错误");
        if (body.getSetmealDishes() == null || body.getSetmealDishes().isEmpty()) throw new SetmealBusinessException("套餐菜品不能为空");

        Date now = new Date();
        Setmeal entity = new Setmeal();
        entity.setCategoryId(body.getCategoryId());
        entity.setName(body.getName().trim());
        entity.setPrice(body.getPrice());
        entity.setStatus(body.getStatus() == null ? 0 : body.getStatus());
        entity.setDescription(body.getDescription());
        entity.setImage(body.getImage());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUser(adminId);
        entity.setUpdateUser(adminId);
        try {
            setmealService.save(entity);
        } catch (DuplicateKeyException e) {
            throw new SetmealBusinessException("套餐名称已存在");
        }
        saveSetmealDishes(entity.getId(), body.getSetmealDishes());
    }

    @Override
    @Transactional
    public void update(Long adminId, SetmealDTO body) {
        if (adminId == null || body == null || body.getId() == null) throw new SetmealBusinessException("参数错误");
        Setmeal exists = setmealService.getById(body.getId());
        if (exists == null) throw new SetmealBusinessException("套餐不存在");

        Setmeal entity = new Setmeal();
        entity.setId(body.getId());
        entity.setCategoryId(body.getCategoryId());
        if (StringUtils.hasText(body.getName())) entity.setName(body.getName().trim());
        entity.setPrice(body.getPrice());
        if (body.getStatus() != null) entity.setStatus(body.getStatus());
        entity.setDescription(body.getDescription());
        entity.setImage(body.getImage());
        entity.setUpdateTime(new Date());
        entity.setUpdateUser(adminId);
        try {
            setmealService.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new SetmealBusinessException("套餐名称已存在");
        }
        if (body.getSetmealDishes() != null) {
            saveSetmealDishes(body.getId(), body.getSetmealDishes());
        }
    }

    @Override
    @Transactional
    public void delete(Long adminId, String ids) {
        if (adminId == null || !StringUtils.hasText(ids)) throw new SetmealBusinessException("参数错误");
        List<Long> idList = parseIds(ids);
        if (idList.isEmpty()) throw new SetmealBusinessException("参数错误");

        long selling = setmealService.count(new QueryWrapper<Setmeal>().in("id", idList).eq("status", 1));
        if (selling > 0) throw new SetmealBusinessException("套餐正在售卖中，无法删除");

        setmealDishService.remove(new QueryWrapper<SetmealDish>().in("setmeal_id", idList));
        setmealService.removeByIds(idList);
    }

    @Override
    public void setStatus(Long adminId, Integer status, Long id) {
        if (adminId == null || status == null || id == null) throw new SetmealBusinessException("参数错误");
        Setmeal exists = setmealService.getById(id);
        if (exists == null) throw new SetmealBusinessException("套餐不存在");
        Setmeal update = new Setmeal();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        setmealService.updateById(update);
    }

    @Override
    public com.codeying.vo.admin.setmeal.SetmealVO getById(Long id) {
        if (id == null) throw new SetmealBusinessException("参数错误");
        Setmeal setmeal = setmealService.getById(id);
        if (setmeal == null) throw new SetmealBusinessException("套餐不存在");
        Category category = setmeal.getCategoryId() == null ? null : categoryService.getById(setmeal.getCategoryId());
        String categoryName = category == null ? null : category.getName();
        List<SetmealDish> dishes = setmealDishService.list(new QueryWrapper<SetmealDish>().eq("setmeal_id", id).orderByAsc("id"));
        return SetmealAssembler.toAdminVO(setmeal, categoryName, dishes);
    }

    @Override
    public List<Setmeal> list(Long categoryId, Integer status) {
        QueryWrapper<Setmeal> wrapper = new QueryWrapper<>();
        if (categoryId != null) wrapper.eq("category_id", categoryId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("update_time").orderByDesc("id");
        return setmealService.list(wrapper);
    }

    @Override
    public PageData<com.codeying.vo.admin.setmeal.SetmealPageVO> page(Integer page, Integer pageSize, String name, Long categoryId, Integer status) {
        if (page == null || pageSize == null || page <= 0 || pageSize <= 0) throw new SetmealBusinessException("参数错误");
        QueryWrapper<Setmeal> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(name)) wrapper.like("name", name.trim());
        if (categoryId != null) wrapper.eq("category_id", categoryId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("update_time").orderByDesc("id");
        IPage<Setmeal> result = setmealService.page(new Page<>(page, pageSize), wrapper);

        List<Long> categoryIds = new ArrayList<>();
        for (Setmeal s : result.getRecords()) {
            if (s.getCategoryId() != null && !categoryIds.contains(s.getCategoryId())) categoryIds.add(s.getCategoryId());
        }
        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (Category c : categoryService.listByIds(categoryIds)) {
                if (c.getId() != null) categoryNameMap.put(c.getId(), c.getName());
            }
        }

        List<com.codeying.vo.admin.setmeal.SetmealPageVO> records = new ArrayList<>();
        for (Setmeal s : result.getRecords()) {
            records.add(SetmealAssembler.toAdminPageVO(s, categoryNameMap.get(s.getCategoryId())));
        }
        PageData<com.codeying.vo.admin.setmeal.SetmealPageVO> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(records);
        return data;
    }

    @Override
    public PageData<com.codeying.vo.admin.setmeal.SetmealPageVO> page(SetmealPageQuery query) {
        if (query == null) throw new SetmealBusinessException("参数错误");
        return page(query.getPage(), query.getPageSize(), query.getName(), query.getCategoryId(), query.getStatus());
        }

    @Override
    public List<com.codeying.vo.user.setmeal.SetmealDishVO> userDishes(Long setmealId) {
        if (setmealId == null) throw new SetmealBusinessException("参数错误");
        QueryWrapper<SetmealDish> wrapper = new QueryWrapper<>();
        wrapper.eq("setmeal_id", setmealId);
        wrapper.orderByAsc("id");
        List<SetmealDish> items = setmealDishService.list(wrapper);
        if (items.isEmpty()) return List.of();

        List<Long> dishIds = new ArrayList<>();
        for (SetmealDish item : items) {
            if (item.getDishId() != null && !dishIds.contains(item.getDishId())) dishIds.add(item.getDishId());
        }
        Map<Long, Dish> dishMap = new HashMap<>();
        if (!dishIds.isEmpty()) {
            for (Dish d : dishService.listByIds(dishIds)) {
                if (d.getId() != null) dishMap.put(d.getId(), d);
            }
        }

        List<com.codeying.vo.user.setmeal.SetmealDishVO> result = new ArrayList<>();
        for (SetmealDish item : items) {
            Dish dish = dishMap.get(item.getDishId());
            if (dish == null) continue;
            result.add(SetmealAssembler.toUserSetmealDishVO(item, dish));
        }
        return result;
    }

    private void saveSetmealDishes(Long setmealId, List<SetmealDishDTO> dishes) {
        setmealDishService.remove(new QueryWrapper<SetmealDish>().eq("setmeal_id", setmealId));
        if (dishes == null || dishes.isEmpty()) return;
        List<SetmealDish> entities = new ArrayList<>();
        for (SetmealDishDTO dto : dishes) {
            if (dto == null || dto.getDishId() == null || dto.getCopies() == null) continue;
            SetmealDish e = new SetmealDish();
            e.setSetmealId(setmealId);
            e.setDishId(dto.getDishId());
            e.setCopies(dto.getCopies());
            entities.add(e);
        }
        if (!entities.isEmpty()) setmealDishService.saveBatch(entities);
    }

    private List<Long> parseIds(String ids) {
        String[] parts = ids.split(",");
        List<Long> result = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) continue;
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
