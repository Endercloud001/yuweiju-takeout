package com.codeying.controller.admin;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.category.CategoryDTO;
import com.codeying.dto.admin.category.CategoryPageQuery;
import com.codeying.result.ApiResult;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.entity.Category;
import com.codeying.service.CategoryApplicationService;
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

import java.util.List;

/**
 * 管理端分类管理接口（菜品分类/套餐分类）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/category")
public class AdminCategoryController {

    private final CategoryApplicationService categoryApplicationService;

    public AdminCategoryController(CategoryApplicationService categoryApplicationService) {
        this.categoryApplicationService = categoryApplicationService;
    }

    /**
     * 新增分类。
     *
     * @param body    分类信息
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping
    public ApiResult<Object> create(@RequestBody @Valid CategoryDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        categoryApplicationService.create(adminId, body);
        return ApiResult.success();
    }

    /**
     * 修改分类。
     *
     * @param body    分类信息（需包含 ID）
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PutMapping
    public ApiResult<Object> update(@RequestBody @Valid CategoryDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        categoryApplicationService.update(adminId, body);
        return ApiResult.success();
    }

    /**
     * 启用/禁用分类。
     *
     * @param status  状态：1-启用 0-禁用
     * @param id      分类 ID
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
        categoryApplicationService.setStatus(adminId, status, id);
        return ApiResult.success();
    }

    /**
     * 删除分类（分类下存在菜品或套餐时禁止删除）。
     *
     * @param id      分类 ID
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @DeleteMapping
    public ApiResult<Object> delete(@RequestParam("id") Long id, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        categoryApplicationService.delete(adminId, id);
        return ApiResult.success();
    }

    /**
     * 查询分类列表。
     *
     * @param type 分类类型：1-菜品分类 2-套餐分类（为空表示全部）
     * @return 分类列表
     */
    @GetMapping("/list")
    public ApiResult<List<Category>> list(@RequestParam(value = "type", required = false) Integer type) {
        return ApiResult.successData(categoryApplicationService.list(type));
    }

    /**
     * 分类分页查询。
     *
     * @return 分页结果
     */
    @GetMapping("/page")
    public ApiResult<PageData<Category>> page(
            @Valid CategoryPageQuery query
    ) {
        return ApiResult.successData(categoryApplicationService.page(query));
    }

    private Long getAdminId(HttpServletRequest request) {
        Object adminIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID);
        if (adminIdObj instanceof Long adminId) return adminId;
        return null;
    }
}
