package com.codeying.controller.admin;

import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisScopeTest {
    @Test void rejectsWrongSubjectEvenWhenSignedByAdministratorKey() throws Exception {
        var props = new SkyProperties();
        props.getJwt().setAdminSecretKey("isolated-admin-key-for-analysis-scope");
        props.getJwt().setAdminTokenName("token");
        var interceptor = new JwtAuthInterceptor(props, mock(TokenBlacklistService.class), new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/admin/analysis/training/run");
        request.addHeader("token", JwtUtil.createToken(props.getJwt().getAdminSecretKey(),60000,1L,"fixture","user"));
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request,response,new Object()));
        assertEquals(401,response.getStatus());
        assertNull(request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
        request.removeHeader("token");
        request.addHeader("token", JwtUtil.createToken(props.getJwt().getAdminSecretKey(),60000,1L,"fixture","admin"));
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertEquals(1L,request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
    }
}
