package com.codeying.dto.admin.setmeal;

import lombok.Data;

/**
 * 套餐菜品明细请求参数。
 *
 * @author Endercloud
 */
@Data
public class SetmealDishDTO {
    /**  ID */
    private Long id;
    /** setmealId  */
    private Long setmealId;
    /** dishId  */
    private Long dishId;
    /** copies  */
    private Integer copies;
}

