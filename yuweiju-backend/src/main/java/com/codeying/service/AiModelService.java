package com.codeying.service;

/**
 * AI 大模型调用服务。
 *
 * @author Endercloud
 */
public interface AiModelService {

    /**
     * 以 system + user 方式调用聊天模型。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return 模型文本回复
     */
    String chat(String systemPrompt, String userPrompt);
}

