package com.codeying.dto.admin.order;

import com.codeying.dto.common.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.time.ZoneId;

/**
 * Order Condition Query 
 *
 * @author Endercloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderConditionQuery extends PageQuery {
    /** number field. */
    @Size(max = 32, message = "订单号长度不能超过 32")
    private String number;
    /** Phone number. */
    @Size(max = 20, message = "电话长度不能超过 20")
    private String phone;
    /**  */
    private Integer status;
    /** risk level filter. */
    private String riskLevel;
    /** minimum risk score filter. */
    private Integer minRiskScore;
    /**  */
    private String beginTime;
    /**  */
    private String endTime;

    public Date beginDateTime() {
        return parseDate(beginTime, true);
    }

    public Date endDateTime() {
        return parseDate(endTime, false);
    }

    private Date parseDate(String value, boolean isBegin) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        DateTimeFormatter[] fmts = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        };
        for (DateTimeFormatter f : fmts) {
            try {
                LocalDateTime dt = LocalDateTime.parse(v, f);
                return Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception ignored) {}
        }
        try {
            LocalDate d = LocalDate.parse(v, DateTimeFormatter.ISO_DATE);
            LocalDateTime dt = isBegin ? d.atStartOfDay() : d.plusDays(1).atStartOfDay().minusSeconds(1);
            return Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {}
        return null;
    }
}
