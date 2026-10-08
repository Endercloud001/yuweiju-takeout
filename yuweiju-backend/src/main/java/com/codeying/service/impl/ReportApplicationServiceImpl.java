package com.codeying.service.impl;

import com.codeying.entity.Orders;
import com.codeying.mapper.OrderDetailMapper;
import com.codeying.mapper.OrdersMapper;
import com.codeying.mapper.UserMapper;
import com.codeying.exception.BusinessException;
import com.codeying.vo.admin.report.ReportExportData;
import com.codeying.service.ReportApplicationService;
import com.codeying.vo.admin.report.OrdersStatisticsVO;
import com.codeying.vo.admin.report.ReportBusinessData;
import com.codeying.vo.admin.report.Top10VO;
import com.codeying.vo.admin.report.TurnoverStatisticsVO;
import com.codeying.vo.admin.report.UserStatisticsVO;
import com.codeying.vo.report.GoodsSales;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

/**
 * Report Application Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
public class ReportApplicationServiceImpl implements ReportApplicationService {
    private final UserMapper userMapper;
    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;

    public ReportApplicationServiceImpl(UserMapper userMapper, OrdersMapper ordersMapper, OrderDetailMapper orderDetailMapper) {
        this.userMapper = userMapper;
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
    }

    @Override
    public ReportExportData prepareExport() {
        LocalDate today = LocalDate.now();
        LocalDate begin = today.minusDays(30);
        LocalDate end = today.minusDays(1);
        ReportBusinessData summary = computeBusinessData(dayBegin(begin), dayEnd(end));
        java.util.ArrayList<ReportExportData.DailyData> days = new java.util.ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            days.add(new ReportExportData.DailyData(date, computeBusinessData(dayBegin(date), dayEnd(date))));
        }
        return new ReportExportData(begin, end, summary, List.copyOf(days));
    }

    @Override
    public ReportBusinessData computeBusinessData(Date begin, Date end) {
        if (begin == null || end == null || begin.after(end)) throw new BusinessException("参数错误");
        int newUsers = userMapper.countCreatedInRange(begin, end).intValue();
        var actual = ordersMapper.aggregateBusinessByOrderTimeRange(begin, end, Orders.COMPLETED);
        long totalOrders = actual.getTotalOrders();
        long validOrders = actual.getValidOrders();
        BigDecimal turnover = actual.getTurnover();

        double completionRate = totalOrders == 0 ? 0D : (double) validOrders / (double) totalOrders;
        BigDecimal unitPrice = validOrders == 0 ? BigDecimal.ZERO : turnover.divide(new BigDecimal(validOrders), 2, java.math.RoundingMode.HALF_UP);

        ReportBusinessData data = new ReportBusinessData();
        data.setTurnover(turnover);
        data.setOrderCompletionRate(completionRate);
        data.setNewUsers(newUsers);
        data.setValidOrderCount((int) validOrders);
        data.setUnitPrice(unitPrice);
        return data;
    }

    @Override
    public TurnoverStatisticsVO buildTurnoverStatistics(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        DecimalFormat df = new DecimalFormat("0.00");
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner turnoverList = new StringJoiner(",");
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = dayBegin(cur);
            Date dayEnd = dayEnd(cur);
            BigDecimal turnover = ordersMapper.sumAmountByStatusAndOrderTimeRange(Orders.COMPLETED, dayBegin, dayEnd);
            turnoverList.add(df.format(turnover == null ? BigDecimal.ZERO : turnover));
            cur = cur.plusDays(1);
        }
        TurnoverStatisticsVO vo = new TurnoverStatisticsVO();
        vo.setDateList(dateList.toString());
        vo.setTurnoverList(turnoverList.toString());
        return vo;
    }

    @Override
    public UserStatisticsVO buildUserStatistics(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner newUserList = new StringJoiner(",");
        StringJoiner totalUserList = new StringJoiner(",");
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = dayBegin(cur);
            Date dayEnd = dayEnd(cur);
            int newUsers = userMapper.countCreatedInRange(dayBegin, dayEnd).intValue();
            int totalUsers = userMapper.countCreatedThrough(dayEnd).intValue();
            newUserList.add(String.valueOf(newUsers));
            totalUserList.add(String.valueOf(totalUsers));
            cur = cur.plusDays(1);
        }
        UserStatisticsVO vo = new UserStatisticsVO();
        vo.setDateList(dateList.toString());
        vo.setNewUserList(newUserList.toString());
        vo.setTotalUserList(totalUserList.toString());
        return vo;
    }

    @Override
    public OrdersStatisticsVO buildOrdersStatistics(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner orderCountList = new StringJoiner(",");
        StringJoiner validOrderCountList = new StringJoiner(",");
        int totalOrderCount = 0;
        int validOrderCount = 0;
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = dayBegin(cur);
            Date dayEnd = dayEnd(cur);
            int dayTotal = ordersMapper.countByOrderTimeRange(dayBegin, dayEnd).intValue();
            int dayValid = ordersMapper.countByStatusAndOrderTimeRange(Orders.COMPLETED, dayBegin, dayEnd).intValue();
            totalOrderCount += dayTotal;
            validOrderCount += dayValid;
            orderCountList.add(String.valueOf(dayTotal));
            validOrderCountList.add(String.valueOf(dayValid));
            cur = cur.plusDays(1);
        }
        double completionRate = totalOrderCount == 0 ? 0D : (double) validOrderCount / (double) totalOrderCount;
        OrdersStatisticsVO vo = new OrdersStatisticsVO();
        vo.setDateList(dateList.toString());
        vo.setOrderCountList(orderCountList.toString());
        vo.setValidOrderCountList(validOrderCountList.toString());
        vo.setTotalOrderCount(totalOrderCount);
        vo.setValidOrderCount(validOrderCount);
        vo.setOrderCompletionRate(completionRate);
        return vo;
    }

    @Override
    public Top10VO buildTop10(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        Date beginTime = dayBegin(begin);
        Date endTime = dayEnd(end);
        List<GoodsSales> sales = orderDetailMapper.top10(beginTime, endTime, Orders.COMPLETED);
        StringJoiner nameList = new StringJoiner(",");
        StringJoiner numberList = new StringJoiner(",");
        if (sales != null) {
            for (GoodsSales s : sales) {
                if (s == null) continue;
                nameList.add(s.getName() == null ? "" : s.getName());
                numberList.add(String.valueOf(s.getNumber() == null ? 0 : s.getNumber()));
            }
        }
        Top10VO vo = new Top10VO();
        vo.setNameList(nameList.toString());
        vo.setNumberList(numberList.toString());
        return vo;
    }
    private static void validateRange(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end) || end.equals(LocalDate.MAX)) {
            throw new BusinessException("参数错误");
        }
    }

    private static Date dayBegin(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date dayEnd(LocalDate date) {
        return Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
    }
}
