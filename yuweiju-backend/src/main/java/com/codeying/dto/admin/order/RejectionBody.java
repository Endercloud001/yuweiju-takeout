package com.codeying.dto.admin.order;

import lombok.Data;

/**
 * 管理端拒单请求体。
 *
 * @author Endercloud
 */
@Data
public class RejectionBody {
    /**  ID */
    private Long id;
    /** rejectionReason  */
    private String rejectionReason;
}

