package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Order risk model metadata entity.
 *
 * @author Endercloud
 */
@Data
@TableName("order_risk_model_meta")
public class OrderRiskModelMeta implements Serializable {

    @TableId(value = "model_version", type = IdType.INPUT)
    private String modelVersion;

    @TableField("artifact_path")
    private String artifactPath;

    @TableField("metrics_json")
    private String metricsJson;

    @TableField("train_window_start")
    private Date trainWindowStart;

    @TableField("train_window_end")
    private Date trainWindowEnd;

    @TableField("created_at")
    private Date createdAt;

    @TableField("status")
    private String status;
}
