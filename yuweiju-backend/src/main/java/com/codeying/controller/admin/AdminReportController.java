package com.codeying.controller.admin;

import com.codeying.result.ApiResult;
import com.codeying.dto.admin.report.ReportRangeQuery;
import com.codeying.service.ReportApplicationService;
import com.codeying.vo.admin.report.ReportBusinessData;
import com.codeying.vo.admin.report.OrdersStatisticsVO;
import com.codeying.vo.admin.report.Top10VO;
import com.codeying.vo.admin.report.TurnoverStatisticsVO;
import com.codeying.vo.admin.report.UserStatisticsVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 管理端数据报表接口（导出报表、营业额/用户/订单统计、销量 TOP10）。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/report")
public class AdminReportController {

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());
    private final ReportApplicationService reportApplicationService;

    public AdminReportController(ReportApplicationService reportApplicationService) {
        this.reportApplicationService = reportApplicationService;
    }

    /**
     * 导出运营数据报表（默认导出近 30 天，返回 Excel 文件流）。
     *
     * @param response HTTP 响应
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws java.io.IOException {
        try {
            var export = reportApplicationService.prepareExport();
            LocalDate begin = export.begin();
            LocalDate end = export.end();

            try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx")) {
                if (inputStream == null) throw new IllegalStateException("报表模板不存在");
                try (XSSFWorkbook excel = new XSSFWorkbook(inputStream)) {
                    XSSFSheet sheet = excel.getSheetAt(0);
                    if (sheet == null) throw new IllegalStateException("报表模板缺少 Sheet");

                    ReportBusinessData data30Days = export.summary();

                    getCell(sheet, 1, 1).setCellValue(begin + " 至 " + end);

                    getCell(sheet, 3, 2).setCellValue(data30Days.getTurnover().doubleValue());
                    getCell(sheet, 3, 4).setCellValue(data30Days.getOrderCompletionRate());
                    getCell(sheet, 3, 6).setCellValue(data30Days.getNewUsers());

                    getCell(sheet, 4, 2).setCellValue(data30Days.getValidOrderCount());
                    getCell(sheet, 4, 4).setCellValue(data30Days.getUnitPrice().doubleValue());

                    for (int i = 0; i < export.days().size(); i++) {
                        var day = export.days().get(i);
                        LocalDate date = day.date();
                        ReportBusinessData daily = day.data();

                        int r = 7 + i;
                        getCell(sheet, r, 1).setCellValue(date.toString());
                        getCell(sheet, r, 2).setCellValue(daily.getTurnover().doubleValue());
                        getCell(sheet, r, 3).setCellValue(daily.getValidOrderCount());
                        getCell(sheet, r, 4).setCellValue(daily.getOrderCompletionRate());
                        getCell(sheet, r, 5).setCellValue(daily.getUnitPrice().doubleValue());
                        getCell(sheet, r, 6).setCellValue(daily.getNewUsers());
                    }

                    String fileName = "运营数据报表~" + begin + "至" + end + ".xlsx";
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));

                    try (ServletOutputStream outputStream = response.getOutputStream()) {
                        excel.write(outputStream);
                        outputStream.flush();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("报表导出失败", e);
            if (response.isCommitted()) {
                throw new com.codeying.exception.BusinessException("报表导出失败，请稍后再试", e);
            }
            response.reset();
            response.setStatus(500);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("报表导出失败，请稍后再试");
            response.getWriter().flush();
        }
    }

    /**
     * 查询营业额统计。
     *
     * @param begin 开始日期（yyyy-MM-dd）
     * @param end   结束日期（yyyy-MM-dd）
     * @return 营业额统计
     */
    @GetMapping("/turnoverStatistics")
    public ApiResult<TurnoverStatisticsVO> turnoverStatistics(@Valid ReportRangeQuery query) {
        java.time.LocalDate b = query.beginDate();
        java.time.LocalDate e = query.endDate();
        if (b == null || e == null || b.isAfter(e)) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(reportApplicationService.buildTurnoverStatistics(b, e));
    }

    /**
     * 查询用户统计（新增用户数、累计用户数）。
     *
     * @param begin 开始日期（yyyy-MM-dd）
     * @param end   结束日期（yyyy-MM-dd）
     * @return 用户统计
     */
    @GetMapping("/userStatistics")
    public ApiResult<UserStatisticsVO> userStatistics(@Valid ReportRangeQuery query) {
        java.time.LocalDate b = query.beginDate();
        java.time.LocalDate e = query.endDate();
        if (b == null || e == null || b.isAfter(e)) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(reportApplicationService.buildUserStatistics(b, e));
    }

    /**
     * 查询订单统计（总订单数、有效订单数、完成率）。
     *
     * @param begin 开始日期（yyyy-MM-dd）
     * @param end   结束日期（yyyy-MM-dd）
     * @return 订单统计
     */
    @GetMapping("/ordersStatistics")
    public ApiResult<OrdersStatisticsVO> ordersStatistics(@Valid ReportRangeQuery query) {
        java.time.LocalDate b = query.beginDate();
        java.time.LocalDate e = query.endDate();
        if (b == null || e == null || b.isAfter(e)) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(reportApplicationService.buildOrdersStatistics(b, e));
    }

    /**
     * 查询销量 TOP10 商品。
     *
     * @param begin 开始日期（yyyy-MM-dd）
     * @param end   结束日期（yyyy-MM-dd）
     * @return TOP10 统计
     */
    @GetMapping("/top10")
    public ApiResult<Top10VO> top10(@Valid ReportRangeQuery query) {
        java.time.LocalDate b = query.beginDate();
        java.time.LocalDate e = query.endDate();
        if (b == null || e == null || b.isAfter(e)) return ApiResult.badRequest("参数错误");
        return ApiResult.successData(reportApplicationService.buildTop10(b, e));
    }

    // getBusinessData 下沉到 ReportApplicationService

    private Cell getCell(XSSFSheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        return cell;
    }

    // BusinessData 下沉到 ReportApplicationService

    // 所有 VO 抽取到 com.codeying.vo.admin.report 包
}
