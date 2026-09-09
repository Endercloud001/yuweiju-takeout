package com.codeying.vo.admin.customer_service;

import lombok.Data;

import java.util.Date;

/**
 * 管理端人工客服会话摘要 VO。
 *
 * @author Endercloud
 */
@Data
public class CustomerServiceSessionSummaryVO {
    /** 会话 ID */
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 用户昵称 */
    private String userName;
    /** 用户头像 */
    private String userAvatar;
    /** 会话状态：1-进行中 2-已关闭 */
    private Integer status;
    /** 更新时间 */
    private Date updateTime;
    /** 最后一条消息预览 */
    private String lastMessagePreview;
}
