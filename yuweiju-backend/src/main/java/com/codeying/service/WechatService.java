package com.codeying.service;

/**
 * 微信相关服务（登录、openid 获取等）。
 *
 * @author Endercloud
 */
public interface WechatService {
    /**
     * 通过小程序登录 code 换取用户 openid。
     *
     * @param code 小程序登录 code
     * @return openid
     */
    String codeToOpenid(String code);
}
