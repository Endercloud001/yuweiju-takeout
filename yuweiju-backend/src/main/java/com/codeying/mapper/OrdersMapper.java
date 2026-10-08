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
