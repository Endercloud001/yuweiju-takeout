package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Category;
import com.codeying.mapper.CategoryMapper;
import com.codeying.service.CategoryService;
import org.springframework.stereotype.Service;

/**
 * 分类业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {}
