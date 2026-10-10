package com.codeying.exception;

/**
 * 菜品业务异常。
 *
 * @author Endercloud
 */
public class DishBusinessException extends BusinessException {
    public DishBusinessException(String message) {
        super(message);
    }
    public DishBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

