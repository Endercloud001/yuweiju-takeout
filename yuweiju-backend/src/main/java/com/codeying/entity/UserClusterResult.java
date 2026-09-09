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
 * 用户聚类结果实体。
 *
 * @author Endercloud
 */
@Data
@TableName("user_cluster_result")
public class UserClusterResult implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 */
    private Long id;

    @TableField("snapshot_date")
    /** 聚类快照日期 */
    private LocalDate snapshotDate;

    @TableField("user_id")
    /** 用户 ID */
    private Long userId;

    @TableField("cluster_id")
    /** 簇 ID */
    private Integer clusterId;

    @TableField("cluster_score")
    /** 簇归属置信度 */
    private BigDecimal clusterScore;

    @TableField("model_version")
    /** 模型版本号 */
    private String modelVersion;

    @TableField("feature_version")
    /** 特征版本号 */
    private String featureVersion;

    @TableField("topn_dish_json")
    /** 候选菜品 JSON */
    private String topnDishJson;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;
}
