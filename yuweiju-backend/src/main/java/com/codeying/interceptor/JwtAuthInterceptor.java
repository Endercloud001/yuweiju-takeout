package com.codeying.interceptor;

import com.codeying.result.ApiResult;
import com.codeying.controller.admin.AdminDishController;
import com.codeying.controller.admin.AdminSetmealController;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;

/**
 * JWT 鉴权拦截器（管理端/用户端共用）。
 *
 * @author Endercloud
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ADMIN_ID = "currentAdminId";
    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_JTI = "currentTokenJti";

    private final SkyProperties skyProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(SkyProperties skyProperties, TokenBlacklistService tokenBlacklistService, ObjectMapper objectMapper) {
        this.skyProperties = skyProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    /**
     * 从请求头读取 token（支持 Bearer 前缀），解析 JWT 并将用户信息写入 request attributes。
     * @param request  请求
     * @param response 响应
     * @param handler  handler
     * @return 是否放行
     * @throws Exception 写响应失败等
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        boolean isAdmin = path != null && path.startsWith("/admin/");
        boolean isUser = path != null && path.startsWith("/user/");
        if (!isAdmin && !isUser) return true;

        String tokenName = isAdmin ? skyProperties.getJwt().getAdminTokenName() : skyProperties.getJwt().getUserTokenName();
        String secret = isAdmin ? skyProperties.getJwt().getAdminSecretKey() : skyProperties.getJwt().getUserSecretKey();
        if (!StringUtils.hasText(tokenName) || !StringUtils.hasText(secret)) {
            writeUnauthorized(response, "服务未配置");
            return false;
        }

        String token = request.getHeader(tokenName);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }

        Claims claims;
        try {
            claims = JwtUtil.parseClaims(token, secret);
        } catch (JwtException e) {
            writeUnauthorized(response, "登录已失效");
            return false;
        }

        // Issues #17/#18/#60: these administrative operations require scope as well as signature.
        boolean analysisPath = "/admin/analysis".equals(path) || path.startsWith("/admin/analysis/");
        boolean riskPath = "/admin/order-risk".equals(path) || path.startsWith("/admin/order-risk/");
        // MVC has already resolved matrix parameters and other supported path forms.
        boolean catalogSave = handler instanceof HandlerMethod method
                && (method.getBeanType() == AdminDishController.class || method.getBeanType() == AdminSetmealController.class)
                && (method.getMethod().getName().equals("create") || method.getMethod().getName().equals("update"));
        if (isAdmin && (analysisPath || riskPath || catalogSave) && !"admin".equals(claims.getSubject())) {
            writeUnauthorized(response, catalogSave ? "无管理员目录权限" : riskPath ? "无管理员风控权限" : "无管理员分析权限");
            return false;
        }

        String jti = claims.getId();
        if (tokenBlacklistService.isBlacklisted(jti)) {
            writeUnauthorized(response, "登录已失效");
            return false;
        }

        Long uid = claims.get("uid", Long.class);
        if (uid == null) {
            writeUnauthorized(response, "登录已失效");
            return false;
        }

        request.setAttribute(ATTR_JTI, jti);
        if (isAdmin) {
            request.setAttribute(ATTR_ADMIN_ID, uid);
        } else {
            request.setAttribute(ATTR_USER_ID, uid);
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.unauthorized(message)));
    }
}
