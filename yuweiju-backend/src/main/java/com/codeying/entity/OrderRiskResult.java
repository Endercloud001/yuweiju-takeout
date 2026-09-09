package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Order risk scoring result entity.
 *
 * @author Endercloud
 */
@Data
@TableName("order_risk_result")
public class OrderRiskResult implements Serializable {

    /**
     * Composite PK part-1: order_id + model_version.
     * Do not call xxById for this table.
     */
    @TableId(value = "order_id", type = IdType.INPUT)
    private Long orderId;

    @TableField("model_version")
    private String modelVersion;

    @TableField("feature_version")
    private String featureVersion;

    @TableField("risk_score")
    private Integer riskScore;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("p_lr")
    private BigDecimal pLr;

    @TableField("p_rf")
    private BigDecimal pRf;

    @TableField("reason_json")
    private String reasonJson;

    @TableField("evaluated_at")
    private Date evaluatedAt;

    @TableField("decision_status")
    private String decisionStatus;
}
