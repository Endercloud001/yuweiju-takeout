package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.OrderRiskFeatureSnapshot;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Order risk feature snapshot mapper.
 *
 * @author Endercloud
 */
@Mapper
public interface OrderRiskFeatureSnapshotMapper extends BaseMapper<OrderRiskFeatureSnapshot> {
    /** Snapshot lookup with bound feature version and order IDs. */
    default java.util.List<OrderRiskFeatureSnapshot> findForOrders(java.util.List<Long> ids, String version) {
        return selectList(new QueryWrapper<OrderRiskFeatureSnapshot>().eq("feature_version",version).in("order_id",ids));
    }
    /** Replaces only this order/feature snapshot inside the Service transaction. */
    default int deleteVersion(Long id, String version) {
        return delete(new QueryWrapper<OrderRiskFeatureSnapshot>().eq("order_id",id).eq("feature_version",version));
    }
}

