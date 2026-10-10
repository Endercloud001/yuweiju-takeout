package com.codeying.exception;

/**
 * 套餐业务异常。
 *
 * @author Endercloud
 */
public class SetmealBusinessException extends BusinessException {
    public SetmealBusinessException(String message) {
        super(message);
    }
    public SetmealBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

