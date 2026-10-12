package com.codeying.controller.admin;

import com.codeying.handler.GlobalExceptionHandler;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.service.DefaultImageLibraryService;
import com.codeying.service.UploadService;
import com.codeying.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageProtocolTest {
    @Test void bindingFailuresUseFriendlyExistingBusinessResponse() throws Exception {
        var uploads=mock(UploadService.class); var library=mock(DefaultImageLibraryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AdminCommonController(uploads,library))
                .setControllerAdvice(new GlobalExceptionHandler(null)).build();
        mvc.perform(multipart("/admin/common/upload").param("scene","dish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("文件不能为空"));
        mvc.perform(multipart("/admin/common/upload").file(new MockMultipartFile("file","x.png","image/png",new byte[]{1})).param("maxSize","0"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("maxSize 必须大于 0"));
        mvc.perform(get("/admin/common/default-images").param("limit","0"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("limit 必须大于 0"));
        verifyNoInteractions(uploads,library);
    }
    @Test void realInterceptorRejectsAnonymousWrongSecretAndPassesAdmin() throws Exception {
        var properties=new SkyProperties();properties.getJwt().setAdminTokenName("token");properties.getJwt().setUserTokenName("authentication");
        properties.getJwt().setAdminSecretKey("synthetic-admin-key-32-characters-only");properties.getJwt().setUserSecretKey("synthetic-user-key-32-characters-only");
        var uploads=mock(UploadService.class);var library=mock(DefaultImageLibraryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AdminCommonController(uploads,library))
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .addInterceptors(new JwtAuthInterceptor(properties,mock(TokenBlacklistService.class),new com.fasterxml.jackson.databind.ObjectMapper())).build();
        var user=JwtUtil.createToken(properties.getJwt().getUserSecretKey(),60000,900001L,"fixture","user");
        mvc.perform(multipart("/admin/common/upload")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/admin/common/default-images").header("token",user)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(uploads,library);
        var admin=JwtUtil.createToken(properties.getJwt().getAdminSecretKey(),60000,900001L,"fixture","admin");
        when(uploads.upload(any())).thenReturn("local-url");
        mvc.perform(multipart("/admin/common/upload").file(new MockMultipartFile("file","x.png","image/png",new byte[]{1})).header("token",admin))
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data").value("local-url"));verify(uploads).upload(any());
    }
}
