package com.codeying.vo.user.customer_service;

import lombok.Data;

import java.util.Date;

/**
 * 用户端人工客服会话 VO。
 *
 * @author Endercloud
 */
@Data
public class CustomerServiceSessionVO {
    /** 会话 ID */
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 会话状态：1-进行中 2-已关闭 */
    private Integer status;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
    /** 关闭时间 */
    private Date closeTime;
}
