package com.codeying.exception;

/** 微信上游失败；保留内部 cause，但 HTTP 与日志不暴露上游 URL/正文。 */
public class WechatLoginException extends IllegalStateException {
    public WechatLoginException(String message) {
        super(message);
    }

    public WechatLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
