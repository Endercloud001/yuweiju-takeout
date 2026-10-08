package com.codeying.controller.admin;

import com.codeying.service.ReportApplicationService;
import com.codeying.vo.admin.report.ReportBusinessData;
import com.codeying.vo.admin.report.ReportExportData;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Issue19ReportExportTest {
    private final ReportApplicationService service = mock(ReportApplicationService.class);
    private final AdminReportController controller = new AdminReportController(service);

    @Test
    void workbookPreservesServicePeriodAmountsAndNumericDailyCells() throws Exception {
        var begin = LocalDate.of(2020, 2, 29);
        var data = new ReportBusinessData();
        data.setTurnover(new BigDecimal("10.00"));
        data.setValidOrderCount(3);
        data.setOrderCompletionRate(.75D);
        data.setUnitPrice(new BigDecimal("3.33"));
        data.setNewUsers(2);
        when(service.prepareExport()).thenReturn(new ReportExportData(begin, begin, data,
                List.of(new ReportExportData.DailyData(begin, data))));
        var response = new MockHttpServletResponse();

        controller.export(response);

        assertEquals(200, response.getStatus());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").endsWith("2020-02-29%E8%87%B32020-02-29.xlsx"));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("2020-02-29 至 2020-02-29", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(10D, sheet.getRow(3).getCell(2).getNumericCellValue());
            assertEquals(.75D, sheet.getRow(3).getCell(4).getNumericCellValue());
            assertEquals(2D, sheet.getRow(3).getCell(6).getNumericCellValue());
            assertEquals(3D, sheet.getRow(4).getCell(2).getNumericCellValue());
            assertEquals(3.33D, sheet.getRow(4).getCell(4).getNumericCellValue());
            assertEquals(begin.toString(), sheet.getRow(7).getCell(1).getStringCellValue());
            double[] values = {10D, 3D, .75D, 3.33D, 2D};
            for (int i = 0; i < values.length; i++) {
                var cell = sheet.getRow(7).getCell(2 + i);
                assertEquals(CellType.NUMERIC, cell.getCellType());
                assertEquals(values[i], cell.getNumericCellValue());
            }
        }
    }

    @Test
    void coreQueryFailureReturnsFriendlyHttpFailureWithoutWorkbook() throws Exception {
        when(service.prepareExport()).thenThrow(new DataAccessResourceFailureException("synthetic SQL failure"));
        var response = new MockHttpServletResponse();

        controller.export(response);

        assertEquals(500, response.getStatus());
        assertEquals("text/plain;charset=UTF-8", response.getContentType());
        assertNull(response.getHeader("Content-Disposition"));
        assertEquals("报表导出失败，请稍后再试", response.getContentAsString());
    }
}
