package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Order risk standardizer metadata entity.
 *
 * @author Endercloud
 */
@Data
@TableName("order_risk_standardizer_meta")
public class OrderRiskStandardizerMeta implements Serializable {

    /**
     * Composite PK part-1: feature_version + feature_name.
     * Do not call xxById for this table.
     */
    @TableId(value = "feature_version", type = IdType.INPUT)
    private String featureVersion;

    @TableField("feature_name")
    private String featureName;

    @TableField("mean_value")
    private Double meanValue;

    @TableField("std_value")
    private Double stdValue;

    @TableField("created_at")
    private Date createdAt;
}
