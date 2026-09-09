package com.codeying.vo.admin.analysis;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 菜品热度预测视图对象。
 *
 * @author Endercloud
 */
@Data
public class DishHeatPredictionVO {

    /** 菜品 ID */
    private Long dishId;

    /** 菜品名称 */
    private String dishName;

    /** 预测窗口开始日期 */
    private LocalDate windowStart;

    /** 预测窗口结束日期 */
    private LocalDate windowEnd;

    /** 预测销量 */
    private BigDecimal predictedSalesQty;

    /** 热度得分 */
    private BigDecimal heatScore;

    /** 模型版本 */
    private String modelVersion;

    /** 特征版本 */
    private String featureVersion;

    /** 解释信息 */
    private String explainJson;
}
