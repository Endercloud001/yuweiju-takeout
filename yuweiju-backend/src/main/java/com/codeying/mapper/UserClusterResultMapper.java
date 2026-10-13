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

}
