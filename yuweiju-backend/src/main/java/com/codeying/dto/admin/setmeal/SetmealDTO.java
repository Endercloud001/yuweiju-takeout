package com.codeying.dto.admin.setmeal;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套餐新增/修改请求参数。
 *
 * @author Endercloud
 */
@Data
public class SetmealDTO {
    /**  ID */
    private Long id;
    /** categoryId  */
    private Long categoryId;
    /**  */
    private String name;
    /** price  */
    @DecimalMin(value = "0", message = "金额不能为负数")
    @DecimalMax(value = "99999999.99", message = "金额超出存储范围")
    private BigDecimal price;
    /**  */
    private Integer status;
    /** description  */
    private String description;
    /** image  */
    private String image;
    /** setmealDishes  */
    @Valid
    private List<@NotNull(message = "套餐菜品明细不能为空") SetmealDishDTO> setmealDishes;
}

