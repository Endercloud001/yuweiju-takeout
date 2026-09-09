package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.mapper.AdminMapper;
import com.codeying.entity.Admin;
import com.codeying.service.AdminService;
import org.springframework.stereotype.Service;

/**
 * Admin Service implementation.
 *
 * @author Endercloud
 */
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {}
