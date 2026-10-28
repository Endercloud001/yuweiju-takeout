package com.codeying.dto.admin.dish;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

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
    @DecimalMin(value = "0", message = "金额不能为负数")
    @DecimalMax(value = "99999999.99", message = "金额超出存储范围")
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

