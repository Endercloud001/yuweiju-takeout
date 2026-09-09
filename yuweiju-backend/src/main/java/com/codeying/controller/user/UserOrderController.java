package com.codeying.controller.user;

import com.codeying.common.page.PageData;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.dto.user.order.OrderHistoryQuery;
import com.codeying.dto.user.order.OrdersPaymentDTO;
import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.entity.Orders;
import com.codeying.service.OrdersService;
import com.codeying.vo.user.order.OrderPaymentVO;
import com.codeying.vo.user.order.OrderSubmitVO;
import com.codeying.vo.user.order.OrderVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端订单接口（下单、支付、查询订单、取消、催单、再来一单等）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/order")
public class UserOrderController {

    private final OrdersService ordersService;
    private final com.codeying.service.OrdersApplicationService ordersApplicationService;

    public UserOrderController(
            OrdersService ordersService,
            com.codeying.service.OrdersApplicationService ordersApplicationService
    ) {
        this.ordersService = ordersService;
        this.ordersApplicationService = ordersApplicationService;
    }

    /**
     * 用户提交订单。
     *
     * @param body    下单参数
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 下单结果
     */
    @PostMapping("/submit")
    public ApiResult<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (body == null || body.getAddressBookId() == null) return ApiResult.badRequest("参数错误");
        try {
            return ApiResult.successData(ordersApplicationService.submit(userId, body));
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
    }

    /**
     * 计算并返回预计送达时间（HH:mm）。
     *
     * @param addressBookId 地址簿 ID
     * @param request       HTTP 请求（用于获取用户 ID）
     * @return 预计送达时间（HH:mm）
     */
    @GetMapping("/estimatedDeliveryTime")
    public ApiResult<String> estimatedDeliveryTime(@RequestParam("addressBookId") Long addressBookId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (addressBookId == null) return ApiResult.badRequest("参数错误");
        try {
            return ApiResult.successData(ordersApplicationService.estimatedDeliveryTime(userId, addressBookId));
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
    }

    /**
     * 支付订单（当前为 mock 支付流程，并更新支付状态）。
     *
     * @param body    支付参数
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 支付参数（用于前端发起支付）
     */
    @PutMapping("/payment")
    public ApiResult<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (body == null || !StringUtils.hasText(body.getOrderNumber())) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(ordersApplicationService.paymentMock(userId, body));
    }

    /**
     * 查询历史订单（分页，可按状态筛选）。
     *
     * @param request  HTTP 请求（用于获取用户 ID）
     * @return 分页结果
     */
    @GetMapping("/historyOrders")
    public ApiResult<PageData<OrderVO>> historyOrders(@Valid OrderHistoryQuery query, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(ordersApplicationService.historyOrders(userId, query));
    }

    /**
     * 查询订单详情（包含明细）。
     *
     * @param id      订单 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 订单详情
     */
    @GetMapping("/orderDetail/{id}")
    public ApiResult<OrderVO> orderDetail(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (id == null) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(ordersApplicationService.orderDetail(userId, id));
    }

    /**
     * 用户取消订单（仅允许待付款/待接单状态取消）。
     *
     * @param id      订单 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PutMapping("/cancel/{id}")
    public ApiResult<Object> cancel(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (id == null) return ApiResult.badRequest("参数错误");
        ordersApplicationService.cancelByUser(userId, id);
        return ApiResult.success();
    }

    /**
     * 再来一单：将指定订单明细重新加入购物车。
     *
     * @param id      订单 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping("/repetition/{id}")
    public ApiResult<Object> repetition(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (id == null) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.repetition(userId, id);
            return ApiResult.success();
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
    }

    /**
     * 催单（60 秒内同一订单限一次）。
     *
     * @param orderId 订单 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @GetMapping("/reminder/{id}")
    public ApiResult<Object> reminder(@PathVariable("id") Long orderId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        ordersApplicationService.reminder(userId, orderId);
        return ApiResult.success();
    }

    private Long getUserId(HttpServletRequest request) {
        Object uidObj = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (uidObj instanceof Long userId) return userId;
        return null;
    }

    // DTO/VO/PageData 已外移至 dto/vo/common 包
}
