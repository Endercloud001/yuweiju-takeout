package com.codeying.controller.user;

import com.codeying.result.ApiResult;
import com.codeying.entity.Category;
import com.codeying.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端分类查询接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/category")
public class UserCategoryController {

    private final CategoryService categoryService;

    public UserCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询分类列表（按类型筛选）。
     *
     * @param type 分类类型：1-菜品分类 2-套餐分类（为空表示全部）
     * @return 分类列表
     */
    @GetMapping("/list")
    public ApiResult<List<Category>> list(@RequestParam(value = "type", required = false) Integer type) {
        return ApiResult.successData(categoryService.listEnabled(type));
    }
}
