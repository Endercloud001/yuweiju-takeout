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
     * @return 1-营业 0-打烊；prod缺失键默认营业，核心缓存故障抛业务异常
     */
    int getStatus();

    /**
     * 设置店铺营业状态。
     *
     * @param status 0-打烊，其他值按既有规则归一为1；prod写失败抛业务异常
     */
    void setStatus(int status);
}
