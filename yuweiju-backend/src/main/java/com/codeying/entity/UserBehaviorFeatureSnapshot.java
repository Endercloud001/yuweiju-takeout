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
 * 用户行为特征快照实体。
 *
 * @author Endercloud
 */
@Data
@TableName("user_behavior_feature_snapshot")
public class UserBehaviorFeatureSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 */
    private Long id;

    @TableField("snapshot_date")
    /** 快照日期 */
    private LocalDate snapshotDate;

    @TableField("user_id")
    /** 用户 ID */
    private Long userId;

    @TableField("rfm_recency_days")
    /** 最近一次下单距今天数 */
    private Integer rfmRecencyDays;

    @TableField("rfm_frequency_30d")
    /** 近 30 天下单次数 */
    private Integer rfmFrequency30d;

    @TableField("rfm_monetary_30d")
    /** 近 30 天消费金额 */
    private BigDecimal rfmMonetary30d;

    @TableField("order_lunch_ratio")
    /** 午餐时段下单占比 */
    private BigDecimal orderLunchRatio;

    @TableField("order_dinner_ratio")
    /** 晚餐时段下单占比 */
    private BigDecimal orderDinnerRatio;

    @TableField("order_night_ratio")
    /** 夜宵时段下单占比 */
    private BigDecimal orderNightRatio;

    @TableField("price_low_ratio")
    /** 低价带偏好占比 */
    private BigDecimal priceLowRatio;

    @TableField("price_mid_ratio")
    /** 中价带偏好占比 */
    private BigDecimal priceMidRatio;

    @TableField("price_high_ratio")
    /** 高价带偏好占比 */
    private BigDecimal priceHighRatio;

    @TableField("category_pref_top1")
    /** 偏好 Top1 品类 ID */
    private Long categoryPrefTop1;

    @TableField("category_pref_top2")
    /** 偏好 Top2 品类 ID */
    private Long categoryPrefTop2;

    @TableField("flavor_vector_json")
    /** 口味向量 JSON */
    private String flavorVectorJson;

    @TableField("feature_version")
    /** 特征版本号 */
    private String featureVersion;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;

    @TableField("update_time")
    /** 更新时间 */
    private Date updateTime;
}
