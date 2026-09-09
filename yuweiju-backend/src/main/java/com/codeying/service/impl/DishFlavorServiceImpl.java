package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.DishFlavor;
import com.codeying.mapper.DishFlavorMapper;
import com.codeying.service.DishFlavorService;
import org.springframework.stereotype.Service;

/**
 * 菜品口味业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class DishFlavorServiceImpl extends ServiceImpl<DishFlavorMapper, DishFlavor> implements DishFlavorService {}
