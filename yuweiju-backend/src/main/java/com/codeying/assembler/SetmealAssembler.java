package com.codeying.assembler;

import com.codeying.entity.Setmeal;
import com.codeying.entity.SetmealDish;

import java.util.List;

/**
 * 套餐装配器：将 Entity 装配为 VO。
 *
 * @author Endercloud
 */
public final class SetmealAssembler {
    private SetmealAssembler() {}

    /**
     * 
     *
     * @param setmeal setmeal 
     * @param categoryName categoryName 
     * @param dishes dishes 
     * @return com.codeying.vo.admin.setmeal.SetmealVO 
     */
    public static com.codeying.vo.admin.setmeal.SetmealVO toAdminVO(Setmeal setmeal, String categoryName, List<SetmealDish> dishes) {
        com.codeying.vo.admin.setmeal.SetmealVO vo = new com.codeying.vo.admin.setmeal.SetmealVO();
        vo.setId(setmeal.getId());
        vo.setCategoryId(setmeal.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setDescription(setmeal.getDescription());
        vo.setImage(setmeal.getImage());
        vo.setName(setmeal.getName());
        vo.setPrice(setmeal.getPrice());
        vo.setStatus(setmeal.getStatus());
        vo.setUpdateTime(setmeal.getUpdateTime());
        vo.setSetmealDishes(dishes == null ? List.of() : dishes);
        return vo;
    }

    /**
     * 
     *
     * @param setmeal setmeal 
     * @param categoryName categoryName 
     * @return com.codeying.vo.admin.setmeal.SetmealPageVO 
     */
    public static com.codeying.vo.admin.setmeal.SetmealPageVO toAdminPageVO(Setmeal setmeal, String categoryName) {
        com.codeying.vo.admin.setmeal.SetmealPageVO vo = new com.codeying.vo.admin.setmeal.SetmealPageVO();
        vo.setId(setmeal.getId());
        vo.setCategoryId(setmeal.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setDescription(setmeal.getDescription());
        vo.setImage(setmeal.getImage());
        vo.setName(setmeal.getName());
        vo.setPrice(setmeal.getPrice());
        vo.setStatus(setmeal.getStatus());
        vo.setUpdateTime(setmeal.getUpdateTime());
        return vo;
    }

    /**
     * Convert data structure.
     *
     * @param item item parameter
     * @param dish dish parameter
     * @return com.codeying.vo.user.setmeal.SetmealDishVO result
     */
    public static com.codeying.vo.user.setmeal.SetmealDishVO toUserSetmealDishVO(SetmealDish item, com.codeying.entity.Dish dish) {
        com.codeying.vo.user.setmeal.SetmealDishVO vo = new com.codeying.vo.user.setmeal.SetmealDishVO();
        vo.setCopies(item.getCopies());
        vo.setDescription(dish.getDescription());
        vo.setImage(dish.getImage());
        vo.setName(dish.getName());
        return vo;
    }
}

