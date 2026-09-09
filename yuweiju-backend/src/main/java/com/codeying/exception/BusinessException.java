package com.codeying.exception;

/**
 * 业务异常基类。
 *
 * @author Endercloud
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

