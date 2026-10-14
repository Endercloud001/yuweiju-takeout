package com.codeying.vo.admin.analysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One synchronous use case; legacy response fields remain alongside stage details. */
public class AnalysisRun {
    private final LocalDate date;
    private final List<AnalysisStage> stages = new ArrayList<>();
    private final Map<String, Object> legacy = new LinkedHashMap<>();
    private boolean executed = true;

    public AnalysisRun(LocalDate date) { this.date = date; }
    public void add(AnalysisStage stage) { stages.add(stage); }
    public List<AnalysisStage> stages() { return List.copyOf(stages); }
    public void executed(boolean value) { executed = value; }
    public boolean executed() { return executed; }
    public void put(String field, Object value) { legacy.put(field, value); }
    public String status() {
        if (stages.stream().allMatch(s -> "COMPLETE".equals(s.status()))) return "COMPLETE";
        if (stages.stream().anyMatch(s -> "COMPLETE".equals(s.status()) || "PARTIAL".equals(s.status()))) return "PARTIAL";
        if (stages.stream().anyMatch(s -> "FAILED".equals(s.status()))) return "FAILED";
        return "SKIPPED";
    }
    public Map<String, Object> response() {
        Map<String, Object> result = new LinkedHashMap<>(legacy);
        result.put("snapshotDate", date);
        result.put("executed", executed);
        result.put("status", status());
        result.put("executionMode", "SYNCHRONOUS");
        result.put("stages", stages());
        result.put("message", !executed ? "训练任务未执行，请检查阶段原因"
                : "COMPLETE".equals(status()) ? "重训任务完成，请另行核对效果指标" : "分析任务" + status() + "，请检查阶段原因");
        return result;
    }
}
