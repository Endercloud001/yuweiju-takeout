package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.Orders;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单 Mapper。
 *
 * @author Endercloud
 */
public interface OrdersMapper extends BaseMapper<Orders> {
    /** Finds a stored order under the authenticated owner, without recomputing fees. */
    default Orders findOwned(@Param("userId") Long userId, @Param("orderId") Long orderId) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Orders>()
                .eq("user_id", userId).eq("id", orderId).last("limit 1"));
    }
    /** Resolves the owned stored order for simulated payment. */
    default Orders findByNumberOwned(@Param("userId") Long userId, @Param("number") String number) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Orders>()
                .eq("user_id", userId).eq("number", number).last("limit 1"));
    }
    /** User history with original latest-first ordering and optional status. */
    default com.baomidou.mybatisplus.core.metadata.IPage<Orders> findHistoryPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Orders> page, @Param("userId") Long userId, @Param("status") Integer status) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Orders>()
                .eq("user_id", userId).eq(status != null, "status", status).orderByDesc("order_time").orderByDesc("id"));
    }

    /** Inclusive actual aggregation, SQL failures propagate to the use case. */
    com.codeying.vo.admin.report.OrderBusinessAggregate aggregateBusinessByOrderTimeRange(
            @Param("begin") Date begin, @Param("end") Date end, @Param("completed") int completed);

    /** All-history overview, without a date filter. */
    @Select("select count(*) from orders")
    Long countAllOrders();

    /** All-history count for the requested business status. */
    @Select("select count(*) from orders where status = #{status}")
    Long countByStatus(@Param("status") int status);


    /** Administrator filters with inclusive time bounds and latest-risk predicates.
     * SQL failures, including risk filtering failures, propagate to the caller.
     */
    com.baomidou.mybatisplus.core.metadata.IPage<Orders> selectAdminConditionPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Orders> page,
            @Param("query") com.codeying.dto.admin.order.OrderConditionQuery query,
            @Param("begin") Date begin, @Param("end") Date end,
            @Param("riskLevel") String riskLevel);


    /**
     * 统计指定时间范围内的订单数量。
     *
     * @param begin 开始时间
     * @param end   结束时间
     * @return 订单数量
     */
    @Select("select count(1) from orders where order_time >= #{begin} and order_time <= #{end}")
    Long countByOrderTimeRange(@Param("begin") Date begin, @Param("end") Date end);

    /**
     * 统计指定状态且在时间范围内的订单数量。
     *
     * @param status 订单状态
     * @param begin  开始时间
     * @param end    结束时间
     * @return 订单数量
     */
    @Select("select count(1) from orders where status = #{status} and order_time >= #{begin} and order_time <= #{end}")
    Long countByStatusAndOrderTimeRange(@Param("status") Integer status, @Param("begin") Date begin, @Param("end") Date end);

    /**
     * 统计指定状态且在时间范围内的营业额。
     *
     * @param status 订单状态
     * @param begin  开始时间
     * @param end    结束时间
     * @return 营业额（单位：元）
     */
    BigDecimal sumAmountByStatusAndOrderTimeRange(@Param("status") Integer status, @Param("begin") Date begin, @Param("end") Date end);
}
