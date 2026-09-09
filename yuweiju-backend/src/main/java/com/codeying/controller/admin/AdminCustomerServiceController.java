package com.codeying.controller.admin;

import com.codeying.dto.admin.customer_service.CustomerServiceCloseBody;
import com.codeying.dto.common.customer_service.CustomerServiceMessageListQuery;
import com.codeying.result.ApiResult;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.vo.admin.customer_service.CustomerServiceSessionSummaryVO;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端人工客服接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/customerService")
public class AdminCustomerServiceController {

    private final CustomerServiceApplicationService customerServiceApplicationService;

    public AdminCustomerServiceController(CustomerServiceApplicationService customerServiceApplicationService) {
        this.customerServiceApplicationService = customerServiceApplicationService;
    }

    /**
     * 会话列表（进行中）。
     *
     * @return 会话列表
     */
    @GetMapping("/session/openList")
    public ApiResult<List<CustomerServiceSessionSummaryVO>> openList() {
        return ApiResult.successData(customerServiceApplicationService.listOpenSessions());
    }

    /**
     * 会话消息列表（游标分页）。
     *
     * @param query 查询参数
     * @return 消息列表
     */
    @GetMapping("/message/list")
    public ApiResult<List<CustomerServiceMessageVO>> messageList(@Valid CustomerServiceMessageListQuery query) {
        return ApiResult.successData(customerServiceApplicationService.listMessages(
                query.getSessionId(),
                query.getBeforeId(),
                query.getLimit()
        ));
    }

    /**
     * 关闭会话。
     *
     * @param body 请求体
     * @return 操作结果
     */
    @PostMapping("/session/close")
    public ApiResult<Object> close(@RequestBody @Valid CustomerServiceCloseBody body) {
        customerServiceApplicationService.closeSession(body.getSessionId(), body.getCloseReason());
        return ApiResult.success();
    }
}
