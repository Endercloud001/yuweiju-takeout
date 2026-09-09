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
     * Execute paymentMock.
     *
     * @param userId business identifier
     * @param body request payload
     * @return com.codeying.vo.user.order.OrderPaymentVO result
     */
    com.codeying.vo.user.order.OrderPaymentVO paymentMock(Long userId, OrdersPaymentDTO body);
    /**
     * Change business status.
     *
     * @param userId business identifier
     * @param orderId business identifier
     */
    void cancelByUser(Long userId, Long orderId);
    /**
     * Execute repetition.
     *
     * @param userId business identifier
     * @param orderId business identifier
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
     * Execute reminder.
     *
     * @param userId business identifier
     * @param orderId business identifier
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
     * Execute adminStatistics.
     *
     * @return com.codeying.vo.admin.order.OrderStatisticsVO result
     */
    com.codeying.vo.admin.order.OrderStatisticsVO adminStatistics();

    /**
     * Change business status.
     *
     * @param orderId business identifier
     */
    void markPaid(Long orderId);
    /**
     * Change business status.
     *
     * @param id business identifier
     */
    void confirm(Long id);
    /**
     * Change business status.
     *
     * @param id business identifier
     * @param reason reason parameter
     */
    void reject(Long id, String reason);
    /**
     * Change business status.
     *
     * @param id business identifier
     * @param reason reason parameter
     */
    void cancelByAdmin(Long id, String reason);
    /**
     * Change business status.
     *
     * @param id business identifier
     */
    void deliver(Long id);
    /**
     * Change business status.
     *
     * @param id business identifier
     */
    void complete(Long id);
}
