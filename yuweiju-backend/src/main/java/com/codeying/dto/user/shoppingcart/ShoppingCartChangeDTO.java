package com.codeying.dto.user.shoppingcart;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * Shopping Cart Change DTO 
 *
 * @author Endercloud
 */
@Data
public class ShoppingCartChangeDTO {
    /** dishId  */
    private Long dishId;
    /** setmealId  */
    private Long setmealId;
    /** dishFlavor  */
    private String dishFlavor;

    @AssertTrue(message = "必须且只能选择菜品或套餐")
    public boolean isOneKindSelected() {
        /** hasDish  */
        boolean hasDish = dishId != null;
        /** hasSetmeal  */
        boolean hasSetmeal = setmealId != null;
        return hasDish ^ hasSetmeal;
    }
}

