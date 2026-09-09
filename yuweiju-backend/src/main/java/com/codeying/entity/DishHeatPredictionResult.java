package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 菜品热度预测结果实体。
 *
 * @author Endercloud
 */
@Data
@TableName("dish_heat_prediction_result")
public class DishHeatPredictionResult implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 */
    private Long id;

    @TableField("window_start")
    /** 预测窗口开始日期 */
    private LocalDate windowStart;

    @TableField("window_end")
    /** 预测窗口结束日期 */
    private LocalDate windowEnd;

    @TableField("dish_id")
    /** 菜品 ID */
    private Long dishId;

    @TableField("predicted_sales_qty")
    /** 预测销量 */
    private BigDecimal predictedSalesQty;

    @TableField("heat_score")
    /** 热度得分 */
    private BigDecimal heatScore;

    @TableField("model_version")
    /** 模型版本号 */
    private String modelVersion;

    @TableField("feature_version")
    /** 特征版本号 */
    private String featureVersion;

    @TableField("explain_json")
    /** 解释信息 */
    private String explainJson;

    @TableField("status")
    /** 状态 */
    private Integer status;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;
}
