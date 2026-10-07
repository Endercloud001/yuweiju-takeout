package com.codeying.vo.admin.report;

import lombok.Data;
import java.math.BigDecimal;

/** Internal actual order aggregation; caller retains its own price precision. */
@Data
public class OrderBusinessAggregate {
    private long totalOrders;
    private long validOrders;
    private BigDecimal turnover;
}
