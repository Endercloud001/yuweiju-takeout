package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.User;
import com.codeying.mapper.UserMapper;
import com.codeying.properties.SkyProperties;
import com.codeying.service.UserService;
import com.codeying.service.WechatService;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.user.auth.LoginVO;
import org.springframework.stereotype.Service;
import java.util.Date;

/** 微信登录编排及用户业务规则。 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final WechatService wechatService;
    private final SkyProperties skyProperties;

    public UserServiceImpl(UserMapper userMapper, WechatService wechatService, SkyProperties skyProperties) {
        this.baseMapper = userMapper;
        this.wechatService = wechatService;
        this.skyProperties = skyProperties;
    }

    @Override
    public LoginVO login(String code) {
        String openid = wechatService.codeToOpenid(code);
        User user = baseMapper.findByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setCreateTime(new Date());
            save(user);
        }
        long ttl = skyProperties.getJwt().getUserTtl() == null ? 72000000L : skyProperties.getJwt().getUserTtl();
        LoginVO result = new LoginVO();
        result.setId(user.getId());
        result.setOpenid(openid);
        result.setToken(JwtUtil.createToken(skyProperties.getJwt().getUserSecretKey(), ttl,
                user.getId(), openid, "user"));
        return result;
    }

    @Override
    public long countCreatedBetween(Date begin, Date end) {
        return baseMapper.countCreatedInRange(begin, end);
    }

    @Override
    public long countCreatedThrough(Date end) {
        return baseMapper.countCreatedThrough(end);
    }
}
