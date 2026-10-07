package com.codeying.service;

import com.codeying.vo.admin.report.OrdersStatisticsVO;
import com.codeying.vo.admin.report.ReportBusinessData;
import com.codeying.vo.admin.report.Top10VO;
import com.codeying.vo.admin.report.TurnoverStatisticsVO;
import com.codeying.vo.admin.report.UserStatisticsVO;

import java.time.LocalDate;
import java.util.Date;

/**
 * Report Application Service service interface.
 *
 * @author Endercloud
 */
public interface ReportApplicationService {
    /** Yesterday-ending 30-day export period and daily actuals; core query failures propagate. */
    com.codeying.vo.admin.report.ReportExportData prepareExport();

    /**
     * Execute computeBusinessData.
     *
     * @param begin begin parameter
     * @param end end parameter
     * @return ReportBusinessData result
     */
    ReportBusinessData computeBusinessData(Date begin, Date end);
    /**
     * Convert data structure.
     *
     * @param begin begin parameter
     * @param end end parameter
     * @return TurnoverStatisticsVO result
     */
    TurnoverStatisticsVO buildTurnoverStatistics(LocalDate begin, LocalDate end);
    /**
     * Convert data structure.
     *
     * @param begin begin parameter
     * @param end end parameter
     * @return UserStatisticsVO result
     */
    UserStatisticsVO buildUserStatistics(LocalDate begin, LocalDate end);
    /**
     * Convert data structure.
     *
     * @param begin begin parameter
     * @param end end parameter
     * @return OrdersStatisticsVO result
     */
    OrdersStatisticsVO buildOrdersStatistics(LocalDate begin, LocalDate end);
    /**
     * Convert data structure.
     *
     * @param begin begin parameter
     * @param end end parameter
     * @return Top10VO result
     */
    Top10VO buildTop10(LocalDate begin, LocalDate end);
}
