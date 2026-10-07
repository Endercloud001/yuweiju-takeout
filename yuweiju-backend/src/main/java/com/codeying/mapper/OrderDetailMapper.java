package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.vo.report.GoodsSales;
import com.codeying.entity.OrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 订单明细 Mapper。
 *
 * @author Endercloud
 */
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    /**
     * 查询指定时间范围内销量 TOP10 商品。
     *
     * @param begin  开始时间
     * @param end    结束时间
     * @param status 订单状态（通常为已完成）
     * @return 商品销量列表
     */
    List<GoodsSales> top10(@Param("begin") Date begin, @Param("end") Date end, @Param("status") Integer status);
}
