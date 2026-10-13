package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.AiAssistantSession;

/**
 * AI 助手会话 Mapper。
 *
 * @author Endercloud
 */
public interface AiAssistantSessionMapper extends BaseMapper<AiAssistantSession> {
    /** Latest open session created within the existing day bounds for this owner. */
    default AiAssistantSession findOpenToday(Long userId, java.util.Date begin, java.util.Date end) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiAssistantSession>()
                .eq("user_id", userId).eq("status", AiAssistantSession.STATUS_OPEN)
                .ge("create_time", begin).lt("create_time", end).orderByDesc("update_time").last("limit 1"));
    }
    /** Administrator active sessions, preserving update-time ordering and bounds. */
    default java.util.List<AiAssistantSession> listActiveToday(java.util.Date begin, java.util.Date end) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiAssistantSession>()
                .eq("status", AiAssistantSession.STATUS_OPEN).ge("update_time", begin).lt("update_time", end)
                .orderByDesc("update_time"));
    }
    /** Update reply state only for an owned open session; explicitly clears absent pending action/payload. */
    default int updateReplyState(Long id, Long userId, String intent, String action, java.util.Date now) {
        return update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiAssistantSession>()
                .eq("id", id).eq("user_id", userId).eq("status", AiAssistantSession.STATUS_OPEN)
                .set("last_intent", intent).set("pending_action", action).set("pending_payload", null)
                .set("update_time", now));
    }

}

