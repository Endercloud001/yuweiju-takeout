package com.codeying.service;

import java.time.LocalDate;

/**
 * AI 推荐天气服务。
 *
 * @author Endercloud
 */
public interface AiWeatherService {

    /**
     * 根据地址文本获取天气摘要。
     *
     * @param locationText 城市/地址文本
     * @return 天气摘要，失败时返回 null
     */
    String getWeatherSummary(String locationText);

    /**
     * 根据地址文本与日期获取天气编码，优先返回可用于训练的稳定分类值。
     *
     * @param locationText 城市/地址文本
     * @param date 目标日期
     * @return 天气编码，失败时返回 null
     */
    String getWeatherCode(String locationText, LocalDate date);
}
