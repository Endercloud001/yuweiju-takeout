package com.codeying.dto.admin.setmeal;

import lombok.Data;

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
    private BigDecimal price;
    /**  */
    private Integer status;
    /** description  */
    private String description;
    /** image  */
    private String image;
    /** setmealDishes  */
    private List<SetmealDishDTO> setmealDishes;
}

