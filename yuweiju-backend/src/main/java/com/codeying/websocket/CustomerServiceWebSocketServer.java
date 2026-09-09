package com.codeying.websocket;

import com.codeying.config.SpringContextHolder;
import com.codeying.entity.CustomerServiceMessage;
import com.codeying.entity.CustomerServiceSession;
import com.codeying.properties.SkyProperties;
import com.codeying.security.TokenBlacklistService;
import com.codeying.service.CustomerServiceMessageService;
import com.codeying.service.CustomerServiceSessionService;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.common.customer_service.CustomerServiceMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工客服 WebSocket 服务端。
 *
 * @author Endercloud
 */
@Slf4j
@Component
@ServerEndpoint(value = "/ws/customer-service/{clientType}")
public class CustomerServiceWebSocketServer {

    private static final long LOCK_TIMEOUT_MS = 30_000L;
/**
 * ConcurrentHashMap.newKeySet
 * @return 
 */

    private static final Set<Session> ADMIN_SESSIONS = ConcurrentHashMap.newKeySet();
/**
 * ConcurrentHashMap<>
 * @return 
 */
    private static final ConcurrentHashMap<Long, Session> USER_SESSIONS = new ConcurrentHashMap<>();
/**
 * ConcurrentHashMap<>
 * @return 
 */
    private static final ConcurrentHashMap<Long, ReplyLock> REPLY_LOCKS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("clientType") String clientType) {
        if (!"admin".equals(clientType) && !"user".equals(clientType)) {
            close(session, "非法客户端");
            return;
        }

        if (!authenticate(session, clientType)) return;

        Map<String, Object> props = session.getUserProperties();
        Long uid = asLong(props.get("uid"));

        if ("admin".equals(clientType)) {
            ADMIN_SESSIONS.add(session);
            return;
        }

        USER_SESSIONS.put(uid, session);
        Long sessionId = asLong(props.get("sessionId"));
        if (sessionId != null && !isUserSession(uid, sessionId)) {
            close(session, "会话无权限");
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("clientType") String clientType) {
        Map<String, Object> props = session.getUserProperties();
        Long uid = asLong(props.get("uid"));
        if ("admin".equals(clientType)) {
            ADMIN_SESSIONS.remove(session);
            releaseLocksByAdmin(uid);
            return;
        }
        if (uid != null) {
            Session existing = USER_SESSIONS.get(uid);
            if (existing == session) {
                USER_SESSIONS.remove(uid);
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("clientType") String clientType) {
        InboundMessage inbound = parseInbound(message);
        if (inbound == null || !StringUtils.hasText(inbound.type)) {
            sendError(session, "消息格式错误");
            return;
        }

        if ("PING".equalsIgnoreCase(inbound.type)) {
            send(session, new OutboundMessage("PONG", null, null));
            return;
        }

        if ("CHAT_SEND".equalsIgnoreCase(inbound.type)) {
            handleChatSend(session, clientType, inbound);
            return;
        }

        if ("TYPING_LOCK".equalsIgnoreCase(inbound.type)) {
            handleTypingLock(session, clientType, inbound);
            return;
        }

        if ("TYPING_UNLOCK".equalsIgnoreCase(inbound.type)) {
            handleTypingUnlock(session, clientType, inbound);
            return;
        }

        sendError(session, "不支持的消息类型");
    }

    public static void releaseExpiredLocks() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, ReplyLock> entry : REPLY_LOCKS.entrySet()) {
            ReplyLock lock = entry.getValue();
            if (lock != null && lock.expiresAt <= now) {
                if (REPLY_LOCKS.remove(entry.getKey(), lock)) {
                    broadcastToAdmins(new OutboundMessage("TYPING_UNLOCKED",
                            Map.of("sessionId", entry.getKey()), null));
                }
            }
        }
    }

    private void handleChatSend(Session session, String clientType, InboundMessage inbound) {
        Long sessionId = inbound.sessionId;
        String content = StringUtils.hasText(inbound.content) ? inbound.content.trim() : null;
        if (sessionId == null || !StringUtils.hasText(content)) {
            sendError(session, "参数错误");
            return;
        }

        CustomerServiceSessionService sessionService = SpringContextHolder.getBean(CustomerServiceSessionService.class);
        CustomerServiceMessageService messageService = SpringContextHolder.getBean(CustomerServiceMessageService.class);

        CustomerServiceSession chatSession = sessionService.getById(sessionId);
        if (chatSession == null || chatSession.getStatus() == null || chatSession.getStatus() != CustomerServiceSession.STATUS_OPEN) {
            sendError(session, "会话不存在或已关闭");
            return;
        }

        Long uid = asLong(session.getUserProperties().get("uid"));
        if ("user".equals(clientType)) {
            if (uid == null || !uid.equals(chatSession.getUserId())) {
                sendError(session, "无权限");
                return;
            }
        }

        if ("admin".equals(clientType) && !canSendAsAdmin(uid, sessionId)) {
            sendError(session, "当前会话已有其他客服在回复");
            return;
        }

        Date now = new Date();
        CustomerServiceMessage entity = new CustomerServiceMessage();
        entity.setSessionId(sessionId);
        entity.setSenderType("admin".equals(clientType)
                ? CustomerServiceMessage.SENDER_ADMIN
                : CustomerServiceMessage.SENDER_USER);
        entity.setSenderId(uid);
        entity.setContent(content);
        entity.setCreateTime(now);
        messageService.save(entity);

        CustomerServiceSession update = new CustomerServiceSession();
        update.setId(sessionId);
        update.setUpdateTime(now);
        sessionService.updateById(update);

        CustomerServiceMessageVO vo = toMessageVO(entity);
        OutboundMessage outbound = new OutboundMessage("CHAT_MESSAGE", vo, null);
        broadcastToAdmins(outbound);
        sendToUser(chatSession.getUserId(), outbound);

        if ("admin".equals(clientType)) {
            unlockIfOwner(uid, sessionId);
        }
    }

    private void handleTypingLock(Session session, String clientType, InboundMessage inbound) {
        if (!"admin".equals(clientType)) {
            sendError(session, "无权限");
            return;
        }
        Long sessionId = inbound.sessionId;
        if (sessionId == null) {
            sendError(session, "参数错误");
            return;
        }

        Long adminId = asLong(session.getUserProperties().get("uid"));
        if (adminId == null) {
            sendError(session, "未登录");
            return;
        }
        String adminName = asString(session.getUserProperties().get("username"));
        ReplyLock existing = REPLY_LOCKS.get(sessionId);
        long now = System.currentTimeMillis();

        if (existing != null && existing.expiresAt > now && !existing.adminId.equals(adminId)) {
            sendError(session, "当前会话已有其他客服在回复");
            return;
        }

        ReplyLock lock = new ReplyLock(adminId, adminName, now + LOCK_TIMEOUT_MS);
        REPLY_LOCKS.put(sessionId, lock);
        broadcastToAdmins(new OutboundMessage("TYPING_LOCKED", buildLockPayload(sessionId, lock), null));
    }

    private void handleTypingUnlock(Session session, String clientType, InboundMessage inbound) {
        if (!"admin".equals(clientType)) {
            sendError(session, "无权限");
            return;
        }
        Long sessionId = inbound.sessionId;
        Long adminId = asLong(session.getUserProperties().get("uid"));
        if (sessionId == null || adminId == null) {
            sendError(session, "参数错误");
            return;
        }

        ReplyLock existing = REPLY_LOCKS.get(sessionId);
        if (existing == null || !adminId.equals(existing.adminId)) {
            return;
        }
        REPLY_LOCKS.remove(sessionId, existing);
        broadcastToAdmins(new OutboundMessage("TYPING_UNLOCKED", Map.of("sessionId", sessionId), null));
    }

    private boolean canSendAsAdmin(Long adminId, Long sessionId) {
        if (adminId == null || sessionId == null) return false;
        ReplyLock lock = REPLY_LOCKS.get(sessionId);
        if (lock == null) return true;
        if (lock.expiresAt <= System.currentTimeMillis()) {
            REPLY_LOCKS.remove(sessionId, lock);
            return true;
        }
        return adminId.equals(lock.adminId);
    }

    private void unlockIfOwner(Long adminId, Long sessionId) {
        if (adminId == null || sessionId == null) return;
        ReplyLock lock = REPLY_LOCKS.get(sessionId);
        if (lock == null || !adminId.equals(lock.adminId)) return;
        REPLY_LOCKS.remove(sessionId, lock);
        broadcastToAdmins(new OutboundMessage("TYPING_UNLOCKED", Map.of("sessionId", sessionId), null));
    }

    private void releaseLocksByAdmin(Long adminId) {
        if (adminId == null) return;
        List<Long> released = new ArrayList<>();
        for (Map.Entry<Long, ReplyLock> entry : REPLY_LOCKS.entrySet()) {
            ReplyLock lock = entry.getValue();
            if (lock != null && adminId.equals(lock.adminId)) {
                if (REPLY_LOCKS.remove(entry.getKey(), lock)) {
                    released.add(entry.getKey());
                }
            }
        }
        for (Long sessionId : released) {
            broadcastToAdmins(new OutboundMessage("TYPING_UNLOCKED", Map.of("sessionId", sessionId), null));
        }
    }

    private Map<String, Object> buildLockPayload(Long sessionId, ReplyLock lock) {
        Map<String, Object> payload = new ConcurrentHashMap<>(4);
        payload.put("sessionId", sessionId);
        payload.put("adminId", lock.adminId);
        payload.put("adminName", lock.adminName == null ? "" : lock.adminName);
        payload.put("expiresAt", lock.expiresAt);
        return payload;
    }

    private void sendToUser(Long userId, OutboundMessage payload) {
        if (userId == null) return;
        Session userSession = USER_SESSIONS.get(userId);
        if (userSession != null && userSession.isOpen()) {
            send(userSession, payload);
        }
    }

    private static void broadcastToAdmins(OutboundMessage payload) {
        for (Session session : ADMIN_SESSIONS) {
            if (session != null && session.isOpen()) {
                sendStatic(session, payload);
            }
        }
    }

    private boolean isUserSession(Long userId, Long sessionId) {
        CustomerServiceSessionService sessionService = SpringContextHolder.getBean(CustomerServiceSessionService.class);
        CustomerServiceSession session = sessionService.getById(sessionId);
        if (session == null) return false;
        if (!userId.equals(session.getUserId())) return false;
        return session.getStatus() != null && session.getStatus() == CustomerServiceSession.STATUS_OPEN;
    }

    private InboundMessage parseInbound(String message) {
        if (!StringUtils.hasText(message)) return null;
        try {
            return mapper().readValue(message, InboundMessage.class);
        } catch (Exception e) {
            log.warn("解析 WebSocket 消息失败: {}", e.getMessage());
            return null;
        }
    }

    private void sendError(Session session, String message) {
        send(session, new OutboundMessage("ERROR", null, message));
    }

    private void send(Session session, OutboundMessage payload) {
        sendStatic(session, payload);
    }

    private static void sendStatic(Session session, OutboundMessage payload) {
        if (session == null || payload == null) return;
        try {
            ObjectMapper mapper = SpringContextHolder.getBean(ObjectMapper.class);
            session.getBasicRemote().sendText(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("发送 WebSocket 消息失败: {}", e.getMessage(), e);
        }
    }

    private void close(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception e) {
            log.warn("关闭 WebSocket 连接失败: {}", e.getMessage());
        }
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

    private ObjectMapper mapper() {
        return SpringContextHolder.getBean(ObjectMapper.class);
    }

    private boolean authenticate(Session session, String clientType) {
        Map<String, Object> props = session.getUserProperties();
        String token = getQueryParam(session, "token");
        if (!StringUtils.hasText(token)) {
            close(session, "未登录");
            return false;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }

        SkyProperties skyProperties = SpringContextHolder.getBean(SkyProperties.class);
        TokenBlacklistService tokenBlacklistService = SpringContextHolder.getBean(TokenBlacklistService.class);
        String secret = "admin".equals(clientType)
                ? skyProperties.getJwt().getAdminSecretKey()
                : skyProperties.getJwt().getUserSecretKey();

        Claims claims;
        try {
            claims = JwtUtil.parseClaims(token, secret);
        } catch (JwtException e) {
            close(session, "登录已失效");
            return false;
        }

        String jti = claims.getId();
        if (tokenBlacklistService.isBlacklisted(jti)) {
            close(session, "登录已失效");
            return false;
        }

        Long uid = claims.get("uid", Long.class);
        if (uid == null) {
            close(session, "登录已失效");
            return false;
        }

        String username = claims.get("username", String.class);
        Long sessionId = parseLong(getQueryParam(session, "sessionId"));

        props.put("uid", uid);
        props.put("clientType", clientType);
        props.put("username", username);
        if (sessionId != null) {
            props.put("sessionId", sessionId);
        }
        return true;
    }

    private String getQueryParam(Session session, String key) {
        if (session == null || !StringUtils.hasText(key)) return null;
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params != null) {
            List<String> values = params.get(key);
            if (values != null && !values.isEmpty() && StringUtils.hasText(values.get(0))) {
                return values.get(0);
            }
        }
        URI uri = session.getRequestURI();
        if (uri != null && StringUtils.hasText(uri.getQuery())) {
            String[] pairs = uri.getQuery().split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && key.equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long asLong(Object raw) {
        return raw instanceof Long ? (Long) raw : null;
    }

    private String asString(Object raw) {
        return raw instanceof String ? (String) raw : null;
    }

    private static class InboundMessage {
        public String type;
        public Long sessionId;
        public String content;
    }

    private static class OutboundMessage {
        public String type;
        public Object data;
        public String message;

        public OutboundMessage(String type, Object data, String message) {
            this.type = type;
            this.data = data;
            this.message = message;
        }
    }

    private static class ReplyLock {
        public Long adminId;
        public String adminName;
        public long expiresAt;

        public ReplyLock(Long adminId, String adminName, long expiresAt) {
            this.adminId = adminId;
            this.adminName = adminName;
            this.expiresAt = expiresAt;
        }
    }
}
