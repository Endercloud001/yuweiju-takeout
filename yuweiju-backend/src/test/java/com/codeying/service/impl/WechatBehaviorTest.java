package com.codeying.service.impl;

import com.codeying.exception.WechatLoginException;
import com.codeying.handler.GlobalExceptionHandler;
import com.codeying.properties.SkyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatBehaviorTest {
    SkyProperties configured() {
        SkyProperties p = new SkyProperties(); p.getWechat().setAppid("synthetic-app");
        p.getWechat().setSecret("synthetic-sensitive-marker"); p.getWechat().setMockLogin(false); return p;
    }
    MockRestServiceServer server(Object service) {
        return MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    }
    @Test void productionIdentityAndMissingIdentity() {
        var service = new WechatServiceImpl(configured()); var server = server(service);
        server.expect(anything()).andRespond(withSuccess("{\"openid\":\"fixture-openid\"}", MediaType.APPLICATION_JSON));
        assertEquals("fixture-openid", service.codeToOpenid("code")); server.verify();
        server.reset(); server.expect(anything()).andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"synthetic-sensitive-marker\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(WechatLoginException.class, () -> service.codeToOpenid("code"));
        assertFalse(error.getMessage().contains("synthetic-sensitive-marker"));
        assertEquals(0, new GlobalExceptionHandler(null).handleWechatLoginException(error).getCode()); server.verify();
    }
    @Test void upstreamTransportAndMalformedResponsePreserveSafeFailure() {
        var service = new WechatServiceImpl(configured()); var server = server(service);
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var error = assertThrows(WechatLoginException.class, () -> service.codeToOpenid("code"));
        assertNotNull(error.getCause()); assertFalse(error.getMessage().contains("secret=")); server.verify();
        server.reset(); server.expect(anything()).andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));
        assertThrows(WechatLoginException.class, () -> service.codeToOpenid("code")); server.verify();
    }
    @Test void developmentFallbackIsPreserved() {
        var p = configured(); var service = new WechatServiceDevImpl(p);
        assertThrows(IllegalStateException.class, () -> service.codeToOpenid(" "));
        assertEquals("mock_openid_test", service.codeToOpenid("test"));
        var server = server(service); server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals("mock_openid_dev", service.codeToOpenid("code")); server.verify();
        p.getWechat().setMockLogin(true); assertEquals("mock_openid_dev", service.codeToOpenid("another"));
        p.getWechat().setMockLogin(false); p.getWechat().setSecret(null);
        assertEquals("mock_openid_dev", service.codeToOpenid("another"));
    }
}
