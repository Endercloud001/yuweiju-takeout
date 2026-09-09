package com.codeying.controller.user;

import com.codeying.result.ApiResult;
import com.codeying.service.DishApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端菜品查询接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/dish")
public class UserDishController {

    private final DishApplicationService dishApplicationService;

    public UserDishController(DishApplicationService dishApplicationService) {
        this.dishApplicationService = dishApplicationService;
    }

    /**
     * 根据分类查询菜品列表（包含口味列表）。
     *
     * @param categoryId 分类 ID
     * @return 菜品列表
     */
    @GetMapping("/list")
    public ApiResult<List<com.codeying.vo.user.dish.DishVO>> list(@RequestParam("categoryId") Long categoryId) {
        return ApiResult.successData(dishApplicationService.userList(categoryId));
    }
}
