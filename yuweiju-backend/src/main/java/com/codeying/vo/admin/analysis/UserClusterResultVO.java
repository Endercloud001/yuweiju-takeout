package com.codeying.vo.admin.analysis;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户聚类结果视图对象。
 *
 * @author Endercloud
 */
@Data
public class UserClusterResultVO {

    /** 用户 ID */
    private Long userId;

    /** 用户名称 */
    private String userName;

    /** 聚类日期 */
    private LocalDate snapshotDate;

    /** 簇 ID */
    private Integer clusterId;

    /** 归属置信度 */
    private BigDecimal clusterScore;

    /** 模型版本 */
    private String modelVersion;

    /** 特征版本 */
    private String featureVersion;

    /** 候选菜品 ID 列表 */
    private List<Long> topDishIds;
}
