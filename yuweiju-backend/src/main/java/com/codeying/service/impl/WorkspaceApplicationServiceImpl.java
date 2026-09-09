package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.Dish;
import com.codeying.entity.Orders;
import com.codeying.entity.Setmeal;
import com.codeying.entity.User;
import com.codeying.service.DishService;
import com.codeying.service.OrdersService;
import com.codeying.service.SetmealService;
import com.codeying.service.UserService;
import com.codeying.service.WorkspaceApplicationService;
import com.codeying.vo.admin.workspace.BusinessDataVO;
import com.codeying.vo.admin.workspace.OverviewOrdersVO;
import com.codeying.vo.admin.workspace.OverviewVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Workspace Application Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
public class WorkspaceApplicationServiceImpl implements WorkspaceApplicationService {

    private final OrdersService ordersService;
    private final UserService userService;
    private final DishService dishService;
    private final SetmealService setmealService;

    public WorkspaceApplicationServiceImpl(OrdersService ordersService, UserService userService, DishService dishService, SetmealService setmealService) {
        this.ordersService = ordersService;
        this.userService = userService;
        this.dishService = dishService;
        this.setmealService = setmealService;
    }

    @Override
    public BusinessDataVO businessData() {
        Date begin = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = new Date();

        int newUsers = (int) userService.count(new QueryWrapper<User>().ge("create_time", begin).le("create_time", end));
        long totalOrderCount = ordersService.count(new QueryWrapper<Orders>().ge("order_time", begin).le("order_time", end));
        long validOrderCount = ordersService.count(new QueryWrapper<Orders>().ge("order_time", begin).le("order_time", end).eq("status", Orders.COMPLETED));

        QueryWrapper<Orders> turnoverWrapper = new QueryWrapper<>();
        turnoverWrapper.select("amount");
        turnoverWrapper.eq("status", Orders.COMPLETED);
        turnoverWrapper.ge("order_time", begin);
        turnoverWrapper.le("order_time", end);
        List<Orders> completed = ordersService.list(turnoverWrapper);
        BigDecimal turnoverBd = BigDecimal.ZERO;
        for (Orders o : completed) {
            if (o != null && o.getAmount() != null) turnoverBd = turnoverBd.add(o.getAmount());
        }
        double turnover = turnoverBd.doubleValue();
        double orderCompletionRate = totalOrderCount == 0 ? 0D : (double) validOrderCount / (double) totalOrderCount;
        double unitPrice = validOrderCount == 0 ? 0D : turnover / (double) validOrderCount;

        BusinessDataVO vo = new BusinessDataVO();
        vo.setNewUsers(newUsers);
        vo.setOrderCompletionRate(orderCompletionRate);
        vo.setTurnover(turnover);
        vo.setUnitPrice(unitPrice);
        vo.setValidOrderCount((int) validOrderCount);
        return vo;
    }

    @Override
    public OverviewVO overviewDishes() {
        OverviewVO vo = new OverviewVO();
        vo.setSold((int) dishService.count(new QueryWrapper<Dish>().eq("status", 1)));
        vo.setDiscontinued((int) dishService.count(new QueryWrapper<Dish>().eq("status", 0)));
        return vo;
    }

    @Override
    public OverviewVO overviewSetmeals() {
        OverviewVO vo = new OverviewVO();
        vo.setSold((int) setmealService.count(new QueryWrapper<Setmeal>().eq("status", 1)));
        vo.setDiscontinued((int) setmealService.count(new QueryWrapper<Setmeal>().eq("status", 0)));
        return vo;
    }

    @Override
    public OverviewOrdersVO overviewOrders() {
        OverviewOrdersVO vo = new OverviewOrdersVO();
        vo.setAllOrders((int) ordersService.count());
        vo.setCancelledOrders((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.CANCELLED)));
        vo.setCompletedOrders((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.COMPLETED)));
        vo.setDeliveredOrders((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.CONFIRMED)));
        vo.setWaitingOrders((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.TO_BE_CONFIRMED)));
        return vo;
    }
}

