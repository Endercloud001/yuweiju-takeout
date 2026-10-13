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
     * @return 经响应信封校验的模型文本；业务选择由推荐用例校验
     * @throws IllegalStateException 配置、HTTP、信封失败或冷却期抑制；没有自动重试
     */
    String chat(String systemPrompt, String userPrompt);
}

