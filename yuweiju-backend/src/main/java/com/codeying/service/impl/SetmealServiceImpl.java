package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Setmeal;
import com.codeying.mapper.SetmealMapper;
import com.codeying.service.SetmealService;
import org.springframework.stereotype.Service;

/**
 * 套餐业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements SetmealService {}
