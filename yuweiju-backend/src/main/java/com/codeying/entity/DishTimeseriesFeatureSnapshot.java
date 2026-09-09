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
 * 菜品时序特征快照实体。
 *
 * @author Endercloud
 */
@Data
@TableName("dish_timeseries_feature_snapshot")
public class DishTimeseriesFeatureSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 */
    private Long id;

    @TableField("snapshot_date")
    /** 快照日期 */
    private LocalDate snapshotDate;

    @TableField("dish_id")
    /** 菜品 ID */
    private Long dishId;

    @TableField("category_id")
    /** 品类 ID */
    private Long categoryId;

    @TableField("sales_qty_1d")
    /** 1 日销量 */
    private Integer salesQty1d;

    @TableField("sales_qty_7d")
    /** 7 日销量 */
    private Integer salesQty7d;

    @TableField("sales_qty_30d")
    /** 30 日销量 */
    private Integer salesQty30d;

    @TableField("refund_qty_30d")
    /** 30 日退款数量 */
    private Integer refundQty30d;

    @TableField("decay_sales_30d")
    /** 30 日衰减销量 */
    private BigDecimal decaySales30d;

    @TableField("is_holiday")
    /** 是否节假日 */
    private Integer isHoliday;

    @TableField("is_promo")
    /** 是否促销日 */
    private Integer isPromo;

    @TableField("weather_code")
    /** 天气分类编码 */
    private String weatherCode;

    @TableField("price")
    /** 菜品价格 */
    private BigDecimal price;

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
