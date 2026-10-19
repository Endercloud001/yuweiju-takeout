package com.codeying.vo.user;

import com.codeying.entity.ShoppingCart;
import lombok.Getter;
import lombok.Setter;

/** Compatible cart read extension. Unavailable rows retain snapshots but have no trusted amount. */
@Getter
@Setter
public class ShoppingCartItemVO extends ShoppingCart {
    private boolean available;
    private String unavailableReason;
}
