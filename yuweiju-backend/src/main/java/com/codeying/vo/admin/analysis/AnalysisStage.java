package com.codeying.vo.admin.analysis;

import java.time.LocalDate;
import java.util.Map;

/** Synchronous stage outcome. Counts are committed SQL outputs, never trigger counts. */
public record AnalysisStage(String stage, String status, String reason, LocalDate windowStart,
                            LocalDate windowEnd, Map<String, Object> outputs,
                            @com.fasterxml.jackson.annotation.JsonIgnore Throwable failureCause) {
    public static AnalysisStage of(String stage, String status, String reason, LocalDate start,
                                   LocalDate end, Map<String, Object> outputs) {
        return new AnalysisStage(stage, status, reason, start, end, outputs, null);
    }
}
