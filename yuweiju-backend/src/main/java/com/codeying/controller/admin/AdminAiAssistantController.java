package com.codeying.controller.admin;

import com.codeying.dto.common.ai_assistant.AiAssistantMessageListQuery;
import com.codeying.result.ApiResult;
import com.codeying.service.AiAssistantApplicationService;
import com.codeying.vo.admin.ai_assistant.AiAssistantSessionSummaryVO;
import com.codeying.vo.common.ai_assistant.AiAssistantMessageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 AI 助手会话查询接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/aiAssistant")
public class AdminAiAssistantController {

    private final AiAssistantApplicationService aiAssistantApplicationService;

    public AdminAiAssistantController(AiAssistantApplicationService aiAssistantApplicationService) {
        this.aiAssistantApplicationService = aiAssistantApplicationService;
    }

    /**
     * 获取进行中的 AI 会话列表。
     *
     * @return 会话摘要列表
     */
    @GetMapping("/session/openList")
    public ApiResult<List<AiAssistantSessionSummaryVO>> openList() {
        return ApiResult.successData(aiAssistantApplicationService.listOpenSessions());
    }

    /**
     * 查询 AI 会话消息列表。
     *
     * @param query 查询参数
     * @return 消息列表
     */
    @GetMapping("/message/list")
    public ApiResult<List<AiAssistantMessageVO>> messageList(@Valid AiAssistantMessageListQuery query) {
        return ApiResult.successData(aiAssistantApplicationService.listMessages(
                query.getSessionId(),
                query.getBeforeId(),
                query.getLimit()
        ));
    }
}

