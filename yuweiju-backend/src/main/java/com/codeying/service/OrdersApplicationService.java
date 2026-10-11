package com.codeying.service;

import com.codeying.dto.user.order.OrdersPaymentDTO;
import com.codeying.common.page.PageData;
import com.codeying.entity.Orders;
import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.dto.admin.order.OrderConditionQuery;
import com.codeying.dto.user.order.OrderHistoryQuery;

/**
 * 订单应用服务：承载订单领域的装配与状态流转。
 *
 * @author Endercloud
 */
public interface OrdersApplicationService {
    /**
     * Convert data structure.
     *
     * @param order order parameter
     * @return com.codeying.vo.user.order.OrderVO result
     */
    com.codeying.vo.user.order.OrderVO buildUserOrderVO(Orders order);
    /**
     * Convert data structure.
     *
     * @param order order parameter
     * @return com.codeying.vo.admin.order.OrderVO result
     */
    com.codeying.vo.admin.order.OrderVO buildAdminOrderVO(Orders order);

    /**
     * Execute submit.
     *
     * @param userId business identifier
     * @param body request payload
     * @return com.codeying.vo.user.order.OrderSubmitVO result
     */
    com.codeying.vo.user.order.OrderSubmitVO submit(Long userId, OrdersSubmitDTO body);
    /**
     * 模拟支付自己的存储订单；1→2写支付时间，已支付2/3/4/5重试不写入。
     *
     * @param userId authenticated positive owner ID
     * @param body order number; missing, foreign or cancelled order fails
     * @return mock payment response retaining the existing protocol
     */
    com.codeying.vo.user.order.OrderPaymentVO paymentMock(Long userId, OrdersPaymentDTO body);
    /**
     * 用户仅可取消自己的待付款/待接单订单；并发状态变化失败。
     *
     * @param userId authenticated positive owner ID
     * @param orderId positive stored order ID
     */
    void cancelByUser(Long userId, Long orderId);
    /**
     * 原子替换自己的购物车为历史订单明细；缺单/越权失败，SQL错误回滚。
     *
     * @param userId authenticated positive owner ID
     * @param orderId positive owned stored order ID
     */
    void repetition(Long userId, Long orderId);
    /**
     * Execute estimatedDeliveryTime.
     *
     * @param userId business identifier
     * @param addressBookId business identifier
     * @return String result
     */
    String estimatedDeliveryTime(Long userId, Long addressBookId);
    /**
     * 校验自己的待接单订单后执行60秒限流；Redis故障保持已有降级。
     *
     * @param userId authenticated positive owner ID
     * @param orderId positive owned stored order ID; missing/wrong state fails
     */
    void reminder(Long userId, Long orderId);
    /**
     * Execute historyOrders.
     *
     * @param userId business identifier
     * @param page paging parameter
     * @param pageSize paging parameter
     * @param status status value
     * @return paged data result
     */
    PageData<com.codeying.vo.user.order.OrderVO> historyOrders(Long userId, Integer page, Integer pageSize, Integer status);
    /**
     * Execute orderDetail.
     *
     * @param userId business identifier
     * @param orderId business identifier
     * @return com.codeying.vo.user.order.OrderVO result
     */
    com.codeying.vo.user.order.OrderVO orderDetail(Long userId, Long orderId);
    /**
     * Execute adminConditionSearch.
     *
     * @param query query parameters
     * @return paged data result
     */
    PageData<com.codeying.vo.admin.order.OrderVO> adminConditionSearch(OrderConditionQuery query);
    /**
     * Execute historyOrders.
     *
     * @param userId business identifier
     * @param query query parameters
     * @return paged data result
     */
    PageData<com.codeying.vo.user.order.OrderVO> historyOrders(Long userId, OrderHistoryQuery query);
    /**
     * Execute adminOrderDetail.
     *
     * @param id business identifier
     * @return com.codeying.vo.admin.order.OrderVO result
     */
    com.codeying.vo.admin.order.OrderVO adminOrderDetail(Long id);
    /**
     * 按既有全历史口径统计待接单、已接单、配送中数量；SQL失败传播。
     *
     * @return 三种状态的数量，保留管理端既有整数字段
     */
    com.codeying.vo.admin.order.OrderStatisticsVO adminStatistics();

    /**
     * 可信调用入口：自己校验缺单/状态；仅1→2，已支付2至5重试不写，取消拒绝。
     *
     * @param orderId positive stored ID; missing/invalid state throws OrderBusinessException
     */
    void markPaid(Long orderId);
    /**
     * 仅待接单→已接单；缺单/错误状态/并发冲突失败。
     *
     * @param id positive stored ID
     */
    void confirm(Long id);
    /**
     * 仅待接单→取消；记录拒单原因和取消时间，不改变支付状态。
     *
     * @param id positive stored ID
     * @param reason nonblank rejection reason, trimmed; invalid parameters/state fail
     */
    void reject(Long id, String reason);
    /**
     * 管理员仅取消待付款/待接单/已接单，记录原因/时间，不执行退款。
     *
     * @param id positive stored ID
     * @param reason nonblank cancellation reason, trimmed; invalid parameters/state fail
     */
    void cancelByAdmin(Long id, String reason);
    /**
     * 仅已接单→配送中；缺单/错误状态/并发冲突失败。
     *
     * @param id positive stored ID
     */
    void deliver(Long id);
    /**
     * 仅配送中→完成并记录送达时间；缺单/错误状态/并发冲突失败。
     *
     * @param id positive stored ID
     */
    void complete(Long id);
}
