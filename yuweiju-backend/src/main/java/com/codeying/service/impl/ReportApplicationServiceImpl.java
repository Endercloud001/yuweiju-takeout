package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.OrderDetail;
import com.codeying.entity.Orders;
import com.codeying.entity.User;
import com.codeying.mapper.OrderDetailMapper;
import com.codeying.mapper.OrdersMapper;
import com.codeying.service.ReportApplicationService;
import com.codeying.service.OrdersService;
import com.codeying.service.UserService;
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
    private final OrdersService ordersService;
    private final UserService userService;
    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;

    public ReportApplicationServiceImpl(OrdersService ordersService, UserService userService, OrdersMapper ordersMapper, OrderDetailMapper orderDetailMapper) {
        this.ordersService = ordersService;
        this.userService = userService;
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
    }

    @Override
    public ReportBusinessData computeBusinessData(Date begin, Date end) {
        int newUsers = (int) userService.count(new QueryWrapper<User>().ge("create_time", begin).le("create_time", end));
        long totalOrders = ordersService.count(new QueryWrapper<Orders>().ge("order_time", begin).le("order_time", end));
        long validOrders = ordersService.count(new QueryWrapper<Orders>().ge("order_time", begin).le("order_time", end).eq("status", Orders.COMPLETED));

        QueryWrapper<Orders> turnoverWrapper = new QueryWrapper<>();
        turnoverWrapper.select("amount");
        turnoverWrapper.eq("status", Orders.COMPLETED);
        turnoverWrapper.ge("order_time", begin);
        turnoverWrapper.le("order_time", end);
        List<Orders> completed = ordersService.list(turnoverWrapper);
        BigDecimal turnover = BigDecimal.ZERO;
        for (Orders o : completed) {
            if (o != null && o.getAmount() != null) turnover = turnover.add(o.getAmount());
        }

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
        DecimalFormat df = new DecimalFormat("0.00");
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner turnoverList = new StringJoiner(",");
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = Date.from(cur.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(cur.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
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
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner newUserList = new StringJoiner(",");
        StringJoiner totalUserList = new StringJoiner(",");
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = Date.from(cur.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(cur.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
            int newUsers = (int) userService.count(new QueryWrapper<User>().ge("create_time", dayBegin).le("create_time", dayEnd));
            int totalUsers = (int) userService.count(new QueryWrapper<User>().le("create_time", dayEnd));
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
        StringJoiner dateList = new StringJoiner(",");
        StringJoiner orderCountList = new StringJoiner(",");
        StringJoiner validOrderCountList = new StringJoiner(",");
        int totalOrderCount = 0;
        int validOrderCount = 0;
        LocalDate cur = begin;
        while (!cur.isAfter(end)) {
            dateList.add(cur.toString());
            Date dayBegin = Date.from(cur.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(cur.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
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
        Date beginTime = Date.from(begin.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endTime = Date.from(end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
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
}
