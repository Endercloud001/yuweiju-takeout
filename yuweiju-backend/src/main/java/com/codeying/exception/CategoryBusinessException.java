package com.codeying.exception;

/**
 * 分类业务异常。
 *
 * @author Endercloud
 */
public class CategoryBusinessException extends BusinessException {
    public CategoryBusinessException(String message) {
        super(message);
    }
    public CategoryBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

