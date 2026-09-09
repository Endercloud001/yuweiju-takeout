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
    @Select("select ifnull(sum(amount),0) from orders where status = #{status} and order_time >= #{begin} and order_time <= #{end}")
    BigDecimal sumAmountByStatusAndOrderTimeRange(@Param("status") Integer status, @Param("begin") Date begin, @Param("end") Date end);
}
