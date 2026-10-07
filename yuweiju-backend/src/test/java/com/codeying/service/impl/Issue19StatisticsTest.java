package com.codeying.service.impl;

import com.codeying.entity.Orders;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.*;
import com.codeying.vo.admin.report.OrderBusinessAggregate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Issue19StatisticsTest {
    private final OrdersMapper orders = mock(OrdersMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final OrderDetailMapper details = mock(OrderDetailMapper.class);
    private final ReportApplicationServiceImpl report = new ReportApplicationServiceImpl(users, orders, details);
    private final WorkspaceApplicationServiceImpl workspace = new WorkspaceApplicationServiceImpl(
            orders, users, mock(DishMapper.class), mock(SetmealMapper.class));

    private void actual(long total, long completed, String amount) {
        var data = new OrderBusinessAggregate();
        data.setTotalOrders(total); data.setValidOrders(completed); data.setTurnover(new BigDecimal(amount));
        when(orders.aggregateBusinessByOrderTimeRange(any(), any(), eq(Orders.COMPLETED))).thenReturn(data);
        when(users.countCreatedInRange(any(), any())).thenReturn(2L);
    }

    @Test void retainsDifferentPricePrecisionAndSameActualAmount() {
        actual(4, 3, "10.00");
        var r = report.computeBusinessData(new Date(0), new Date(1));
        var w = workspace.businessData();
        assertEquals(new BigDecimal("3.33"), r.getUnitPrice());
        assertEquals(10D / 3D, w.getUnitPrice());
        assertNotEquals(r.getUnitPrice().doubleValue(), w.getUnitPrice());
        assertEquals(10D, w.getTurnover()); assertEquals(new BigDecimal("10.00"), r.getTurnover());
        assertEquals(.75D, r.getOrderCompletionRate()); assertEquals(.75D, w.getOrderCompletionRate());
    }

    @Test void emptyStatisticsHaveZeroAmountCountsAndRates() {
        actual(0, 0, "0"); when(users.countCreatedInRange(any(), any())).thenReturn(0L);
        var r = report.computeBusinessData(new Date(0), new Date(1));
        var w = workspace.businessData();
        assertEquals(BigDecimal.ZERO, r.getUnitPrice()); assertEquals(0D, r.getOrderCompletionRate());
        assertEquals(0D, w.getUnitPrice()); assertEquals(0, w.getValidOrderCount()); assertEquals(0, r.getNewUsers());
        when(orders.countByOrderTimeRange(any(), any())).thenReturn(0L);
        when(orders.countByStatusAndOrderTimeRange(anyInt(), any(), any())).thenReturn(0L);
        when(orders.sumAmountByStatusAndOrderTimeRange(anyInt(), any(), any())).thenReturn(BigDecimal.ZERO);
        var date = LocalDate.of(2026, 2, 1);
        assertEquals("0", report.buildOrdersStatistics(date, date).getOrderCountList());
        assertEquals("0.00", report.buildTurnoverStatistics(date, date).getTurnoverList());
        assertEquals("", report.buildTop10(date, date).getNameList());
    }

    @Test void inclusiveDayBoundariesAndCumulativeUsersStayInSystemTimezone() {
        var date = LocalDate.of(2026, 2, 1);
        when(users.countCreatedInRange(any(), any())).thenReturn(1L);
        when(users.countCreatedThrough(any())).thenReturn(7L);
        var vo = report.buildUserStatistics(date, date);
        assertEquals("1", vo.getNewUserList()); assertEquals("7", vo.getTotalUserList());
        var begin = ArgumentCaptor.forClass(Date.class); var end = ArgumentCaptor.forClass(Date.class);
        verify(users).countCreatedInRange(begin.capture(), end.capture());
        assertEquals(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()), begin.getValue());
        assertEquals(Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)), end.getValue());
    }

    @Test void exportServiceOwnsThirtyDailyRowsAndSummary() {
        actual(1, 1, "12.34");
        var export = report.prepareExport();
        assertEquals(LocalDate.now().minusDays(1), export.end());
        assertEquals(export.end().minusDays(29), export.begin());
        assertEquals(30, export.days().size()); assertEquals(export.begin(), export.days().get(0).date());
        assertEquals(export.end(), export.days().get(29).date());
        assertEquals(new BigDecimal("12.34"), export.summary().getTurnover());
    }

    @Test void invalidDirectServiceRangesDoNotQuery() {
        var date = LocalDate.of(2026, 2, 1);
        assertThrows(BusinessException.class, () -> report.buildOrdersStatistics(date, date.minusDays(1)));
        assertThrows(BusinessException.class, () -> report.buildTurnoverStatistics(null, date));
        assertThrows(BusinessException.class, () -> report.buildUserStatistics(date, LocalDate.MAX));
        assertThrows(BusinessException.class, () -> report.buildTop10(date, date.minusDays(1)));
        assertThrows(BusinessException.class, () -> report.computeBusinessData(new Date(2), new Date(1)));
        verifyNoInteractions(orders, users, details);
    }

    @Test void coreSqlFailuresPropagateInsteadOfBecomingZeroMetrics() {
        when(users.countCreatedInRange(any(), any())).thenReturn(0L);
        var failure = new DataAccessResourceFailureException("synthetic SQL unavailable");
        when(orders.aggregateBusinessByOrderTimeRange(any(), any(), anyInt())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> report.computeBusinessData(new Date(0), new Date(1))));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, workspace::businessData));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, report::prepareExport));
    }

    @Test void orderOverviewRetainsAllHistoryAndConfirmedMeaning() {
        when(orders.countAllOrders()).thenReturn(20L);
        when(orders.countByStatus(Orders.COMPLETED)).thenReturn(5L);
        when(orders.countByStatus(Orders.CONFIRMED)).thenReturn(3L);
        when(orders.countByStatus(Orders.CANCELLED)).thenReturn(2L);
        when(orders.countByStatus(Orders.TO_BE_CONFIRMED)).thenReturn(1L);
        var vo = workspace.overviewOrders();
        assertEquals(20, vo.getAllOrders()); assertEquals(3, vo.getDeliveredOrders());
        assertEquals(5, vo.getCompletedOrders()); assertEquals(2, vo.getCancelledOrders());
        assertEquals(1, vo.getWaitingOrders());
    }
}
