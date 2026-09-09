package com.codeying.exception;

/**
 * 订单业务异常。
 *
 * @author Endercloud
 */
public class OrderBusinessException extends BusinessException {
    public OrderBusinessException(String message) {
        super(message);
    }

    public OrderBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

