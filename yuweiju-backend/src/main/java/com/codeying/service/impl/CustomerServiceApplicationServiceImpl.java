package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.CustomerServiceMessage;
import com.codeying.entity.CustomerServiceSession;
import com.codeying.entity.User;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.service.CustomerServiceMessageService;
import com.codeying.service.CustomerServiceSessionService;
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

    private final CustomerServiceSessionService sessionService;
    private final CustomerServiceMessageService messageService;
    private final UserService userService;

    public CustomerServiceApplicationServiceImpl(
            CustomerServiceSessionService sessionService,
            CustomerServiceMessageService messageService,
            UserService userService
    ) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.userService = userService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomerServiceSessionVO openSession(Long userId) {
        if (userId == null) return null;
        CustomerServiceSession existing = sessionService.getOne(new QueryWrapper<CustomerServiceSession>()
                .eq("user_id", userId)
                .eq("status", CustomerServiceSession.STATUS_OPEN)
                .orderByDesc("update_time")
                .last("limit 1"));
        if (existing != null) {
            return toSessionVO(existing);
        }

        Date now = new Date();
        CustomerServiceSession session = new CustomerServiceSession();
        session.setUserId(userId);
        session.setStatus(CustomerServiceSession.STATUS_OPEN);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionService.save(session);
        return toSessionVO(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomerServiceSessionSummaryVO> listOpenSessions() {
        List<CustomerServiceSession> sessions = sessionService.list(new QueryWrapper<CustomerServiceSession>()
                .eq("status", CustomerServiceSession.STATUS_OPEN)
                .orderByDesc("update_time"));
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
    public List<CustomerServiceMessageVO> listMessages(Long sessionId, Long beforeId, Integer limit) {
        if (sessionId == null) return Collections.emptyList();
        int size = normalizeLimit(limit);

        QueryWrapper<CustomerServiceMessage> wrapper = new QueryWrapper<CustomerServiceMessage>()
                .eq("session_id", sessionId);
        if (beforeId != null) {
            wrapper.lt("id", beforeId);
        }
        wrapper.orderByDesc("id").last("limit " + size);

        List<CustomerServiceMessage> messages = messageService.list(wrapper);
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
    public void closeSession(Long sessionId, String closeReason) {
        if (sessionId == null) return;
        CustomerServiceSession session = sessionService.getById(sessionId);
        if (session == null) return;
        if (CustomerServiceSession.STATUS_CLOSED == session.getStatus()) return;

        CustomerServiceSession update = new CustomerServiceSession();
        update.setId(sessionId);
        update.setStatus(CustomerServiceSession.STATUS_CLOSED);
        update.setCloseTime(new Date());
        update.setUpdateTime(new Date());
        if (StringUtils.hasText(closeReason)) {
            update.setCloseReason(closeReason.trim());
        }
        sessionService.updateById(update);
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
        return messageService.getOne(new QueryWrapper<CustomerServiceMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("limit 1"));
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
