package com.codeying.dto.admin.setmeal;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
    @NotNull(message = "组成菜品不能为空")
    private Long dishId;
    /** copies  */
    @NotNull(message = "菜品份数不能为空")
    @Positive(message = "菜品份数必须为正整数")
    @JsonDeserialize(using = SetmealCopiesDeserializer.class)
    private Integer copies;
}

