package com.codeying.exception;

/**
 * 频率限制异常。
 *
 * @author Endercloud
 */
public class RateLimitException extends BusinessException {
    public RateLimitException(String message) {
        super(message);
    }
}

