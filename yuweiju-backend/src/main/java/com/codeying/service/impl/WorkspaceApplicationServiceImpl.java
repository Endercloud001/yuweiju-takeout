package com.codeying.service.impl;

import com.codeying.entity.Orders;
import com.codeying.mapper.OrdersMapper;
import com.codeying.mapper.UserMapper;
import com.codeying.mapper.DishMapper;
import com.codeying.mapper.SetmealMapper;
import com.codeying.service.WorkspaceApplicationService;
import com.codeying.vo.admin.workspace.BusinessDataVO;
import com.codeying.vo.admin.workspace.OverviewOrdersVO;
import com.codeying.vo.admin.workspace.OverviewVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Workspace Application Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
public class WorkspaceApplicationServiceImpl implements WorkspaceApplicationService {

    private final OrdersMapper ordersMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    public WorkspaceApplicationServiceImpl(OrdersMapper ordersMapper, UserMapper userMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.ordersMapper = ordersMapper;
        this.userMapper = userMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    @Override
    public BusinessDataVO businessData() {
        Date begin = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = new Date();

        int newUsers = userMapper.countCreatedInRange(begin, end).intValue();
        var actual = ordersMapper.aggregateBusinessByOrderTimeRange(begin, end, Orders.COMPLETED);
        long totalOrderCount = actual.getTotalOrders();
        long validOrderCount = actual.getValidOrders();
        BigDecimal turnoverBd = actual.getTurnover();
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
        vo.setSold(dishMapper.countByStatus(1).intValue());
        vo.setDiscontinued(dishMapper.countByStatus(0).intValue());
        return vo;
    }

    @Override
    public OverviewVO overviewSetmeals() {
        OverviewVO vo = new OverviewVO();
        vo.setSold(setmealMapper.countByStatus(1).intValue());
        vo.setDiscontinued(setmealMapper.countByStatus(0).intValue());
        return vo;
    }

    @Override
    public OverviewOrdersVO overviewOrders() {
        OverviewOrdersVO vo = new OverviewOrdersVO();
        vo.setAllOrders(ordersMapper.countAllOrders().intValue());
        vo.setCancelledOrders(ordersMapper.countByStatus(Orders.CANCELLED).intValue());
        vo.setCompletedOrders(ordersMapper.countByStatus(Orders.COMPLETED).intValue());
        vo.setDeliveredOrders(ordersMapper.countByStatus(Orders.CONFIRMED).intValue());
        vo.setWaitingOrders(ordersMapper.countByStatus(Orders.TO_BE_CONFIRMED).intValue());
        return vo;
    }
}

