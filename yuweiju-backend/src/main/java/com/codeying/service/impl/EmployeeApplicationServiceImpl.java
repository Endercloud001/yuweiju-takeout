package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.common.page.PageData;
import com.codeying.dto.admin.employee.EditPasswordDTO;
import com.codeying.dto.admin.employee.EmployeeDTO;
import com.codeying.dto.admin.employee.EmployeePageQuery;
import com.codeying.dto.admin.employee.LoginDTO;
import com.codeying.entity.Employee;
import com.codeying.mapper.EmployeeMapper;
import com.codeying.exception.BusinessException;
import com.codeying.properties.SkyProperties;
import com.codeying.service.EmployeeApplicationService;
import com.codeying.service.EmployeeService;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.admin.employee.EmployeeVO;
import com.codeying.vo.admin.employee.LoginVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Employee Application Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
public class EmployeeApplicationServiceImpl implements EmployeeApplicationService {

    private final EmployeeService employeeService;
    private final EmployeeMapper employeeMapper;
    private final SkyProperties skyProperties;

    public EmployeeApplicationServiceImpl(EmployeeService employeeService, EmployeeMapper employeeMapper, SkyProperties skyProperties) {
        this.employeeService = employeeService;
        this.employeeMapper = employeeMapper;
        this.skyProperties = skyProperties;
    }

    @Override
    public LoginVO login(LoginDTO body) {
        if (body == null || !StringUtils.hasText(body.getUsername()) || !StringUtils.hasText(body.getPassword())) {
            throw new BusinessException("参数错误");
        }
        Employee employee = employeeMapper.findEnabledByCredentials(body.getUsername(), body.getPassword());
        if (employee == null) {
            throw new BusinessException("账号或密码错误");
        }
        long ttl = skyProperties.getJwt().getAdminTtl() == null ? 72000000L : skyProperties.getJwt().getAdminTtl();
        String token = JwtUtil.createToken(
                skyProperties.getJwt().getAdminSecretKey(),
                ttl,
                employee.getId(),
                employee.getUsername(),
                "admin"
        );
        LoginVO vo = new LoginVO();
        vo.setId(employee.getId());
        vo.setName(employee.getName());
        vo.setUserName(employee.getUsername());
        vo.setToken(token);
        vo.setTtlMillis(ttl);
        return vo;
    }

    @Override
    public void editPassword(Long adminId, EditPasswordDTO body) {
        if (adminId == null || body == null || body.getEmpId() == null || !StringUtils.hasText(body.getOldPassword()) || !StringUtils.hasText(body.getNewPassword())) {
            throw new BusinessException("参数错误");
        }
        if (!adminId.equals(body.getEmpId())) throw new BusinessException("无权限");
        Employee employee = employeeService.getById(body.getEmpId());
        if (employee == null) throw new BusinessException("员工不存在");
        if (!body.getOldPassword().equals(employee.getPassword())) throw new BusinessException("旧密码错误");
        Employee update = new Employee();
        update.setId(employee.getId());
        update.setPassword(body.getNewPassword());
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        employeeService.updateById(update);
    }

    @Override
    public void setStatus(Long adminId, Integer status, Long id) {
        if (adminId == null || status == null || id == null || (status != 0 && status != 1)) throw new BusinessException("参数错误");
        Employee employee = employeeService.getById(id);
        if (employee == null) throw new BusinessException("员工不存在");
        Employee update = new Employee();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        update.setUpdateUser(adminId);
        employeeService.updateById(update);
    }

    @Override
    public PageData<EmployeeVO> page(EmployeePageQuery query) {
        if (query == null || query.getPage() == null || query.getPageSize() == null || query.getPage() <= 0 || query.getPageSize() <= 0) {
            throw new BusinessException("参数错误");
        }
        IPage<Employee> result = employeeMapper.pageByName(new Page<>(query.getPage(), query.getPageSize()), query.getName());
        List<EmployeeVO> records = new ArrayList<>();
        for (Employee e : result.getRecords()) {
            records.add(toVO(e));
        }
        PageData<EmployeeVO> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(records);
        return data;
    }

    @Override
    public void create(Long adminId, EmployeeDTO body) {
        if (adminId == null || body == null || !StringUtils.hasText(body.getUsername()) || !StringUtils.hasText(body.getName())
                || !StringUtils.hasText(body.getPhone()) || !StringUtils.hasText(body.getSex()) || !StringUtils.hasText(body.getIdNumber())) {
            throw new BusinessException("参数错误");
        }
        if (employeeMapper.countByUsernameExcludingId(body.getUsername().trim(), null) > 0) throw new BusinessException("用户名已存在");
        Date now = new Date();
        Employee entity = new Employee();
        entity.setUsername(body.getUsername().trim());
        entity.setName(body.getName().trim());
        entity.setPhone(body.getPhone().trim());
        entity.setSex(body.getSex().trim());
        entity.setIdNumber(body.getIdNumber().trim());
        entity.setStatus(1);
        entity.setPassword("123456");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateUser(adminId);
        entity.setUpdateUser(adminId);
        employeeService.save(entity);
    }

    @Override
    public EmployeeVO getById(Long id) {
        if (id == null) throw new BusinessException("参数错误");
        Employee employee = employeeService.getById(id);
        if (employee == null) throw new BusinessException("员工不存在");
        return toVO(employee);
    }

    @Override
    public void update(Long adminId, EmployeeDTO body) {
        if (adminId == null || body == null || body.getId() == null) throw new BusinessException("参数错误");
        Employee existing = employeeService.getById(body.getId());
        if (existing == null) throw new BusinessException("员工不存在");
        if (employeeMapper.countByUsernameExcludingId(body.getUsername().trim(), body.getId()) > 0) throw new BusinessException("用户名已存在");
        Employee entity = new Employee();
        entity.setId(body.getId());
        entity.setUsername(body.getUsername().trim());
        entity.setName(body.getName().trim());
        entity.setPhone(body.getPhone().trim());
        entity.setSex(body.getSex().trim());
        entity.setIdNumber(body.getIdNumber().trim());
        entity.setUpdateTime(new Date());
        entity.setUpdateUser(adminId);
        employeeService.updateById(entity);
    }

    private EmployeeVO toVO(Employee employee) {
        if (employee == null) return null;
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setUsername(employee.getUsername());
        vo.setName(employee.getName());
        vo.setPhone(employee.getPhone());
        vo.setSex(employee.getSex());
        vo.setIdNumber(employee.getIdNumber());
        vo.setStatus(employee.getStatus());
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        vo.setCreateUser(employee.getCreateUser());
        vo.setUpdateUser(employee.getUpdateUser());
        return vo;
    }
}
