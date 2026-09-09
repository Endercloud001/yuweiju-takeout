package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.OrderDetail;
import com.codeying.mapper.OrderDetailMapper;
import com.codeying.service.OrderDetailService;
import org.springframework.stereotype.Service;

/**
 * 订单明细业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements OrderDetailService {}
