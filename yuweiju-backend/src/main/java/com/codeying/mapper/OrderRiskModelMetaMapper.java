package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.OrderRiskModelMeta;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Order risk model metadata mapper.
 *
 * @author Endercloud
 */
@Mapper
public interface OrderRiskModelMetaMapper extends BaseMapper<OrderRiskModelMeta> {
    /** Recent model metadata, bounded by the Service. */
    @org.apache.ibatis.annotations.Select("SELECT * FROM order_risk_model_meta ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<OrderRiskModelMeta> findRecent(@org.apache.ibatis.annotations.Param("limit") int limit);
    /** Latest active model, never evidence of a new training run. */
    default OrderRiskModelMeta findActive() {
        return selectOne(new QueryWrapper<OrderRiskModelMeta>().eq("status","ACTIVE").orderByDesc("created_at").last("limit 1"));
    }
    /** Exact model version; value is bound by MyBatis. */
    default OrderRiskModelMeta findVersion(String version) {
        return selectOne(new QueryWrapper<OrderRiskModelMeta>().eq("model_version",version).last("limit 1"));
    }
    /** Archive active metadata only as part of publishing the new model. */
    default int archiveActive() {
        return update(null,new UpdateWrapper<OrderRiskModelMeta>().eq("status","ACTIVE").set("status","ARCHIVED"));
    }
}

