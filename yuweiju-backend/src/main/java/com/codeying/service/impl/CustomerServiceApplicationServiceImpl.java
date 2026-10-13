package com.codeying.service.impl;

import com.codeying.exception.BusinessException;
import com.codeying.mapper.CustomerServiceSessionMapper;
import com.codeying.mapper.CustomerServiceMessageMapper;
import org.springframework.transaction.annotation.Transactional;
import com.codeying.entity.CustomerServiceMessage;
import com.codeying.entity.CustomerServiceSession;
import com.codeying.entity.User;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.service.UserService;
import com.codeying.vo.admin.customer_service.CustomerServiceSessionSummaryVO;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import com.codeying.vo.user.customer_service.CustomerServiceSessionVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人工客服应用层服务实现类。
 *
 * @author Endercloud
 */
@Service
public class CustomerServiceApplicationServiceImpl implements CustomerServiceApplicationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final UserService userService;

    public CustomerServiceApplicationServiceImpl(
            CustomerServiceSessionMapper sessionMapper,
            CustomerServiceMessageMapper messageMapper,
            UserService userService
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.userService = userService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomerServiceSessionVO openSession(Long userId) {
        requireIdentity(userId);
        CustomerServiceSession existing = sessionMapper.findLatestOpenByUser(userId);
        if (existing != null) {
            return toSessionVO(existing);
        }

        Date now = new Date();
        CustomerServiceSession session = new CustomerServiceSession();
        session.setUserId(userId);
        session.setStatus(CustomerServiceSession.STATUS_OPEN);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        if (sessionMapper.insert(session) != 1) throw new BusinessException("打开会话失败");
        return toSessionVO(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomerServiceSessionSummaryVO> listOpenSessions() {
        List<CustomerServiceSession> sessions = sessionMapper.findOpenSessions();
        if (sessions == null || sessions.isEmpty()) return Collections.emptyList();

        List<Long> userIds = sessions.stream()
                .map(CustomerServiceSession::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = buildUserMap(userIds);

        List<CustomerServiceSessionSummaryVO> result = new ArrayList<>(sessions.size());
        for (CustomerServiceSession session : sessions) {
            CustomerServiceSessionSummaryVO vo = new CustomerServiceSessionSummaryVO();
            vo.setId(session.getId());
            vo.setUserId(session.getUserId());
            vo.setStatus(session.getStatus());
            vo.setUpdateTime(session.getUpdateTime());
            User user = userMap.get(session.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
                vo.setUserAvatar(user.getAvatar());
            }
            CustomerServiceMessage lastMessage = findLastMessage(session.getId());
            if (lastMessage != null && StringUtils.hasText(lastMessage.getContent())) {
                vo.setLastMessagePreview(limitPreview(lastMessage.getContent()));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomerServiceMessageVO> listAdminMessages(Long sessionId, Long beforeId, Integer limit) {
        if (sessionId == null) return Collections.emptyList();
        int size = normalizeLimit(limit);

        List<CustomerServiceMessage> messages = messageMapper.findHistory(sessionId, beforeId, size);
        if (messages == null || messages.isEmpty()) return Collections.emptyList();

        List<CustomerServiceMessageVO> result = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            result.add(toMessageVO(messages.get(i)));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeSession(Long sessionId, String closeReason) {
        if (sessionId == null) return;
        CustomerServiceSession session = sessionMapper.findByIdForUpdate(sessionId);
        if (session == null || Integer.valueOf(CustomerServiceSession.STATUS_CLOSED).equals(session.getStatus())) return;
        if (sessionMapper.closeOpenSession(sessionId, new Date(),
                StringUtils.hasText(closeReason) ? closeReason.trim() : null) != 1) {
            throw new BusinessException("关闭会话失败");
        }
    }

    @Override
    public List<CustomerServiceMessageVO> listUserMessages(Long userId, Long sessionId, Long beforeId, Integer limit) {
        requireOwner(userId, sessionId == null ? null : sessionMapper.selectById(sessionId));
        return listAdminMessages(sessionId, beforeId, limit);
    }

    @Override
    public void validateUserConnection(Long userId, Long sessionId) {
        CustomerServiceSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        requireOpen(session);
        requireOwner(userId, session);
    }

    @Override
    public void validateAdminSession(Long sessionId) {
        requireOpen(sessionId == null ? null : sessionMapper.selectById(sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDelivery storeUserMessage(Long userId, Long sessionId, String content) {
        return storeMessage(userId, sessionId, content, CustomerServiceMessage.SENDER_USER);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDelivery storeAdminMessage(Long adminId, Long sessionId, String content) {
        return storeMessage(adminId, sessionId, content, CustomerServiceMessage.SENDER_ADMIN);
    }

    private MessageDelivery storeMessage(Long actorId, Long sessionId, String content, int senderType) {
        String normalized = content == null ? null : content.trim();
        if (sessionId == null || !StringUtils.hasText(normalized)) throw new BusinessException("参数错误");
        CustomerServiceSession session = sessionMapper.findByIdForUpdate(sessionId);
        requireOpen(session);
        if (senderType == CustomerServiceMessage.SENDER_USER) requireOwner(actorId, session);
        else requireIdentity(actorId);
        Date now = new Date();
        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(actorId);
        message.setContent(normalized);
        message.setCreateTime(now);
        if (messageMapper.insert(message) != 1) throw new BusinessException("消息发送失败，请重试");
        if (sessionMapper.touchOpenSession(sessionId, now) != 1) throw new BusinessException("会话不存在或已关闭");
        return new MessageDelivery(toMessageVO(message), session.getUserId());
    }

    private void requireIdentity(Long actorId) {
        if (actorId == null) throw new BusinessException("未登录");
    }

    private void requireOwner(Long userId, CustomerServiceSession session) {
        if (userId == null || session == null || !userId.equals(session.getUserId())) throw new BusinessException("无权限");
    }

    private void requireOpen(CustomerServiceSession session) {
        if (session == null || !Integer.valueOf(CustomerServiceSession.STATUS_OPEN).equals(session.getStatus())) {
            throw new BusinessException("会话不存在或已关闭");
        }
    }

    private Map<Long, User> buildUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userService.listByIds(userIds);
        if (users == null || users.isEmpty()) return Collections.emptyMap();
        Map<Long, User> map = new HashMap<>(users.size());
        for (User user : users) {
            if (user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    private CustomerServiceSessionVO toSessionVO(CustomerServiceSession session) {
        if (session == null) return null;
        CustomerServiceSessionVO vo = new CustomerServiceSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setStatus(session.getStatus());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        vo.setCloseTime(session.getCloseTime());
        return vo;
    }

    private CustomerServiceMessageVO toMessageVO(CustomerServiceMessage message) {
        CustomerServiceMessageVO vo = new CustomerServiceMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setSenderType(message.getSenderType());
        vo.setSenderId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private CustomerServiceMessage findLastMessage(Long sessionId) {
        if (sessionId == null) return null;
        return messageMapper.findLastMessage(sessionId);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private String limitPreview(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= 60) return trimmed;
        return trimmed.substring(0, 60) + "...";
    }
}
