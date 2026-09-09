package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.ShoppingCart;
import com.codeying.mapper.ShoppingCartMapper;
import com.codeying.service.ShoppingCartService;
import org.springframework.stereotype.Service;

/**
 * 购物车业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart> implements ShoppingCartService {}
