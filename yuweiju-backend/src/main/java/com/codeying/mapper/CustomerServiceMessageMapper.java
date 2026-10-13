package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.CustomerServiceMessage;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** Named customer message history queries; normalized limit is supplied by Service. */
@Mapper
public interface CustomerServiceMessageMapper extends BaseMapper<CustomerServiceMessage> {
    default List<CustomerServiceMessage> findHistory(Long sessionId, Long beforeId, int limit) {
        QueryWrapper<CustomerServiceMessage> query = new QueryWrapper<CustomerServiceMessage>().eq("session_id", sessionId);
        if (beforeId != null) query.lt("id", beforeId);
        return selectList(query.orderByDesc("id").last("limit " + limit));
    }

    default CustomerServiceMessage findLastMessage(Long sessionId) {
        return selectOne(new QueryWrapper<CustomerServiceMessage>().eq("session_id", sessionId)
                .orderByDesc("id").last("limit 1"));
    }
}
