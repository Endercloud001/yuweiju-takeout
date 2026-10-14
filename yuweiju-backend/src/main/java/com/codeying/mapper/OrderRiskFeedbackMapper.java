package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.OrderRiskFeedback;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Order risk feedback mapper.
 *
 * @author Endercloud
 */
@Mapper
public interface OrderRiskFeedbackMapper extends BaseMapper<OrderRiskFeedback> {
    /** Latest feedback first; ID breaks ties within database timestamp precision. */
    default java.util.List<OrderRiskFeedback> findForOrders(java.util.List<Long> ids) {
        return selectList(new QueryWrapper<OrderRiskFeedback>().in("order_id",ids).orderByDesc("created_at").orderByDesc("id"));
    }
}

