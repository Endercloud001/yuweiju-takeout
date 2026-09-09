package com.codeying.dto.admin.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Report Range Query 
 *
 * @author Endercloud
 */
@Data
public class ReportRangeQuery {
    /** begin field. */
    @NotBlank(message = "begin 不能为空")
    private String begin;
    /** end field. */
    @NotBlank(message = "end 不能为空")
    private String end;

    public LocalDate beginDate() {
        try {
            return LocalDate.parse(begin.trim(), DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    public LocalDate endDate() {
        try {
            return LocalDate.parse(end.trim(), DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}

