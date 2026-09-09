package com.codeying.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.utils.JwtUtil;
import com.codeying.result.ApiResult;
import com.codeying.entity.User;
import com.codeying.service.UserService;
import com.codeying.service.WechatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * 用户端认证接口（小程序登录、退出登录）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/user")
public class UserAuthController {

    private final SkyProperties skyProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final WechatService wechatService;
    private final UserService userService;

    public UserAuthController(SkyProperties skyProperties, TokenBlacklistService tokenBlacklistService, WechatService wechatService, UserService userService) {
        this.skyProperties = skyProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        this.wechatService = wechatService;
        this.userService = userService;
    }

    /**
     * 小程序登录：通过 code 换取 openid，并签发用户端 JWT。
     *
     * @param request 登录请求
     * @return 登录结果（包含 token）
     */
    @PostMapping("/login")
    public ApiResult<com.codeying.vo.user.auth.LoginVO> login(@RequestBody @Valid com.codeying.dto.user.auth.LoginDTO request) {

        String openid = wechatService.codeToOpenid(request.getCode());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("openid", openid);
        User user = userService.getOne(wrapper);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setCreateTime(new Date());
            userService.save(user);
        }

        long ttl = skyProperties.getJwt().getUserTtl() == null ? 72000000L : skyProperties.getJwt().getUserTtl();
        String token = JwtUtil.createToken(
                skyProperties.getJwt().getUserSecretKey(),
                ttl,
                user.getId(),
                openid,
                "user"
        );

        com.codeying.vo.user.auth.LoginVO resp = new com.codeying.vo.user.auth.LoginVO();
        resp.setId(user.getId());
        resp.setOpenid(openid);
        resp.setToken(token);
        return ApiResult.successData(resp);
    }

    /**
     * 退出登录：将当前 token 加入黑名单。
     *
     * @param request HTTP 请求（从 header 读取 token）
     * @return 操作结果
     */
    @PostMapping("/logout")
    public ApiResult<Object> logout(HttpServletRequest request) {
        String token = request.getHeader(skyProperties.getJwt().getUserTokenName());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.blacklist(token, skyProperties.getJwt().getUserSecretKey());
        }
        return ApiResult.success();
    }

    // 入参与返回 VO 已外移
}
