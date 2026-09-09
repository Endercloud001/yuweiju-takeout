package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.User;
import com.codeying.mapper.UserMapper;
import com.codeying.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {}
