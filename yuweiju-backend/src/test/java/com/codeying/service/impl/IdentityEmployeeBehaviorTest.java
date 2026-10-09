package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.dto.admin.employee.*;
import com.codeying.entity.Employee;
import com.codeying.entity.User;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.EmployeeMapper;
import com.codeying.mapper.UserMapper;
import com.codeying.properties.SkyProperties;
import com.codeying.service.EmployeeService;
import com.codeying.service.WechatService;
import com.codeying.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 普通用例测试；真实筛选与排序由 isolated probe 验证。 */
class IdentityEmployeeBehaviorTest {
    EmployeeMapper employees;
    EmployeeService crud;
    EmployeeApplicationServiceImpl service;
    SkyProperties properties;

    @BeforeEach void setup() {
        employees = mock(EmployeeMapper.class);
        crud = mock(EmployeeService.class);
        properties = new SkyProperties();
        properties.getJwt().setAdminSecretKey("synthetic-admin-key-32-characters-only");
        properties.getJwt().setUserSecretKey("synthetic-user-key-32-characters-only");
        service = new EmployeeApplicationServiceImpl(crud, employees, properties);
    }
    LoginDTO credentials(String password) {
        LoginDTO dto = new LoginDTO(); dto.setUsername("fixture"); dto.setPassword(password); return dto;
    }
    Employee employee(long id) {
        Employee e = new Employee(); e.setId(id); e.setUsername("fixture"); e.setName("Fixture");
        e.setPassword("synthetic"); e.setStatus(1); return e;
    }

    @Test void loginValidWrongDisabledAndInvalid() {
        when(employees.findEnabledByCredentials("fixture", "synthetic")).thenReturn(employee(11));
        var login = service.login(credentials("synthetic"));
        assertEquals(11L, login.getId());
        assertEquals(11L, JwtUtil.parseClaims(login.getToken(), properties.getJwt().getAdminSecretKey()).get("uid", Long.class));
        assertThrows(BusinessException.class, () -> service.login(credentials("wrong")));
        // Mapper 的 disabled/null 语义在 probe 以真实 SQL 验证。
        when(employees.findEnabledByCredentials("fixture", "synthetic")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.login(credentials("synthetic")));
        assertThrows(BusinessException.class, () -> service.login(null));
    }

    @Test void passwordOwnershipAndOldPasswordRemainInReusableService() {
        EditPasswordDTO dto = new EditPasswordDTO(); dto.setEmpId(11L); dto.setOldPassword("synthetic"); dto.setNewPassword("synthetic-new");
        assertThrows(BusinessException.class, () -> service.editPassword(12L, dto));
        verifyNoInteractions(crud);
        when(crud.getById(11L)).thenReturn(employee(11));
        dto.setOldPassword("wrong");
        assertThrows(BusinessException.class, () -> service.editPassword(11L, dto));
        dto.setOldPassword("synthetic"); service.editPassword(11L, dto);
        verify(crud).updateById(argThat(e -> e.getId().equals(11L) && e.getUpdateUser().equals(11L) && "synthetic-new".equals(e.getPassword())));
    }

    @Test void statusValidationMissingAndAllowedExistingActor() {
        assertThrows(BusinessException.class, () -> service.setStatus(null, 0, 11L));
        assertThrows(BusinessException.class, () -> service.setStatus(12L, 2, 11L));
        assertThrows(BusinessException.class, () -> service.setStatus(12L, 0, 11L));
        when(crud.getById(11L)).thenReturn(employee(11));
        service.setStatus(12L, 0, 11L);
        verify(crud).updateById(argThat(e -> e.getStatus() == 0 && e.getUpdateUser().equals(12L)));
    }

    @Test void pageProducesSafeProjectionAndEmptyResult() {
        EmployeePageQuery q = new EmployeePageQuery(); q.setPage(1); q.setPageSize(2); q.setName(" Fixture ");
        when(employees.pageByName(any(), eq(" Fixture "))).thenReturn(new Page<Employee>(1,2,1).setRecords(List.of(employee(11))));
        assertEquals(11L, service.page(q).getRecords().get(0).getId());
        when(employees.pageByName(any(), any())).thenReturn(new Page<Employee>(1,2,0).setRecords(List.of()));
        assertTrue(service.page(q).getRecords().isEmpty());
        q.setPage(0); assertThrows(BusinessException.class, () -> service.page(q));
    }

    @Test void usernameConflictsAndActorValidation() {
        EmployeeDTO dto = new EmployeeDTO(); dto.setUsername(" fixture "); dto.setName("Fixture");
        dto.setPhone("00000000000"); dto.setSex("1"); dto.setIdNumber("fixture");
        assertThrows(BusinessException.class, () -> service.create(null, dto));
        when(employees.countByUsernameExcludingId("fixture", null)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(11L, dto));
        when(employees.countByUsernameExcludingId("fixture", null)).thenReturn(0L);
        service.create(11L, dto);
        verify(crud).save(argThat(e -> e.getStatus() == 1 && "fixture".equals(e.getUsername()) && e.getCreateUser().equals(11L)));
        dto.setId(11L); when(crud.getById(11L)).thenReturn(employee(11));
        when(employees.countByUsernameExcludingId("fixture", 11L)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.update(11L, dto));
    }

    @Test void wechatExistingMissingAndUpstreamFailure() {
        UserMapper mapper = mock(UserMapper.class); WechatService wechat = mock(WechatService.class);
        var users = new UserServiceImpl(mapper, wechat, properties);
        when(wechat.codeToOpenid("existing")).thenReturn("fixture-openid");
        User existing = new User(); existing.setId(22L); existing.setOpenid("fixture-openid");
        when(mapper.findByOpenid("fixture-openid")).thenReturn(existing);
        assertEquals(22L, users.login("existing").getId()); verify(mapper, never()).insert(any(User.class));
        when(wechat.codeToOpenid("new")).thenReturn("new-openid");
        when(mapper.insert(any(User.class))).thenAnswer(invocation -> { User u = invocation.getArgument(0); u.setId(23L); return 1; });
        var login = users.login("new"); assertEquals(23L, login.getId());
        assertEquals(23L, JwtUtil.parseClaims(login.getToken(), properties.getJwt().getUserSecretKey()).get("uid", Long.class));
        verify(mapper).insert(argThat((User u) -> u.getCreateTime() != null && "new-openid".equals(u.getOpenid())));
        reset(mapper); when(wechat.codeToOpenid("failure")).thenThrow(new IllegalStateException("fixture failure"));
        assertThrows(IllegalStateException.class, () -> users.login("failure")); verifyNoInteractions(mapper);
    }
}
