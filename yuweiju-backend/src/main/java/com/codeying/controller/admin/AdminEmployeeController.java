package com.codeying.controller.admin;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.employee.EditPasswordDTO;
import com.codeying.dto.admin.employee.EmployeeDTO;
import com.codeying.dto.admin.employee.EmployeePageQuery;
import com.codeying.dto.admin.employee.LoginDTO;
import com.codeying.result.ApiResult;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.service.EmployeeApplicationService;
import com.codeying.vo.admin.employee.EmployeeVO;
import com.codeying.vo.admin.employee.LoginVO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.Date;

/**
 * 管理端员工接口（登录、员工管理、修改密码、退出登录）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/employee")
public class AdminEmployeeController {

    private final EmployeeApplicationService employeeApplicationService;
    private final SkyProperties skyProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public AdminEmployeeController(EmployeeApplicationService employeeApplicationService, SkyProperties skyProperties, TokenBlacklistService tokenBlacklistService) {
        this.employeeApplicationService = employeeApplicationService;
        this.skyProperties = skyProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 管理端登录：校验账号密码并签发管理员 JWT。
     *
     * @param body  登录请求
     * @param response HTTP 响应（写入 token cookie）
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResult<LoginVO> login(@RequestBody @Valid LoginDTO body, HttpServletResponse response) {
        LoginVO vo = employeeApplicationService.login(body);
        writeTokenCookie(response, skyProperties.getJwt().getAdminTokenName(), vo.getToken(), vo.getTtlMillis());
        return ApiResult.successData(vo);
    }

    /**
     * 修改当前登录员工密码。
     *
     * @param body    请求体（包含员工 ID、旧密码、新密码）
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PutMapping("/editPassword")
    public ApiResult<Object> editPassword(@RequestBody @Valid EditPasswordDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        employeeApplicationService.editPassword(adminId, body);
        return ApiResult.success();
    }

    /**
     * 启用/禁用员工。
     *
     * @param status  状态：1-启用 0-禁用
     * @param id      员工 ID
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping("/status/{status}")
    public ApiResult<Object> status(@PathVariable("status") Integer status, @RequestParam("id") Long id, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        employeeApplicationService.setStatus(adminId, status, id);
        return ApiResult.success();
    }

    /**
     * 员工分页查询。
     *
     * @param query    查询参数
     * @param request  HTTP 请求（用于获取管理员 ID）
     * @return 分页结果
     */
    @GetMapping("/page")
    public ApiResult<PageData<EmployeeVO>> page(@Valid EmployeePageQuery query, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(employeeApplicationService.page(query));
    }

    /**
     * 新增员工。
     *
     * @param body    员工信息
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PostMapping
    public ApiResult<Object> create(@RequestBody @Valid EmployeeDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        employeeApplicationService.create(adminId, body);
        return ApiResult.success();
    }

    /**
     * 根据 ID 查询员工详情。
     *
     * @param id      员工 ID
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 员工信息
     */
    @GetMapping("/{id}")
    public ApiResult<EmployeeVO> getById(@PathVariable("id") Long id, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(employeeApplicationService.getById(id));
    }

    /**
     * 修改员工信息。
     *
     * @param body    员工信息（需包含 ID）
     * @param request HTTP 请求（用于获取管理员 ID）
     * @return 操作结果
     */
    @PutMapping
    public ApiResult<Object> update(@RequestBody @Valid EmployeeDTO body, HttpServletRequest request) {
        Long adminId = getAdminId(request);
        if (adminId == null) return ApiResult.unauthorized("未登录");
        employeeApplicationService.update(adminId, body);
        return ApiResult.success();
    }

    /**
     * 退出登录：将 token 加入黑名单并清理 cookie。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @return 操作结果
     */
    @PostMapping("/logout")
    public ApiResult<Object> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader(skyProperties.getJwt().getAdminTokenName());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.blacklist(token, skyProperties.getJwt().getAdminSecretKey());
        }
        clearTokenCookie(response, skyProperties.getJwt().getAdminTokenName());
        return ApiResult.success();
    }

    private void writeTokenCookie(HttpServletResponse response, String cookieName, String token, long ttlMillis) {
        if (response == null || !StringUtils.hasText(cookieName) || !StringUtils.hasText(token)) return;
        int maxAge = ttlMillis <= 0 ? -1 : (int) (ttlMillis / 1000);
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(false);
        response.addCookie(cookie);
    }

    private void clearTokenCookie(HttpServletResponse response, String cookieName) {
        if (response == null || !StringUtils.hasText(cookieName)) return;
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(false);
        response.addCookie(cookie);
    }

    private Long getAdminId(HttpServletRequest request) {
        Object adminIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID);
        if (adminIdObj instanceof Long adminId) return adminId;
        return null;
    }

}
