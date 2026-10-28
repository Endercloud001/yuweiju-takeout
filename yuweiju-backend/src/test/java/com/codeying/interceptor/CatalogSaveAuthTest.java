package com.codeying.interceptor;

import com.codeying.properties.SkyProperties;
import com.codeying.controller.admin.AdminDishController;
import com.codeying.controller.admin.AdminSetmealController;
import org.springframework.web.method.HandlerMethod;
import java.util.Arrays;
import com.codeying.security.TokenBlacklistService;
import com.codeying.service.DishApplicationService;
import com.codeying.service.SetmealApplicationService;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CatalogSaveAuthTest {
    @ParameterizedTest
    @CsvSource({"POST,/admin/dish", "PUT,/admin/dish", "POST,/admin/setmeal", "PUT,/admin/setmeal"})
    void catalogSaveRequiresBothAdministratorSignatureAndScope(String method, String path) throws Exception {
        var properties = new SkyProperties();
        String key = "synthetic-only-catalog-admin-key-32";
        properties.getJwt().setAdminSecretKey(key); properties.getJwt().setAdminTokenName("token");
        var interceptor = new JwtAuthInterceptor(properties, mock(TokenBlacklistService.class), new ObjectMapper());
        Object controller = path.contains("setmeal")
                ? new AdminSetmealController(mock(SetmealApplicationService.class))
                : new AdminDishController(mock(DishApplicationService.class));
        var handler = new HandlerMethod(controller, Arrays.stream(controller.getClass().getMethods())
                .filter(m -> m.getName().equals(method.equals("POST") ? "create" : "update"))
                .findFirst().orElseThrow());
        for (String credential : new String[]{"anonymous", "wrong-key", "wrong-scope", "admin"}) {
            var request = new MockHttpServletRequest(method,path);
            if (!credential.equals("anonymous")) request.addHeader("token", JwtUtil.createToken(
                    credential.equals("wrong-key") ? "synthetic-only-user-key-32-characters" : key,
                    60000,1L,"fixture",credential.equals("admin") ? "admin" : "user"));
            var response = new MockHttpServletResponse();
            assertEquals(credential.equals("admin"),interceptor.preHandle(request,response,handler));
            if (credential.equals("admin")) assertEquals(1L,request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
            else {
                assertEquals(401,response.getStatus());
                assertNull(request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID));
            }
        }
    }
}
