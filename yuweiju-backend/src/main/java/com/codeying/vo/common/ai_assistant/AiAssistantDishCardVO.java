package com.codeying.vo.common.ai_assistant;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 推荐菜品卡片数据。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantDishCardVO {
    /** dishId identifier. */
    private Long dishId;
    /** Display name. */
    private String name;
    /** Amount value. */
    private BigDecimal price;
    /** imageUrl field. */
    private String imageUrl;
    /** flavors field. */
    private List<String> flavors;
}

