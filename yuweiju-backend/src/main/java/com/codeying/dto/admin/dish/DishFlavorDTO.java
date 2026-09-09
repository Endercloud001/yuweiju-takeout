package com.codeying.dto.admin.dish;

import lombok.Data;

/**
 * 菜品口味请求参数。
 *
 * @author Endercloud
 */
@Data
public class DishFlavorDTO {
    /**  ID */
    private Long id;
    /** dishId  */
    private Long dishId;
    /**  */
    private String name;
    /** value  */
    private String value;
}

