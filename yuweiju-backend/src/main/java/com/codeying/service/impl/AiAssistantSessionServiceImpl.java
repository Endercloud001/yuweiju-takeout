package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.AiAssistantSession;
import com.codeying.mapper.AiAssistantSessionMapper;
import com.codeying.service.AiAssistantSessionService;
import org.springframework.stereotype.Service;

/**
 * AI 助手会话服务实现。
 *
 * @author Endercloud
 */
@Service
public class AiAssistantSessionServiceImpl
        extends ServiceImpl<AiAssistantSessionMapper, AiAssistantSession>
        implements AiAssistantSessionService {}

