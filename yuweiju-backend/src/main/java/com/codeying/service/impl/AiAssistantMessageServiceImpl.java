package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.AiAssistantMessage;
import com.codeying.mapper.AiAssistantMessageMapper;
import com.codeying.service.AiAssistantMessageService;
import org.springframework.stereotype.Service;

/**
 * AI 助手消息服务实现。
 *
 * @author Endercloud
 */
@Service
public class AiAssistantMessageServiceImpl
        extends ServiceImpl<AiAssistantMessageMapper, AiAssistantMessage>
        implements AiAssistantMessageService {}

