import com.codeying.App;
import com.codeying.entity.CustomerServiceMessage;
import com.codeying.mapper.CustomerServiceMessageMapper;
import com.codeying.service.CustomerServiceApplicationService;
import com.codeying.service.WechatService;
import com.codeying.exception.BusinessException;
import com.codeying.websocket.CustomerServiceWebSocketServer;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.support.AopUtils;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Issue15: real Spring proxy, MySQL, protected HTTP and JDK WebSocket; synthetic fixtures only. */
public class Issue15Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18090";
    static final String RUN = "i15_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    static final List<String> results = new ArrayList<>();
    static final List<String> cleanupFailures = new ArrayList<>();
    static final List<Long> ownedSessions = new ArrayList<>();
    static final List<Socket> sockets = new ArrayList<>();
    static final Set<String> triggers = new LinkedHashSet<>();
    static JdbcTemplate jdbc;
    static CustomerServiceApplicationService service;
    static CustomerServiceMessageMapper messageMapper;
    static String admin, userA, userB;
    static final List<String> ownedRedisKeys = new ArrayList<>();
    static org.springframework.data.redis.core.StringRedisTemplate redis;
    static long sessionId;
    static Path out;
    static ConfigurableApplicationContext context;
    static boolean business, isolated, finished;
    static String failure = "";
    static final Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
    static jakarta.websocket.Session failedTarget;
    static final Logger wsLogger = (Logger) LoggerFactory.getLogger(CustomerServiceWebSocketServer.class);
    static final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.add(label); System.out.println("ISSUE15 PASS " + label);
    }
    @Configuration(proxyBeanMethods = false)
    public static class ProbeConfig {
        @Bean @Primary WechatService localWechat() {
            return code -> switch (code) {
                case "issue15-a" -> "sandbox-user-a";
                case "issue15-b" -> "sandbox-user-b";
                default -> throw new IllegalArgumentException("local probe code only");
            };
        }
    }
    static class Socket implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder frame = new StringBuilder();
        WebSocket ws;
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            frame.append(text);
            if (last) {
                try { events.add(JSON.readTree(frame.toString())); }
                catch (Exception ex) { closed.completeExceptionally(new AssertionError("non JSON WS event")); }
                frame.setLength(0);
            }
            socket.request(1); return null;
        }
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { closed.complete(status); return null; }
        public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(new IOException("WS transport failed")); }
        void send(String type, Long id, String content) throws Exception {
            var body = new LinkedHashMap<String, Object>(); body.put("type", type);
            if (id != null) body.put("sessionId", id); if (content != null) body.put("content", content);
            ws.sendText(JSON.writeValueAsString(body), true).get(5, TimeUnit.SECONDS);
        }
        JsonNode event(String type, String label) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                var event = events.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                String actual = event.path("type").asText();
                if (type.equals(actual)) { require(true, label); return event; }
                // A failed-send ERROR must never consume and hide an unexpected delivery.
                if ("CHAT_MESSAGE".equals(actual)) throw new AssertionError(label + " unexpected CHAT_MESSAGE");
            }
            throw new AssertionError(label + " timed out");
        }
        void noChat(String label) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350);
            while (System.nanoTime() < deadline) {
                var event = events.poll(50, TimeUnit.MILLISECONDS);
                if (event != null && "CHAT_MESSAGE".equals(event.path("type").asText())) throw new AssertionError(label + " delivered uncommitted message");
            }
            require(true, label + " no delivery");
        }
        public void close() {
            if (ws != null && !ws.isOutputClosed()) {
                try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "probe finished").get(3, TimeUnit.SECONDS); closed.get(3, TimeUnit.SECONDS); }
                catch (Exception ex) { ws.abort(); cleanupFailures.add("WS graceful close:" + ex.getClass().getSimpleName()); }
            }
        }
    }
    static void checkEventWait() throws Exception {
        var socket = new Socket();
        socket.events.add(JSON.readTree("{\"type\":\"CHAT_MESSAGE\"}"));
        socket.events.add(JSON.readTree("{\"type\":\"ERROR\"}"));
        try {
            socket.event("ERROR", "event wait self-check");
            throw new AssertionError("event wait silently discarded delivery");
        } catch (AssertionError ex) {
            require("event wait self-check unexpected CHAT_MESSAGE".equals(ex.getMessage()), "ERROR wait rejects unexpected CHAT_MESSAGE before ERROR");
        }
    }
    static Socket connect(String type, String token, Long id) throws Exception {
        var listener = new Socket(); sockets.add(listener);
        String uri = "ws://127.0.0.1:18090/ws/customer-service/" + type + "?probe=local";
        if (token != null) uri += "&token=" + URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        if (id != null) uri += "&sessionId=" + id;
        listener.ws = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(uri), listener).get(8, TimeUnit.SECONDS);
        return listener;
    }
    static void refusedSocket(String type, String token, Long id, String label) throws Exception {
        var socket = connect(type, token, id);
        require(socket.closed.get(5, TimeUnit.SECONDS) == 1008, label + " policy close");
    }
    static HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header(path.startsWith("/admin/") ? "token" : "authentication", token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode data(HttpResponse<String> response, String label) throws Exception {
        require(response.statusCode() == 200, label + " HTTP200"); var body = JSON.readTree(response.body());
        require(body.path("code").asInt(-9) == 1 && body.path("success").asBoolean(), label + " business success"); return body.path("data");
    }
    static void denied(HttpResponse<String> response, String label) throws Exception {
        require(response.statusCode() == 401 && JSON.readTree(response.body()).path("code").asInt(-9) == 0, label + " protected HTTP401 code0");
    }
    static void refused(HttpResponse<String> response, String message, String label) throws Exception {
        var body = JSON.readTree(response.body());
        require(response.statusCode() == 200 && body.path("code").asInt(-9) == 0 && !body.path("success").asBoolean(), label + " compatible code0");
        if (message != null) require(message.equals(body.path("msg").asText()) && message.equals(body.path("message").asText()), label + " friendly message");
    }
    static void error(Socket socket, String message, String label) throws Exception {
        var event = socket.event("ERROR", label + " ERROR"); require(message.equals(event.path("message").asText()), label + " friendly message");
    }
    static void directRefusal(String message, Runnable action, String label) {
        try { action.run(); throw new AssertionError(label + " unexpectedly accepted"); }
        catch (BusinessException ex) { require(message.equals(ex.getMessage()), label); }
    }
    static List<Map<String, Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"); }
    static List<Map<String, Object>> ownMessages() { return jdbc.queryForList("SELECT * FROM customer_service_message WHERE session_id=? ORDER BY id", sessionId); }
    static Map<String, Object> ownSession() { return jdbc.queryForMap("SELECT * FROM customer_service_session WHERE id=?", sessionId); }
    static long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM customer_service_message WHERE session_id=?", Long.class, sessionId); }
    static void cleanup(Runnable action, String label) {
        try { action.run(); require(true, label); } catch (Throwable ex) { cleanupFailures.add(label + ":" + ex.getClass().getSimpleName()); }
    }
    static void addTrigger(String table, String action, String predicate) {
        String name = RUN + "_" + action.toLowerCase(Locale.ROOT);
        triggers.add(name); // owned even if CREATE fails, finally checks/removes it
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE " + action + " ON " + table + " FOR EACH ROW BEGIN IF " + predicate + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue15 core write failure'; END IF; END");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?", Long.class, name) == 1L, "task " + action + " trigger installed");
    }
    static void removeTriggers() {
        for (String name : List.copyOf(triggers)) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + name); triggers.remove(name);
            require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?", Long.class, name) == 0L, "task trigger removed");
        }
    }
    static void rollback(Socket sender, Socket receiver, boolean insert) throws Exception {
        sender.noChat("before rollback sender"); receiver.noChat("before rollback receiver");
        var beforeSession = ownSession(); var beforeMessages = ownMessages();
        // These task-owned triggers reject actual candidate INSERT or session UPDATE, without impossible row states.
        addTrigger(insert ? "customer_service_message" : "customer_service_session", insert ? "INSERT" : "UPDATE",
                (insert ? "NEW.session_id=" : "NEW.id=") + sessionId);
        try {
            sender.send("CHAT_SEND", sessionId, "synthetic rollback fixture");
            error(sender, "消息发送失败，请重试", (insert ? "insert" : "session update") + " core failure");
            sender.noChat("failed sender"); receiver.noChat("failed receiver");
            require(beforeSession.equals(ownSession()) && beforeMessages.equals(ownMessages()), (insert ? "insert" : "session update") + " real SQL transaction rolled back both rows");
            try { service.storeUserMessage(900001L, sessionId, "synthetic direct failure"); throw new AssertionError("direct failure unexpectedly accepted"); }
            catch (org.springframework.dao.DataAccessException ex) { require(true, "reused Service propagates actual core SQL failure"); }
            require(beforeSession.equals(ownSession()) && beforeMessages.equals(ownMessages()), "reused Service actual proxy rollback");
        } finally { removeTriggers(); }
    }
    /** Deterministic failing delivery target only; business path still uses real sender WS and real SQL. */
    @SuppressWarnings("unchecked")
    static jakarta.websocket.Session failingTarget(AtomicInteger failures) throws Exception {
        var remote = (jakarta.websocket.RemoteEndpoint.Basic) Proxy.newProxyInstance(Issue15Probe.class.getClassLoader(), new Class<?>[]{jakarta.websocket.RemoteEndpoint.Basic.class}, (proxy, method, args) -> {
            if (method.getName().equals("sendText")) { failures.incrementAndGet(); throw new IOException("synthetic conversation must never be logged"); }
            return null;
        });
        var target = (jakarta.websocket.Session) Proxy.newProxyInstance(Issue15Probe.class.getClassLoader(), new Class<?>[]{jakarta.websocket.Session.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isOpen" -> true; case "getBasicRemote" -> remote;
            case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0];
            default -> null;
        });
        var field = CustomerServiceWebSocketServer.class.getDeclaredField("ADMIN_SESSIONS"); field.setAccessible(true);
        ((Set<jakarta.websocket.Session>) field.get(null)).add(target); return target;
    }
    @SuppressWarnings("unchecked")
    static void removeTarget(jakarta.websocket.Session target) throws Exception {
        if (target == null) return;
        var field = CustomerServiceWebSocketServer.class.getDeclaredField("ADMIN_SESSIONS"); field.setAccessible(true);
        ((Set<jakarta.websocket.Session>) field.get(null)).remove(target);
    }
    static boolean lockOwnedBy(Long adminId) throws Exception {
        var field = CustomerServiceWebSocketServer.class.getDeclaredField("REPLY_LOCKS"); field.setAccessible(true);
        var lock = ((Map<?, ?>) field.get(null)).get(sessionId);
        if (lock == null) return adminId == null;
        if (adminId == null) return false;
        var owner = lock.getClass().getDeclaredField("adminId"); owner.setAccessible(true);
        return adminId.equals(owner.get(lock));
    }
    static long chatPair(Socket sender, Socket receiver, int senderType, String label) throws Exception {
        var a = sender.event("CHAT_MESSAGE", label + " sender CHAT_MESSAGE").path("data");
        var b = receiver.event("CHAT_MESSAGE", label + " receiver CHAT_MESSAGE").path("data");
        require(a.equals(b) && a.path("senderType").asInt() == senderType && a.path("senderId").asLong() == 900001L && a.path("sessionId").asLong() == sessionId, label + " same trusted event");
        long id = a.path("id").asLong(); var persisted = jdbc.queryForMap("SELECT * FROM customer_service_message WHERE id=?", id);
        require(a.path("content").asText().equals(persisted.get("content")) && ((Number) persisted.get("sender_type")).intValue() == senderType, label + " persisted event content and role");
        Object created = persisted.get("create_time"); Object updated = ownSession().get("update_time");
        require(created instanceof LocalDateTime && updated instanceof LocalDateTime && created.equals(updated), label + " actual JDBC DATETIME and matching session time");
        // DATETIME has no zone/fraction in this schema. Use the candidate's actual DateTypeHandler
        // roundtrip instead of assigning a guessed zone to JDBC LocalDateTime; SQL precision is seconds.
        long delta = Instant.parse(a.path("createTime").asText()).toEpochMilli() - messageMapper.selectById(id).getCreateTime().getTime();
        require(Math.abs(delta) <= 1000L, label + " existing JSON timestamp matches actual persisted Date within SQL precision");
        return id;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(); out = root.resolve(".scratch/issue15"); Files.createDirectories(out);
        Path runtime = out.resolve(RUN); Files.createDirectories(runtime); System.setProperty("java.io.tmpdir", runtime.toString());
        Thread mainThread = Thread.currentThread();
        Thread shutdown = new Thread(() -> {
            mainThread.interrupt();
            try { closeResources(); } catch (Exception ex) { System.out.println("ISSUE15 FAIL shutdown cleanup incomplete"); }
        }, "issue15-owned-resource-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdown);
        logs.start(); wsLogger.addAppender(logs);
        try {
            checkEventWait();
            Class.forName("com.mysql.cj.jdbc.Driver"); require(true, "completed package JDBC driver loads");
            context = new SpringApplication(App.class, ProbeConfig.class).run(
                "--spring.config.location=file:" + root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk", "--server.address=127.0.0.1", "--server.port=18090", "--server.tomcat.basedir=" + runtime.resolve("tomcat"),
                "--logging.level.root=OFF", "--logging.level.com.codeying.websocket.CustomerServiceWebSocketServer=WARN",
                "--analysis.task.enabled=false", "--order-risk.task.scoring-enabled=false", "--analysis.xgboost.model-dir=" + runtime.resolve("analysis-models"), "--order-risk.model-dir=" + runtime.resolve("risk-models"));
            jdbc = context.getBean(JdbcTemplate.class); service = context.getBean(CustomerServiceApplicationService.class);
            messageMapper = context.getBean(CustomerServiceMessageMapper.class);
            redis = context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
            require(AopUtils.isAopProxy(service), "real Spring transaction proxy");
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "isolated DB before fixture writes");
            try (var connection = jdbc.getDataSource().getConnection()) { require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"), "isolated JDBC host"); }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host")) && "6379".equals(context.getEnvironment().getProperty("spring.data.redis.port")), "isolated Redis host and port");
            for (String table : List.of("employee", "user", "customer_service_session", "customer_service_message")) {
                String ddl = jdbc.queryForMap("SHOW CREATE TABLE " + table).values().stream().map(Object::toString).filter(v -> v.contains("CREATE TABLE")).findFirst().orElseThrow();
                require(ddl.contains("ENGINE=InnoDB") && ddl.contains("PRIMARY KEY (`id`)"), "actual " + table + " engine and primary key");
                if (table.startsWith("customer_service_")) require(ddl.contains("`id` bigint NOT NULL AUTO_INCREMENT") && ddl.contains("datetime NOT NULL"), "actual " + table + " bigint and DATETIME constraints");
                if (table.endsWith("message")) require(ddl.contains("`content` varchar(2000)") && ddl.contains("(`session_id`,`id`)"), "actual message content and cursor index constraints");
                before.put(table, rows(table));
            }
            require(jdbc.queryForObject("SELECT COUNT(*) FROM customer_service_session WHERE user_id=900001 AND status=1", Long.class) == 0L, "no preexisting synthetic open session to mutate");
            isolated = true;
            var loginAdmin = data(call("POST", "/admin/employee/login", null, Map.of("username", "sandbox_admin", "password", "sandbox-only-login")), "actual fixture admin login"); admin = loginAdmin.path("token").asText();
            var loginA = data(call("POST", "/user/user/login", null, Map.of("code", "issue15-a")), "actual fixture user A login"); userA = loginA.path("token").asText();
            var loginB = data(call("POST", "/user/user/login", null, Map.of("code", "issue15-b")), "actual fixture user B login"); userB = loginB.path("token").asText();
            require(loginAdmin.path("id").asLong() == 900001 && loginA.path("id").asLong() == 900001 && loginB.path("id").asLong() == 900002 && !userA.equals(userB), "two actual distinct fixture user IDs and admin ID");
            String revoke = data(call("POST", "/user/user/login", null, Map.of("code", "issue15-a")), "fresh fixture token for blacklist").path("token").asText();
            String secret = context.getBean(com.codeying.properties.SkyProperties.class).getJwt().getUserSecretKey();
            String jti = com.codeying.utils.JwtUtil.parseClaims(revoke, secret).getId();
            ownedRedisKeys.add(com.codeying.constant.RedisKeys.tokenBlacklistKey(jti));
            context.getBean(com.codeying.security.TokenBlacklistService.class).blacklist(revoke, secret);
            require(context.getBean(com.codeying.security.TokenBlacklistService.class).isBlacklisted(jti), "task fixture blacklist uses real Redis");
            denied(call("POST", "/user/customerService/session/open", revoke, null), "blacklisted protected HTTP");
            refusedSocket("user", revoke, null, "blacklisted user WS");
            // Exactly these paths are included by WebMvcConfiguration, none are login/public excludes.
            for (String path : List.of("/admin/customerService/session/openList", "/admin/customerService/message/list?sessionId=1")) {
                denied(call("GET", path, null, null), "anonymous " + path); denied(call("GET", path, userA, null), "wrong scope " + path);
            }
            for (String token : Arrays.asList(null, userA)) denied(call("POST", "/admin/customerService/session/close", token, Map.of("sessionId", 1)), "protected admin close");
            for (String token : Arrays.asList(null, admin)) {
                denied(call("POST", "/user/customerService/session/open", token, null), "protected user open");
                denied(call("GET", "/user/customerService/message/list?sessionId=1", token, null), "protected user history");
            }
            refusedSocket("admin", userA, null, "user token admin WS"); refusedSocket("user", admin, null, "admin token user WS");
            refusedSocket("user", null, null, "anonymous user WS"); refusedSocket("admin", null, null, "anonymous admin WS");
            var opened = data(call("POST", "/user/customerService/session/open", userA, null), "user opens session"); sessionId = opened.path("id").asLong(); ownedSessions.add(sessionId);
            require(sessionId > 0 && opened.path("userId").asLong() == 900001 && opened.path("status").asInt() == 1, "open ownership and state");
            require(data(call("POST", "/user/customerService/session/open", userA, null), "reopen existing").path("id").asLong() == sessionId, "open reuses session");
            require(data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId, userA, null), "empty history").isEmpty(), "empty list valid");
            require(data(call("GET", "/admin/customerService/session/openList", admin, null), "admin open list").findValues("id").stream().anyMatch(id -> id.asLong() == sessionId), "open session visible in admin summary");
            refused(call("GET", "/user/customerService/message/list?sessionId=" + sessionId, userB, null), "无权限", "other user HTTP history");
            long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1000000 FROM customer_service_session", Long.class);
            refused(call("GET", "/user/customerService/message/list?sessionId=" + absent, userA, null), "无权限", "nonexistent user history");
            require(data(call("GET", "/admin/customerService/message/list?sessionId=" + absent, admin, null), "nonexistent admin history").isEmpty(), "nonexistent admin history remains empty success");
            data(call("POST", "/admin/customerService/session/close", admin, Map.of("sessionId", absent)), "nonexistent close compatible no-op");
            directRefusal("无权限", () -> service.listUserMessages(900002L, sessionId, null, null), "reused Service refuses other history");
            directRefusal("无权限", () -> service.storeUserMessage(900002L, sessionId, "synthetic fixture"), "reused Service refuses other write");
            directRefusal("参数错误", () -> service.storeUserMessage(900001L, sessionId, " \n "), "reused Service refuses blank");
            directRefusal("参数错误", () -> service.storeUserMessage(900001L, sessionId, "\u0000\u001f"), "reused user Service refuses trim-to-empty controls");
            directRefusal("参数错误", () -> service.storeAdminMessage(900001L, sessionId, "\u0000\u001f"), "reused admin Service refuses trim-to-empty controls");
            require(count() == 0L, "invalid reused content has no SQL writes");
            directRefusal("会话不存在或已关闭", () -> service.storeAdminMessage(900001L, absent, "synthetic fixture"), "reused Service nonexistent send");
            refusedSocket("user", userB, sessionId, "other user connection session");
            var a = connect("user", userA, sessionId); var b = connect("admin", admin, null); var other = connect("user", userB, null);
            a.send("PING", null, null); a.event("PONG", "user connection ready"); b.send("PING", null, null); b.event("PONG", "admin connection ready");
            other.send("CHAT_SEND", sessionId, "synthetic foreign fixture"); error(other, "无权限", "foreign WS send"); require(count() == 0L, "foreign WS no SQL insert");
            a.send("CHAT_SEND", absent, "synthetic missing fixture"); error(a, "会话不存在或已关闭", "missing WS send");
            a.send("CHAT_SEND", sessionId, " "); error(a, "参数错误", "blank WS send");
            a.ws.sendText("malformed private fixture", true).get(5, TimeUnit.SECONDS); error(a, "消息格式错误", "invalid WS JSON");
            a.send("TYPING_LOCK", sessionId, null); error(a, "无权限", "user typing forbidden");
            a.send("CHAT_SEND", sessionId, "  synthetic user fixture  "); long first = chatPair(a, b, 1, "user to admin");
            require("synthetic user fixture".equals(jdbc.queryForObject("SELECT content FROM customer_service_message WHERE id=?", String.class, first)), "WS normalization preserved");
            b.send("TYPING_LOCK", sessionId, null); require(b.event("TYPING_LOCKED", "admin typing lock").path("data").path("adminId").asLong() == 900001, "typing trusted admin ID");
            b.send("CHAT_SEND", sessionId, "synthetic admin fixture"); long second = chatPair(b, a, 2, "admin to user");
            b.event("TYPING_UNLOCKED", "successful admin reply releases typing lock");
            require(first < second && count() == 2L, "stable generated message ordering");
            var history = data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId, userA, null), "own persisted history");
            require(history.size() == 2 && history.get(0).path("id").asLong() == first && history.get(1).path("id").asLong() == second, "history ascending IDs");
            require(data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId + "&limit=1", userA, null), "latest limit one").get(0).path("id").asLong() == second, "history newest page limit");
            require(data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId + "&beforeId=" + second + "&limit=1", userA, null), "exclusive cursor page").get(0).path("id").asLong() == first, "cursor strictly less and stable");
            require(data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId + "&beforeId=0", userA, null), "empty cursor").isEmpty(), "empty cursor success");
            refused(call("GET", "/user/customerService/message/list?sessionId=" + sessionId + "&limit=0", userA, null), null, "invalid HTTP zero limit");
            refused(call("GET", "/admin/customerService/message/list?sessionId=" + sessionId + "&limit=201", admin, null), null, "invalid HTTP excessive limit");
            refused(call("GET", "/user/customerService/message/list", userA, null), null, "missing HTTP session ID");
            a.close(); a = connect("user", userA, sessionId); a.send("PING", null, null); a.event("PONG", "user reconnected");
            require(history.equals(data(call("GET", "/user/customerService/message/list?sessionId=" + sessionId, userA, null), "reconnect history")), "reconnect keeps persisted history");
            rollback(a, b, true);
            b.send("TYPING_LOCK", sessionId, null); b.event("TYPING_LOCKED", "lock before failing admin core");
            rollback(b, a, false);
            require(lockOwnedBy(900001L), "failed SQL retains transport typing owner lock");
            // Failed SQL retains ephemeral owner lock; retry success releases it, independent of SQL rollback.
            b.send("CHAT_SEND", sessionId, "synthetic retry fixture"); chatPair(b, a, 2, "retry after rollback"); b.event("TYPING_UNLOCKED", "retry releases retained lock");
            require(lockOwnedBy(null), "successful retry removes transient lock");
            b.send("TYPING_LOCK", sessionId, null); b.event("TYPING_LOCKED", "explicit lock"); b.send("TYPING_UNLOCK", sessionId, null); b.event("TYPING_UNLOCKED", "explicit unlock");
            b.send("TYPING_LOCK", sessionId, null); b.event("TYPING_LOCKED", "disconnect lock"); b.close();
            require(lockOwnedBy(null), "admin disconnect removes transient lock");
            b = connect("admin", admin, null); b.send("PING", null, null); b.event("PONG", "admin reconnected");
            var failedWrites = new AtomicInteger(); failedTarget = failingTarget(failedWrites); long priorCount = count();
            b.send("CHAT_SEND", sessionId, "synthetic transport fixture"); long committed = chatPair(b, a, 2, "commit despite failing delivery target");
            require(failedWrites.get() > 0 && count() == priorCount + 1 && jdbc.queryForObject("SELECT COUNT(*) FROM customer_service_message WHERE id=?", Long.class, committed) == 1L, "core commit survives deterministic transport IOException");
            removeTarget(failedTarget); failedTarget = null;
            require(logs.list.stream().allMatch(event -> event.getThrowableProxy() == null && !event.getFormattedMessage().contains("synthetic") && !event.getFormattedMessage().contains(userA) && !event.getFormattedMessage().contains(admin)), "WS logs conceal malformed body tokens SQL content and delivery exception details");
            data(call("POST", "/admin/customerService/session/close", admin, Map.of("sessionId", sessionId, "closeReason", " done ")), "close session");
            require(((Number) ownSession().get("status")).intValue() == 2 && "done".equals(ownSession().get("close_reason")), "closed persisted and reason trimmed");
            long closedCount = count();
            a.send("CHAT_SEND", sessionId, "synthetic closed fixture"); error(a, "会话不存在或已关闭", "closed user WS send");
            b.send("CHAT_SEND", sessionId, "synthetic closed fixture"); error(b, "会话不存在或已关闭", "closed admin WS send");
            require(count() == closedCount, "closed WS no new messages");
            directRefusal("会话不存在或已关闭", () -> service.storeUserMessage(900001L, sessionId, "synthetic closed fixture"), "reused Service closed user send");
            directRefusal("会话不存在或已关闭", () -> service.storeAdminMessage(900001L, sessionId, "synthetic closed fixture"), "reused Service closed admin send");
            require(service.listUserMessages(900001L, sessionId, null, null).size() == closedCount, "closed own history readable");
            data(call("POST", "/admin/customerService/session/close", admin, Map.of("sessionId", sessionId)), "close idempotent");
            refusedSocket("user", userA, sessionId, "closed reconnect rejected");
            var next = data(call("POST", "/user/customerService/session/open", userA, null), "open after close"); long nextId = next.path("id").asLong(); ownedSessions.add(nextId);
            require(nextId != sessionId && next.path("status").asInt() == 1, "new session after close");
            // Real reusable writes characterize normalized defaults and capped history without transport spam.
            for (int i = 0; i < 201; i++) service.storeUserMessage(900001L, nextId, "synthetic paging fixture");
            require(service.listUserMessages(900001L, nextId, null, 0).size() == 50 && service.listUserMessages(900001L, nextId, null, -1).size() == 50 && service.listUserMessages(900001L, nextId, null, 999).size() == 200, "direct reuse default negative zero and maximum history limits");
            require(data(call("GET", "/user/customerService/message/list?sessionId=" + nextId + "&limit=200", userA, null), "real maximum history").size() == 200, "HTTP maximum history page");
            business = true;
        } catch (Throwable ex) {
            failure = ex.getClass().getSimpleName(); System.out.println("ISSUE15 FAIL " + failure + (ex instanceof AssertionError ? " " + ex.getMessage() : ""));
        } finally {
            closeResources();
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
        if (!business || !cleanupFailures.isEmpty()) throw new AssertionError("Issue15 incomplete; sanitized evidence retained");
        System.out.println("ISSUE15 ACCEPTANCE PASS");
    }
    static synchronized void closeResources() throws Exception {
        if (finished) return;
            final var target = failedTarget; cleanup(() -> { try { removeTarget(target); } catch (Exception ex) { throw new IllegalStateException(ex); } }, "failing transport target removed");
            for (var socket : sockets) cleanup(socket::close, "owned WS closed");
            if (isolated && jdbc != null) {
                cleanup(Issue15Probe::removeTriggers, "owned triggers cleanup");
                for (long id : ownedSessions) cleanup(() -> { jdbc.update("DELETE FROM customer_service_message WHERE session_id=?", id); jdbc.update("DELETE FROM customer_service_session WHERE id=?", id); }, "owned synthetic session and messages removed");
                for (var entry : before.entrySet()) cleanup(() -> { if (!entry.getValue().equals(rows(entry.getKey()))) throw new AssertionError("shared rows changed"); }, "shared " + entry.getKey() + " rows unchanged");
            }
            if (isolated && redis != null) for (String key : List.copyOf(ownedRedisKeys)) cleanup(() -> {
                redis.delete(key);
                if (Boolean.TRUE.equals(redis.hasKey(key))) throw new AssertionError("owned blacklist key remains");
                ownedRedisKeys.remove(key);
            }, "owned fixture blacklist key removed");
            wsLogger.detachAppender(logs); logs.stop();
            if (context != null) { final var ownedContext = context; cleanup(() -> { ownedContext.close(); if (ownedContext.isActive()) throw new AssertionError("context still active"); }, "Spring HTTP WS context stopped"); }
            cleanup(HTTP::close, "owned HTTP client stopped");
            boolean stopped = context == null || !context.isActive();
            JSON.writeValue(out.resolve("owned-resources.json").toFile(), Map.of("run", RUN, "syntheticSessions", ownedSessions, "contextStopped", stopped, "webSocketCount", sockets.size(), "remainingTaskTriggers", triggers.size(), "remainingTaskRedisKeys", ownedRedisKeys.size(), "syntheticRowsRemoved", cleanupFailures.isEmpty(), "externalServicesUsed", false));
            JSON.writeValue(out.resolve("scenario-results.json").toFile(), Map.of("businessPassed", business, "cleanupPassed", cleanupFailures.isEmpty() && stopped, "failureClass", failure, "scenarios", results, "cleanupFailures", cleanupFailures,
                "limits", List.of("transport IOException uses a deterministic failing target alongside real JDK WebSockets", "not all multi-admin or multi-node races covered", "real WeChat and official browser acceptance are host-owned")));
        finished = true;
    }

}
