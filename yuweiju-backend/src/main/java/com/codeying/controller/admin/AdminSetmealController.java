package com.codeying.controller.admin;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.setmeal.SetmealDTO;
import com.codeying.dto.admin.setmeal.SetmealPageQuery;
import com.codeying.result.ApiResult;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.entity.Setmeal;
import com.codeying.service.SetmealApplicationService;
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
 * 管理端套餐管理接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/setmeal")
public class AdminSetmealController {

    private final SetmealApplicationService setmealApplicationService;

    public AdminSetmealController(SetmealApplicationService setmealApplicationService) {
        this.setmealApplicationService = setmealApplicationService;
    }

    /**
     * 新增套餐（包含套餐菜品）。
     *
     * @param body    套餐信息
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping
    public ApiResult<Object> create(@RequestBody SetmealDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        setmealApplicationService.create(adminId, body);
        return ApiResult.success();
    }

    /**
     * 修改套餐（包含套餐菜品）。
     *
     * @param body    套餐信息（需包含 ID）
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PutMapping
    public ApiResult<Object> update(@RequestBody SetmealDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        setmealApplicationService.update(adminId, body);
        return ApiResult.success();
    }

    /**
     * 根据 ID 查询套餐详情（包含套餐菜品与分类名）。
     *
     * @param id 套餐 ID
     * @return 套餐详情
     */
    @GetMapping("/{id}")
    public ApiResult<com.codeying.vo.admin.setmeal.SetmealVO> getById(@PathVariable("id") Long id) {
        return ApiResult.successData(setmealApplicationService.getById(id));
    }

    /**
     * 查询套餐列表。
     *
     * @param categoryId 分类 ID
     * @param status     状态：1-起售 0-停售
     * @return 套餐列表
     */
    @GetMapping("/list")
    public ApiResult<List<Setmeal>> list(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) Integer status
    ) {
        return ApiResult.successData(setmealApplicationService.list(categoryId, status));
    }

    /**
     * 套餐分页查询。
     * 
     * @return 分页结果
     */
    @GetMapping("/page")
    public ApiResult<PageData<com.codeying.vo.admin.setmeal.SetmealPageVO>> page(
            @Valid SetmealPageQuery query
    ) {
        return ApiResult.successData(setmealApplicationService.page(query));
    }

    /**
     * 启用/禁用套餐。
     *
     * @param status  状态：1-起售 0-停售
     * @param id      套餐 ID
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
        setmealApplicationService.setStatus(adminId, status, id);
        return ApiResult.success();
    }

    /**
     * 批量删除套餐（售卖中禁止删除）。
     *
     * @param ids     逗号分隔的套餐 ID 列表
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @DeleteMapping
    public ApiResult<Object> delete(@RequestParam("ids") String ids, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        setmealApplicationService.delete(adminId, ids);
        return ApiResult.success();
    }
    private Long getAdminId(HttpServletRequest request) {
        Object adminIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID);
        if (adminIdObj instanceof Long adminId) return adminId;
        return null;
    }
}
