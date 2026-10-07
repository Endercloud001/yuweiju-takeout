package com.codeying.vo.admin.report;

import java.time.LocalDate;
import java.util.List;

/** Internal export data; contains no HTTP or workbook concerns. */
public record ReportExportData(LocalDate begin, LocalDate end, ReportBusinessData summary,
                               List<DailyData> days) {
    public record DailyData(LocalDate date, ReportBusinessData data) {}
}
