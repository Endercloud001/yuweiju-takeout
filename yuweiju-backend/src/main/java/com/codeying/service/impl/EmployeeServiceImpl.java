package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Employee;
import com.codeying.mapper.EmployeeMapper;
import com.codeying.service.EmployeeService;
import org.springframework.stereotype.Service;

/**
 * 员工业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {}
