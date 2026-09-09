package com.codeying.dto.admin.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Admin risk feedback payload.
 *
 * @author Endercloud
 */
@Data
public class OrderRiskFeedbackDTO {

    /** Order ID. */
    @NotNull(message = "orderId cannot be null")
    private Long orderId;

    /** Review decision: approve/reject/review. */
    @NotBlank(message = "decision cannot be blank")
    private String decision;

    /** Optional review reason. */
    private String reason;
}
