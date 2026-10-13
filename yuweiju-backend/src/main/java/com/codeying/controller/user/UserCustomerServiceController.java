package com.codeying.controller.user;

import com.codeying.dto.common.customer_service.CustomerServiceMessageListQuery;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import com.codeying.vo.user.customer_service.CustomerServiceSessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端人工客服接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/customerService")
public class UserCustomerServiceController {

    private final CustomerServiceApplicationService customerServiceApplicationService;

    public UserCustomerServiceController(
            CustomerServiceApplicationService customerServiceApplicationService
    ) {
        this.customerServiceApplicationService = customerServiceApplicationService;
    }

    /**
     * 打开/获取进行中会话。
     *
     * @param request HTTP 请求
     * @return 会话信息
     */
    @PostMapping("/session/open")
    public ApiResult<CustomerServiceSessionVO> openSession(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        CustomerServiceSessionVO session = customerServiceApplicationService.openSession(userId);
        return ApiResult.successData(session);
    }

    /**
     * 会话消息列表（游标分页）。
     *
     * @param query   查询参数
     * @param request HTTP 请求
     * @return 消息列表
     */
    @GetMapping("/message/list")
    public ApiResult<List<CustomerServiceMessageVO>> messageList(@Valid CustomerServiceMessageListQuery query, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        List<CustomerServiceMessageVO> list = customerServiceApplicationService.listUserMessages(
                userId,
                query.getSessionId(),
                query.getBeforeId(),
                query.getLimit()
        );
        return ApiResult.successData(list);
    }

    private Long getUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        return raw instanceof Long ? (Long) raw : null;
    }

}
