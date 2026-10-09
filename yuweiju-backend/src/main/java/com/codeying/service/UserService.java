package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.User;

/**
 * 用户业务服务。
 *
 * @author Endercloud
 */
public interface UserService extends IService<User> {
    /** 微信登录用例：兑换身份、查建用户并签发原协议 JWT；上游失败不创建用户。 */
    com.codeying.vo.user.auth.LoginVO login(String code);
    /** 注册时间闭区间计数。 */
    long countCreatedBetween(java.util.Date begin, java.util.Date end);
    /** 截至指定时间（含）的累计用户数。 */
    long countCreatedThrough(java.util.Date end);
}
