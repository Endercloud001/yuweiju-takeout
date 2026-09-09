package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Order risk feature snapshot entity.
 *
 * @author Endercloud
 */
@Data
@TableName("order_risk_feature_snapshot")
public class OrderRiskFeatureSnapshot implements Serializable {

    /**
     * Composite PK part-1: order_id + feature_version.
     * Do not call xxById for this table.
     */
    @TableId(value = "order_id", type = IdType.INPUT)
    private Long orderId;

    @TableField("feature_version")
    private String featureVersion;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("created_at")
    private Date createdAt;
}
