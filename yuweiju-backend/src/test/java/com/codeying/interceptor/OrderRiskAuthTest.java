package com.codeying.interceptor;

import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRiskAuthTest {
    @Test
    void everyRiskRouteRequiresAdminSubjectEvenWithValidAdminSignature() throws Exception {
        var properties = new SkyProperties();
        properties.getJwt().setAdminSecretKey("synthetic-only-admin-signature-key-32");
        properties.getJwt().setAdminTokenName("token");
        var interceptor = new JwtAuthInterceptor(properties, mock(TokenBlacklistService.class), new ObjectMapper());
        for (String endpoint : new String[]{"training/run", "training/readiness", "models", "replay", "feedback"}) {
            for (String subject : new String[]{"user", "admin"}) {
                var request = new MockHttpServletRequest("POST", "/admin/order-risk/" + endpoint);
                request.addHeader("token", JwtUtil.createToken(properties.getJwt().getAdminSecretKey(), 60000, 1L, "fixture", subject));
                var response = new MockHttpServletResponse();
                assertEquals("admin".equals(subject), interceptor.preHandle(request, response, new Object()));
                if ("user".equals(subject)) {
                    assertEquals(401, response.getStatus());
                    assertNull(request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
                } else assertEquals(1L, request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
            }
        }
    }
}
