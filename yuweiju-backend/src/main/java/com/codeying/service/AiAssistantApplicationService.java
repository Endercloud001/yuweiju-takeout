package com.codeying.service;

import com.codeying.dto.common.ai_assistant.AiAssistantMessageSendDTO;
import com.codeying.vo.admin.ai_assistant.AiAssistantSessionSummaryVO;
import com.codeying.vo.common.ai_assistant.AiAssistantMessageVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.codeying.vo.user.ai_assistant.AiAssistantSessionVO;

import java.util.List;

/**
 * AI 助手应用服务。
 *
 * @author Endercloud
 */
public interface AiAssistantApplicationService {

    /**
     * 打开用户 AI 会话。
     *
     * @param userId 用户 ID
     * @return 会话信息
     */
    AiAssistantSessionVO openSession(Long userId);

    /**
     * 发送消息并获取 AI 回复。
     *
     * @param userId 用户 ID
     * @param body   请求体
     * @return 回复内容
     */
    AiAssistantSendReplyVO sendMessage(Long userId, AiAssistantMessageSendDTO body);

    /**
     * 查询会话消息列表。
     *
     * @param sessionId 会话 ID
     * @param beforeId  游标
     * @param limit     条数
     * @return 消息列表
     */
    List<AiAssistantMessageVO> listMessages(Long sessionId, Long beforeId, Integer limit);

    /**
     * 查询管理端进行中的 AI 会话。
     *
     * @return 会话摘要列表
     */
    List<AiAssistantSessionSummaryVO> listOpenSessions();
}

