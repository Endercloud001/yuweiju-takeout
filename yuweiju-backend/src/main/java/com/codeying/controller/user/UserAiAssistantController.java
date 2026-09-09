package com.codeying.controller.user;

import com.codeying.dto.common.ai_assistant.AiAssistantMessageListQuery;
import com.codeying.dto.common.ai_assistant.AiAssistantMessageSendDTO;
import com.codeying.entity.AiAssistantSession;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.service.AiAssistantApplicationService;
import com.codeying.service.AiAssistantSessionService;
import com.codeying.vo.common.ai_assistant.AiAssistantMessageVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.codeying.vo.user.ai_assistant.AiAssistantSessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 AI 助手接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/aiAssistant")
public class UserAiAssistantController {

    private final AiAssistantApplicationService aiAssistantApplicationService;
    private final AiAssistantSessionService aiAssistantSessionService;

    public UserAiAssistantController(
            AiAssistantApplicationService aiAssistantApplicationService,
            AiAssistantSessionService aiAssistantSessionService
    ) {
        this.aiAssistantApplicationService = aiAssistantApplicationService;
        this.aiAssistantSessionService = aiAssistantSessionService;
    }

    /**
     * 打开/获取 AI 会话。
     *
     * @param request HTTP 请求
     * @return 会话信息
     */
    @PostMapping("/session/open")
    public ApiResult<AiAssistantSessionVO> openSession(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ApiResult.unauthorized("未登录");
        }
        return ApiResult.successData(aiAssistantApplicationService.openSession(userId));
    }

    /**
     * 发送消息并返回 AI 回复。
     *
     * @param body    消息请求
     * @param request HTTP 请求
     * @return AI 回复
     */
    @PostMapping("/message/send")
    public ApiResult<AiAssistantSendReplyVO> sendMessage(
            @RequestBody @Valid AiAssistantMessageSendDTO body,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ApiResult.unauthorized("未登录");
        }
        if (!isUserSession(userId, body.getSessionId())) {
            return ApiResult.forbidden("无权限");
        }
        return ApiResult.successData(aiAssistantApplicationService.sendMessage(userId, body));
    }

    /**
     * 查询消息列表。
     *
     * @param query   查询参数
     * @param request HTTP 请求
     * @return 消息列表
     */
    @GetMapping("/message/list")
    public ApiResult<List<AiAssistantMessageVO>> messageList(
            @Valid AiAssistantMessageListQuery query,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ApiResult.unauthorized("未登录");
        }
        if (!isUserSession(userId, query.getSessionId())) {
            return ApiResult.forbidden("无权限");
        }
        List<AiAssistantMessageVO> list = aiAssistantApplicationService.listMessages(
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

    private boolean isUserSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }
        AiAssistantSession session = aiAssistantSessionService.getById(sessionId);
        if (session == null) {
            return false;
        }
        return userId.equals(session.getUserId());
    }
}

