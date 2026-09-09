package com.codeying.dto.admin.dish;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品新增/修改请求参数。
 *
 * @author Endercloud
 */
@Data
public class DishDTO {
    /**  ID */
    private Long id;
    /**  */
    private String name;
    /** categoryId  */
    private Long categoryId;
    /** price  */
    private BigDecimal price;
    /** image  */
    private String image;
    /** description  */
    private String description;
    /**  */
    private Integer status;
    /** flavors  */
    private List<DishFlavorDTO> flavors;
}

