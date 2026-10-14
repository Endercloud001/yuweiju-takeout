package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.OrderRiskResult;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Order risk result mapper.
 *
 * @author Endercloud
 */
@Mapper
public interface OrderRiskResultMapper extends BaseMapper<OrderRiskResult> {
    /** Latest-first risk rows for the requested orders. */
    default java.util.List<OrderRiskResult> findLatestForOrders(java.util.Collection<Long> ids) {
        return selectList(new QueryWrapper<OrderRiskResult>().in("order_id", ids).orderByDesc("evaluated_at").orderByDesc("model_version"));
    }
    /** Existing score IDs for explicit backfill. */
    default java.util.List<OrderRiskResult> findScored(java.util.List<Long> ids) {
        return selectList(new QueryWrapper<OrderRiskResult>().in("order_id", ids).select("order_id"));
    }
    /** Latest decision target, preserving evaluation-time ordering. */
    default OrderRiskResult findLatest(Long id) {
        return selectOne(new QueryWrapper<OrderRiskResult>().eq("order_id",id).orderByDesc("evaluated_at").last("limit 1"));
    }
    /** Applies feedback only to the selected order/version. */
    default int updateDecision(Long id, String version, String decision) {
        return update(null,new UpdateWrapper<OrderRiskResult>().eq("order_id",id).eq("model_version",version).set("decision_status",decision));
    }
    /** Replaces only this order/model score inside the Service transaction. */
    default int deleteVersion(Long id, String version) {
        return delete(new QueryWrapper<OrderRiskResult>().eq("order_id",id).eq("model_version",version));
    }
}

