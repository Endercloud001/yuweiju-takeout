package com.codeying.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构。
 *
 * @param <T> 数据类型
 * @author Endercloud
 */
@Data
public class ApiResult<T> implements Serializable {

    private Integer code;

    private Boolean success;

    private String message;

    private String msg;

    private T data;

    public ApiResult() {
    }

    public ApiResult(Integer code, Boolean success) {
        this.code = code;
        this.success = success;
    }

    public ApiResult(Integer code, Boolean success, String message) {
        this.code = code;
        this.success = success;
        this.message = message;
        this.msg = message;
    }

    public ApiResult(Integer code, Boolean success, String message, T data) {
        this.code = code;
        this.success = success;
        this.message = message;
        this.msg = message;
        this.data = data;
    }

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(1, true, "成功");
    }

    public static <T> ApiResult<T> successMsg(String message) {
        return new ApiResult<>(1, true, message);
    }

    public static <T> ApiResult<T> successData(T data) {
        return new ApiResult<>(1, true, "成功", data);
    }

    public static <T> ApiResult<T> fail() {
        return new ApiResult<>(0, false, "失败");
    }

    public static <T> ApiResult<T> badRequest(String message) {
        return new ApiResult<>(0, false, message, null);
    }

    public static <T> ApiResult<T> unauthorized(String message) {
        return new ApiResult<>(0, false, message, null);
    }

    public static <T> ApiResult<T> forbidden(String message) {
        return new ApiResult<>(0, false, message, null);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(0, false, message, null);
    }
}
