package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.UserClusterResult;

/**
 * 用户聚类结果 Mapper。
 *
 * @author Endercloud
 */
public interface UserClusterResultMapper extends BaseMapper<UserClusterResult> {
    /** Most recent persisted user cluster used by recommendation cache recovery, or null. */
    default UserClusterResult findLatestForUser(Long userId) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserClusterResult>()
                .eq("user_id", userId).orderByDesc("snapshot_date").orderByDesc("id").last("limit 1"));
    }

    /** Existing daily cluster ordering and bounded page. */
    default java.util.List<UserClusterResult> listDaily(java.time.LocalDate date, int limit) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserClusterResult>()
                .eq("snapshot_date", date).orderByAsc("cluster_id").orderByAsc("user_id").last("limit " + limit));
    }
    /** Rebuild one selected cluster day. */
    default int deleteDaily(java.time.LocalDate date) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserClusterResult>().eq("snapshot_date", date));
    }
    default long countDaily(java.time.LocalDate date) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserClusterResult>().eq("snapshot_date", date));
    }
}
