package com.codeying.controller.admin;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.dish.DishDTO;
import com.codeying.dto.admin.dish.DishPageQuery;
import com.codeying.result.ApiResult;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.entity.Dish;
import com.codeying.service.DishApplicationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端菜品管理接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/dish")
public class AdminDishController {

    private final DishApplicationService dishApplicationService;

    public AdminDishController(DishApplicationService dishApplicationService) {
        this.dishApplicationService = dishApplicationService;
    }

    /**
     * 新增菜品（含口味）。
     *
     * @param body    菜品信息
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping
    public ApiResult<Object> create(@RequestBody DishDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        dishApplicationService.create(adminId, body);
        return ApiResult.success();
    }

    /**
     * 修改菜品（含口味）。
     *
     * @param body    菜品信息（需包含 ID）
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PutMapping
    public ApiResult<Object> update(@RequestBody DishDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        dishApplicationService.update(adminId, body);
        return ApiResult.success();
    }

    /**
     * 批量删除菜品（与套餐关联时禁止删除）。
     *
     * @param ids     逗号分隔的菜品 ID 列表
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @DeleteMapping
    public ApiResult<Object> delete(@RequestParam("ids") String ids, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        dishApplicationService.delete(adminId, ids);
        return ApiResult.success();
    }

    /**
     * 启用/禁用菜品。
     *
     * @param status  状态：1-起售 0-停售
     * @param id      菜品 ID
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping("/status/{status}")
    public ApiResult<Object> setStatus(
            @PathVariable("status") Integer status,
            @RequestParam("id") Long id,
            HttpServletRequest request
    ) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        dishApplicationService.setStatus(adminId, status, id);
        return ApiResult.success();
    }

    /**
     * 根据 ID 查询菜品详情（含口味与分类名）。
     *
     * @param id 菜品 ID
     * @return 菜品详情
     */
    @GetMapping("/{id}")
    public ApiResult<com.codeying.vo.admin.dish.DishVO> getById(@PathVariable("id") Long id) {
        return ApiResult.successData(dishApplicationService.getById(id));
    }

    /**
     * 根据分类查询菜品列表。
     *
     * @param categoryId 分类 ID
     * @return 菜品列表
     */
    @GetMapping("/list")
    public ApiResult<List<Dish>> list(@RequestParam("categoryId") Long categoryId) {
        return ApiResult.successData(dishApplicationService.listByCategory(categoryId));
    }

    /**
     * 菜品分页查询。
     * 
     * @return 分页结果
     */
    @GetMapping("/page")
    public ApiResult<PageData<com.codeying.vo.admin.dish.DishPageVO>> page(
            @Valid DishPageQuery query
    ) {
        return ApiResult.successData(dishApplicationService.page(query));
    }
    private Long getAdminId(HttpServletRequest request) {
        Object adminIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID);
        if (adminIdObj instanceof Long adminId) return adminId;
        return null;
    }
}
