package com.codeying.controller;

import com.codeying.config.WebMvcConfiguration;
import com.codeying.controller.admin.AdminEmployeeController;
import com.codeying.controller.user.UserAuthController;
import com.codeying.handler.GlobalExceptionHandler;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.service.EmployeeApplicationService;
import com.codeying.service.UserService;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.user.auth.LoginVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdentityControllerSecurityTest {
    SkyProperties properties() {
        SkyProperties p = new SkyProperties();
        p.getJwt().setAdminSecretKey("synthetic-admin-key-32-characters-only"); p.getJwt().setAdminTokenName("token");
        p.getJwt().setUserSecretKey("synthetic-user-key-32-characters-only"); p.getJwt().setUserTokenName("authentication"); return p;
    }
    @Test void userLoginDelegatesAndValidationRejectsBeforeService() throws Exception {
        var p = properties(); var users = mock(UserService.class); var blacklist = mock(TokenBlacklistService.class);
        var login = new LoginVO(); login.setId(1L); login.setOpenid("fixture"); login.setToken("synthetic-token");
        when(users.login("fixture-code")).thenReturn(login);
        var mvc = MockMvcBuilders.standaloneSetup(new UserAuthController(p, blacklist, users))
                .setControllerAdvice(new GlobalExceptionHandler(null)).build();
        mvc.perform(post("/user/user/login").contentType("application/json").content("{\"code\":\"fixture-code\"}"))
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(post("/user/user/login").contentType("application/json").content("{\"code\":\"\"}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(users, times(1)).login(anyString());
    }
    @Test void controllersRejectAbsentIdentityAndUseAuthenticatedPasswordOwner() throws Exception {
        var p = properties(); var service = mock(EmployeeApplicationService.class); var blacklist = mock(TokenBlacklistService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminEmployeeController(service, p, blacklist)).build();
        mvc.perform(get("/admin/employee/page").param("page","1").param("pageSize","1"))
                .andExpect(jsonPath("$.code").value(0)); verifyNoInteractions(service);
        mvc.perform(put("/admin/employee/editPassword").requestAttr(JwtAuthInterceptor.ATTR_ADMIN_ID, 9L)
                .contentType("application/json").content("{\"empId\":9,\"oldPassword\":\"synthetic-old\",\"newPassword\":\"synthetic-new\"}"))
                .andExpect(jsonPath("$.code").value(1));
        verify(service).editPassword(eq(9L), any());
    }
    @Test void interceptorRejectsMissingWrongSecretExpiredAndBlacklistedAndSetsIdentity() throws Exception {
        var p = properties(); var blacklist = mock(TokenBlacklistService.class);
        var interceptor = new JwtAuthInterceptor(p, blacklist, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/admin/employee/page"); var response = new MockHttpServletResponse();
        request.getSession().setAttribute("user", new Object());
        assertFalse(interceptor.preHandle(request,response,new Object())); assertEquals(401,response.getStatus());
        request.addHeader("token", JwtUtil.createToken(p.getJwt().getUserSecretKey(),60000,1L,"fixture","user"));
        assertFalse(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        request.removeHeader("token"); request.addHeader("token",JwtUtil.createToken(p.getJwt().getAdminSecretKey(),-1000,1L,"fixture","admin"));
        assertFalse(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        request.removeHeader("token"); request.addHeader("token", "Bearer " + JwtUtil.createToken(p.getJwt().getAdminSecretKey(),60000,9L,"fixture","admin"));
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertEquals(9L, request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
        when(blacklist.isBlacklisted(anyString())).thenReturn(true);
        assertFalse(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        request.setMethod("OPTIONS"); assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
    }
}
