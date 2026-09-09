package com.codeying.service;

/**
 * 店铺营业状态服务。
 *
 * @author Endercloud
 */
public interface ShopStatusService {
    /**
     * 获取店铺营业状态。
     *
     * @return 1-营业 0-打烊
     */
    int getStatus();

    /**
     * 设置店铺营业状态。
     *
     * @param status 1-营业 0-打烊
     */
    void setStatus(int status);
}
