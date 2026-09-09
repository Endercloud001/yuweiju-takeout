package com.codeying.handler;

import com.codeying.exception.BusinessException;
import com.codeying.result.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * @author Endercloud
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Object> handleBusinessException(BusinessException e) {
        return ApiResult.badRequest(e.getMessage());
    }

    /**
     * 兜底异常处理，避免堆栈直接暴露给前端。
     *
     * @param e 异常
     * @return 统一响应结构
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Object> handleException(Exception e) {
        logger.error("发生错误", e);
        if (environment != null && environment.matchesProfiles("dev")) {
            return ApiResult.fail(e.getClass().getName() + ": " + e.getMessage());
        }
        return ApiResult.fail("系统繁忙，请稍后再试或联系管理员");
    }
}
