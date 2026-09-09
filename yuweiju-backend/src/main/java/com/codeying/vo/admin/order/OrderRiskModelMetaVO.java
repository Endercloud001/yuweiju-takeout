package com.codeying.vo.admin.order;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * Order risk model metadata view object.
 *
 * @author Endercloud
 */
@Data
public class OrderRiskModelMetaVO {

    /** Model version. */
    private String modelVersion;

    /** Artifact path. */
    private String artifactPath;

    /** Model status. */
    private String status;

    /** Training window start. */
    private Date trainWindowStart;

    /** Training window end. */
    private Date trainWindowEnd;

    /** Created time. */
    private Date createdAt;

    /** Offline metrics parsed from metrics_json. */
    private Map<String, Object> metrics;
}
