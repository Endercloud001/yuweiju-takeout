package com.codeying.controller.user;

import com.codeying.result.ApiResult;
import com.codeying.entity.Setmeal;
import com.codeying.service.SetmealApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端套餐查询接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/setmeal")
public class UserSetmealController {

    private final SetmealApplicationService setmealApplicationService;

    public UserSetmealController(SetmealApplicationService setmealApplicationService) {
        this.setmealApplicationService = setmealApplicationService;
    }

    /**
     * 根据分类查询套餐列表。
     *
     * @param categoryId 套餐分类 ID
     * @return 套餐列表
     */
    @GetMapping("/list")
    public ApiResult<List<Setmeal>> list(@RequestParam("categoryId") Long categoryId) {
        return ApiResult.successData(setmealApplicationService.list(categoryId, 1));
    }

    /**
     * 查询套餐所包含的菜品列表（用于展示套餐明细）。
     *
     * @param setmealId 套餐 ID
     * @return 套餐菜品列表
     */
    @GetMapping("/dish/{id}")
    public ApiResult<List<com.codeying.vo.user.setmeal.SetmealDishVO>> dish(@PathVariable("id") Long setmealId) {
        return ApiResult.successData(setmealApplicationService.userDishes(setmealId));
    }
}
