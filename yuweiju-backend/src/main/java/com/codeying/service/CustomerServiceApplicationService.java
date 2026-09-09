package com.codeying.service;

import com.codeying.vo.admin.customer_service.CustomerServiceSessionSummaryVO;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import com.codeying.vo.user.customer_service.CustomerServiceSessionVO;

import java.util.List;

/**
 * 人工客服应用层服务。
 *
 * @author Endercloud
 */
public interface CustomerServiceApplicationService {

    /**
     * 获取或创建当前用户的进行中会话。
     *
     * @param userId 用户 ID
     * @return 会话信息
     */
    CustomerServiceSessionVO openSession(Long userId);

    /**
     * 获取进行中会话列表（管理端）。
     *
     * @return 会话列表
     */
    List<CustomerServiceSessionSummaryVO> listOpenSessions();

    /**
     * 查询会话消息列表（游标分页）。
     *
     * @param sessionId 会话 ID
     * @param beforeId  游标（取 id < beforeId）
     * @param limit     条数（默认 50，最大 200）
     * @return 消息列表（按 id 升序）
     */
    List<CustomerServiceMessageVO> listMessages(Long sessionId, Long beforeId, Integer limit);

    /**
     * 关闭会话（管理端）。
     *
     * @param sessionId   会话 ID
     * @param closeReason 关闭原因
     */
    void closeSession(Long sessionId, String closeReason);
}
