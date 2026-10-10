package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.OrderDetail;

/**
 * 订单明细业务服务。
 *
 * @author Endercloud
 */
public interface OrderDetailService extends IService<OrderDetail> {
    /** Reads stored snapshots for one order, never updates historical images or prices. */
    java.util.List<OrderDetail> listForOrder(Long orderId);
}
