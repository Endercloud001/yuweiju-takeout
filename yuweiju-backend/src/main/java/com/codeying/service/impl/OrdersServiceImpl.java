package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Orders;
import com.codeying.mapper.OrdersMapper;
import com.codeying.service.OrdersService;
import org.springframework.stereotype.Service;

/**
 * 订单业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements OrdersService {}
