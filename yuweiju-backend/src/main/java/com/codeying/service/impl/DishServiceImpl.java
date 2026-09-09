package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Dish;
import com.codeying.mapper.DishMapper;
import com.codeying.service.DishService;
import org.springframework.stereotype.Service;

/**
 * 菜品业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {}
