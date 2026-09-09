package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.SetmealDish;
import com.codeying.mapper.SetmealDishMapper;
import com.codeying.service.SetmealDishService;
import org.springframework.stereotype.Service;

/**
 * 套餐菜品关联业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class SetmealDishServiceImpl extends ServiceImpl<SetmealDishMapper, SetmealDish> implements SetmealDishService {}
