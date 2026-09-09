package com.codeying.controller.admin;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.order.OrderConditionQuery;
import com.codeying.dto.admin.order.CancelBody;
import com.codeying.dto.admin.order.IdBody;
import com.codeying.dto.admin.order.RejectionBody;
import com.codeying.result.ApiResult;
import com.codeying.vo.admin.order.OrderStatisticsVO;
import com.codeying.vo.admin.order.OrderVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * 管理端订单管理接口（查询、接单、拒单、取消、派送、完成、统计）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    private final com.codeying.service.OrdersApplicationService ordersApplicationService;

    public AdminOrderController(com.codeying.service.OrdersApplicationService ordersApplicationService) {
        this.ordersApplicationService = ordersApplicationService;
    }

    /**
     * 订单条件分页查询。
     *
     * @param page      页码
     * @param pageSize  每页大小
     * @param number    订单号关键字
     * @param phone     联系电话关键字
     * @param status    订单状态
     * @param beginTime 开始时间
     * @param endTime   结束时间
     * @return 分页结果
     */
    @GetMapping("/conditionSearch")
    public ApiResult<PageData<OrderVO>> conditionSearch(@Valid OrderConditionQuery query) {
        return ApiResult.successData(ordersApplicationService.adminConditionSearch(query));
    }

    /**
     * 查询订单详情（包含明细）。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @GetMapping("/details/{id}")
    public ApiResult<OrderVO> details(@PathVariable("id") Long id) {
        if (id == null) return ApiResult.badRequest("参数错误");
        try {
            return ApiResult.successData(ordersApplicationService.adminOrderDetail(id));
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
    }

    /**
     * 接单（将订单状态从待接单改为已接单）。
     *
     * @param body 请求体（包含订单 ID）
     * @return 操作结果
     */
    @PutMapping("/confirm")
    public ApiResult<Object> confirm(@RequestBody IdBody body) {
        if (body == null || body.getId() == null) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.confirm(body.getId());
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
        return ApiResult.success();
    }

    /**
     * 拒单（将订单取消并记录拒单原因）。
     *
     * @param body 请求体（包含订单 ID 与拒单原因）
     * @return 操作结果
     */
    @PutMapping("/rejection")
    public ApiResult<Object> rejection(@RequestBody RejectionBody body) {
        if (body == null || body.getId() == null || !StringUtils.hasText(body.getRejectionReason())) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.reject(body.getId(), body.getRejectionReason().trim());
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
        return ApiResult.success();
    }

    /**
     * 取消订单（管理端）。
     *
     * @param body 请求体（包含订单 ID 与取消原因）
     * @return 操作结果
     */
    @PutMapping("/cancel")
    public ApiResult<Object> cancel(@RequestBody CancelBody body) {
        if (body == null || body.getId() == null || !StringUtils.hasText(body.getCancelReason())) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.cancelByAdmin(body.getId(), body.getCancelReason().trim());
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
        return ApiResult.success();
    }

    /**
     * 派送订单（将订单状态改为派送中）。
     *
     * @param id 订单 ID
     * @return 操作结果
     */
    @PutMapping("/delivery/{id}")
    public ApiResult<Object> delivery(@PathVariable("id") Long id) {
        if (id == null) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.deliver(id);
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
        return ApiResult.success();
    }

    /**
     * 完成订单（将订单状态改为已完成，并记录送达时间）。
     *
     * @param id 订单 ID
     * @return 操作结果
     */
    @PutMapping("/complete/{id}")
    public ApiResult<Object> complete(@PathVariable("id") Long id) {
        if (id == null) return ApiResult.badRequest("参数错误");
        try {
            ordersApplicationService.complete(id);
        } catch (Exception e) {
            return ApiResult.badRequest(e.getMessage());
        }
        return ApiResult.success();
    }

    /**
     * 查询订单数量统计（待接单、已接单、派送中）。
     *
     * @return 统计数据
     */
    @GetMapping("/statistics")
    public ApiResult<OrderStatisticsVO> statistics() {
        return ApiResult.successData(ordersApplicationService.adminStatistics());
    }

    // VO 装配与状态流转已下沉至 OrdersApplicationService

    // 日期解析已由 OrderConditionQuery 提供
}
