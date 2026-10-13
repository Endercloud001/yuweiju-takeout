package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.AiAssistantMessage;

/**
 * AI 助手消息 Mapper。
 *
 * @author Endercloud
 */
public interface AiAssistantMessageMapper extends BaseMapper<AiAssistantMessage> {
    /** Current-day message cursor, newest first; caller reverses the page for display. */
    default java.util.List<AiAssistantMessage> listTodayBefore(Long sessionId, Long beforeId, int limit,
            java.util.Date begin, java.util.Date end) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiAssistantMessage>()
                .eq("session_id", sessionId).ge("create_time", begin).lt("create_time", end)
                .lt(beforeId != null, "id", beforeId).orderByDesc("id").last("limit " + limit));
    }
    /** Latest persisted message for the administrator summary, or null. */
    default AiAssistantMessage findLast(Long sessionId) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiAssistantMessage>()
                .eq("session_id", sessionId).orderByDesc("id").last("limit 1"));
    }

}

