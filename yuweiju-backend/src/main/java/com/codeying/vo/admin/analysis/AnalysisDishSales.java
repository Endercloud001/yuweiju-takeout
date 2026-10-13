package com.codeying.vo.admin.analysis;

import lombok.Data;

/** Parameterized analysis sales aggregation; no algorithm decisions in SQL. */
@Data
public class AnalysisDishSales {
    private Long dishId;
    private Integer quantity;
}
