package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.CustomerServiceSession;
import com.codeying.mapper.CustomerServiceSessionMapper;
import com.codeying.service.CustomerServiceSessionService;
import org.springframework.stereotype.Service;

/**
 * 人工客服会话业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class CustomerServiceSessionServiceImpl
        extends ServiceImpl<CustomerServiceSessionMapper, CustomerServiceSession>
        implements CustomerServiceSessionService {}
