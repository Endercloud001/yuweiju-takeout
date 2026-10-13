package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.codeying.entity.CustomerServiceSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

/** Named customer session queries and conditional writes. */
@Mapper
public interface CustomerServiceSessionMapper extends BaseMapper<CustomerServiceSession> {
    default CustomerServiceSession findLatestOpenByUser(Long userId) {
        return selectOne(new QueryWrapper<CustomerServiceSession>().eq("user_id", userId)
                .eq("status", CustomerServiceSession.STATUS_OPEN).orderByDesc("update_time").last("limit 1"));
    }

    default List<CustomerServiceSession> findOpenSessions() {
        return selectList(new QueryWrapper<CustomerServiceSession>().eq("status", CustomerServiceSession.STATUS_OPEN)
                .orderByDesc("update_time"));
    }

    /** Serialize message writes and close on this existing row, without changing schema. */
    @Select("SELECT * FROM customer_service_session WHERE id = #{id} FOR UPDATE")
    CustomerServiceSession findByIdForUpdate(@Param("id") Long id);

    default int touchOpenSession(Long id, Date now) {
        return update(null, new UpdateWrapper<CustomerServiceSession>().eq("id", id)
                .eq("status", CustomerServiceSession.STATUS_OPEN).set("update_time", now));
    }

    default int closeOpenSession(Long id, Date now, String reason) {
        UpdateWrapper<CustomerServiceSession> update = new UpdateWrapper<CustomerServiceSession>().eq("id", id)
                .eq("status", CustomerServiceSession.STATUS_OPEN).set("status", CustomerServiceSession.STATUS_CLOSED)
                .set("close_time", now).set("update_time", now);
        if (reason != null) update.set("close_reason", reason);
        return update(null, update);
    }
}
