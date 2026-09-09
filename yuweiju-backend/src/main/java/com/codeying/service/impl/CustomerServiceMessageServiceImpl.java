package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.CustomerServiceMessage;
import com.codeying.mapper.CustomerServiceMessageMapper;
import com.codeying.service.CustomerServiceMessageService;
import org.springframework.stereotype.Service;

/**
 * 人工客服消息业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class CustomerServiceMessageServiceImpl
        extends ServiceImpl<CustomerServiceMessageMapper, CustomerServiceMessage>
        implements CustomerServiceMessageService {}
