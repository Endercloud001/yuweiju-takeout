package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.ShoppingCart;

/**
 * 购物车业务服务。
 *
 * @author Endercloud
 */
public interface ShoppingCartService extends IService<ShoppingCart> {
    /** Adds one saleable item for an authenticated user; core SQL failure propagates. */
    void addItem(Long userId, com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO body);
    /** Decrements/removes one owned cart item; missing item is rejected. */
    void subtractItem(Long userId, com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO body);
    /** Lists only the authenticated user's cart in stable creation order. */
    java.util.List<ShoppingCart> listForUser(Long userId);
    /** Deletes only this user's cart and returns actual affected rows. */
    int clearForUser(Long userId);
    /** Rejects missing or unsaleable goods, including existing cart entries. */
    void requireSaleable(Long dishId, Long setmealId);
}
