package com.codeying.dto.admin.analysis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 分析历史回填请求。
 *
 * @author Endercloud
 */
public record AnalysisBackfillDTO(
        @NotNull(message = "结束日期不能为空")
        LocalDate endDate,
        @NotNull(message = "回填天数不能为空")
        @Min(value = 1, message = "回填天数必须大于 0")
        Integer days
) {}
