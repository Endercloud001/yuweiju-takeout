package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Order risk review feedback entity.
 *
 * @author Endercloud
 */
@Data
@TableName("order_risk_feedback")
public class OrderRiskFeedback implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("decision")
    private String decision;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("reason")
    private String reason;

    @TableField("created_at")
    private Date createdAt;
}

