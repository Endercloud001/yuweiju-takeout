package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.UserBehaviorFeatureSnapshot;

/**
 * 用户行为特征快照 Mapper。
 *
 * @author Endercloud
 */
public interface UserBehaviorFeatureSnapshotMapper extends BaseMapper<UserBehaviorFeatureSnapshot> {

    /** Feature-version-specific daily input, stable entity order. */
    default java.util.List<UserBehaviorFeatureSnapshot> findDaily(java.time.LocalDate date, String version) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserBehaviorFeatureSnapshot>()
                .eq("snapshot_date", date).eq("feature_version", version).orderByAsc("user_id"));
    }
    /** Rebuild only the selected daily feature version. */
    default int deleteDaily(java.time.LocalDate date, String version) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserBehaviorFeatureSnapshot>()
                .eq("snapshot_date", date).eq("feature_version", version));
    }
    /** Actual persisted daily output count. */
    default long countDaily(java.time.LocalDate date, String version) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserBehaviorFeatureSnapshot>()
                .eq("snapshot_date", date).eq("feature_version", version));
    }
}
