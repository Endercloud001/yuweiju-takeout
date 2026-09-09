package com.codeying.vo.common.customer_service;

import lombok.Data;

import java.util.Date;

/**
 * 人工客服消息 VO。
 *
 * @author Endercloud
 */
@Data
public class CustomerServiceMessageVO {
    /** 消息 ID */
    private Long id;
    /** 会话 ID */
    private Long sessionId;
    /** 发送方类型：1-用户 2-管理员 3-系统 */
    private Integer senderType;
    /** 发送方 ID（系统消息为空） */
    private Long senderId;
    /** 内容 */
    private String content;
    /** 创建时间 */
    private Date createTime;
}
