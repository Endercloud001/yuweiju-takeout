package com.codeying.controller.common;

import com.codeying.entity.LoginUser;
import com.codeying.result.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 页面控制器公共基类。
 *
 * @author Endercloud
 */
public class BaseController {

    @Autowired
    protected HttpServletRequest req;

    @Autowired
    protected HttpServletResponse resp;

    @Autowired
    protected HttpSession session;

    protected LoginUser getCurrentUser() {
        return (LoginUser) getSessionValue("user");
    }

    protected Object getSessionValue(String key) {
        return this.session.getAttribute(key);
    }

    protected void setSessionValue(String key, Object value) {
        this.session.setAttribute(key, value);
    }

    protected <T> ApiResult<T> success() {
        return ApiResult.success();
    }

    protected <T> ApiResult<T> fail() {
        return ApiResult.fail();
    }

    protected <T> ApiResult<T> fail(String message) {
        return ApiResult.fail(message);
    }
}

