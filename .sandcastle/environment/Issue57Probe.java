import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.exception.BusinessException;
import com.codeying.properties.SkyProperties;
import com.codeying.service.OrdersApplicationService;
import com.codeying.service.ShopStatusService;
import com.codeying.service.impl.ShopStatusServiceImpl;
import com.codeying.utils.BaiduMapUtil;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Task-owned prod HTTP -> real Spring transaction -> MyBatis/MySQL; real Redis status, no external IO. */
public class Issue57Probe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String RUN = "I57-" + UUID.randomUUID().toString().substring(0, 12);
    static final List<String> checks = new ArrayList<>();
    static final Map<String, List<String>> primaryKeys = new TreeMap<>();
    static final AtomicInteger coreWrites = new AtomicInteger(), mapCalls = new AtomicInteger();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static long owner, other, dish, oldOrder, cancelPending, cancelPaid, adminCancel, foreignOrder;
    static String base, token, otherToken, adminToken;
    static boolean businessPassed, cleanupPassed, springClosed, httpClosed;
    static String failure;

    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks.add(name);
        System.out.println("ISSUE57 PASS " + name);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Fixtures {
        @Bean CoreWrites coreWrites() { return new CoreWrites(); }
        @Bean @Primary BaiduMapUtil isolatedMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop, String address, String unused) {
                    check("explicit_local_map_seam_only_synthetic_address", "Isolation street".equals(shop) && address.contains(RUN));
                    mapCalls.incrementAndGet();
                    return 7;
                }
            };
        }
        @Bean static BeanFactoryPostProcessor disableJobs() {
            return factory -> {
                var registry = (BeanDefinitionRegistry) factory;
                String scheduler = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(scheduler)) registry.removeBeanDefinition(scheduler);
            };
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    public static class CoreWrites implements Interceptor {
        @Override public Object intercept(Invocation invocation) throws Throwable {
            String id = ((MappedStatement) invocation.getArgs()[0]).getId();
            if (id.startsWith("com.codeying.mapper.OrdersMapper.")
                    || id.startsWith("com.codeying.mapper.OrderDetailMapper.")
                    || id.startsWith("com.codeying.mapper.ShoppingCartMapper.")) {
                check("real_core_write_inside_transaction", TransactionSynchronizationManager.isActualTransactionActive());
                coreWrites.incrementAndGet();
            }
            return invocation.proceed();
        }
    }

    static int count(String query, Object... args) { return sql.queryForObject(query, Integer.class, args); }
    static String identifier(String name) {
        if (!name.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("unexpected metadata identifier");
        return "`" + name + "`";
    }
    static void inspectSchema(Path run) throws Exception {
        var schema = new TreeMap<String, Object>();
        // Inspect actual keys/columns/indexes/constraints before any fixture write. Values are always bound.
        var tables = sql.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name", String.class);
        for (String table : tables) {
            var keys = sql.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position", String.class, table);
            check("actual_primary_key_present_" + table, !keys.isEmpty());
            primaryKeys.put(table, keys);
            var columns = sql.queryForList("SELECT column_name,is_nullable,column_default,column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position", table);
            var constraints = sql.queryForList("SELECT constraint_name,constraint_type FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name=? ORDER BY constraint_name", table);
            var indexes = sql.queryForList("SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? ORDER BY index_name,seq_in_index", table);
            schema.put(table, Map.of("primaryKey", keys, "columns", columns, "constraints", constraints, "indexes", indexes));
        }
        for (String table : List.of("user", "address_book", "dish", "orders", "order_detail", "shopping_cart")) {
            check("expected_actual_id_key_" + table, List.of("id").equals(primaryKeys.get(table)));
            check("transactional_engine_" + table, "InnoDB".equals(sql.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", String.class, table)));
        }
        check("actual_detail_amount_nonnull_decimal", count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_detail' AND column_name='amount' AND is_nullable='NO' AND data_type='decimal'") == 1);
        check("actual_dish_unique_name", count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dish' AND column_name='name' AND non_unique=0") > 0);
        Files.writeString(run.resolve("schema-inspection.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }
    static Map<String, String> snapshot() throws Exception {
        var result = new TreeMap<String, String>();
        for (var entry : primaryKeys.entrySet()) {
            String order = String.join(",", entry.getValue().stream().map(Issue57Probe::identifier).toList());
            result.put(entry.getKey(), JSON.writeValueAsString(sql.queryForList("SELECT * FROM " + identifier(entry.getKey()) + " ORDER BY " + order)));
        }
        return result;
    }
    static List<Map<String, Object>> carts(long user) {
        return sql.queryForList("SELECT * FROM shopping_cart WHERE user_id=? ORDER BY id", user);
    }
    record RedisValue(byte[] dump, long expiresAt) {
        static RedisValue read(String key) {
            byte[] dump = redis.dump(key);
            Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
            return new RedisValue(dump, ttl != null && ttl >= 0 ? System.currentTimeMillis() + ttl : -1);
        }
        boolean matches(String key) {
            RedisValue now = read(key);
            if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) return now.dump == null;
            return Arrays.equals(dump, now.dump) && (expiresAt == -1 ? now.expiresAt == -1 : Math.abs(expiresAt - now.expiresAt) < 2000);
        }
        void restore(String key) {
            // Restore only the status key whose exclusive temporary use this probe owns.
            redis.delete(key);
            if (dump != null && (expiresAt == -1 || expiresAt > System.currentTimeMillis())) {
                redis.restore(key, dump, expiresAt == -1 ? 0 : Math.max(1, expiresAt - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            }
        }
    }
    static Map<String, RedisValue> unrelatedRedis() {
        var values = new TreeMap<String, RedisValue>();
        for (String key : Objects.requireNonNull(redis.keys("*"))) {
            if (!key.equals(RedisKeys.shopStatusKey())) values.put(key, RedisValue.read(key));
        }
        return values;
    }
    static boolean sameUnrelatedRedis(Map<String, RedisValue> original) {
        var current = new TreeSet<>(Objects.requireNonNull(redis.keys("*")));
        current.remove(RedisKeys.shopStatusKey());
        var expected = new TreeSet<String>();
        original.forEach((key, value) -> { if (value.expiresAt == -1 || value.expiresAt > System.currentTimeMillis()) expected.add(key); });
        return expected.equals(current) && original.entrySet().stream().allMatch(entry -> entry.getValue().matches(entry.getKey()));
    }
    static JsonNode request(HttpClient client, String method, String path, String auth, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
        if (auth != null) builder.header(path.startsWith("/admin/") ? "token" : "authentication", auth);
        if (body != null) builder.header("Content-Type", "application/json");
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body());
    }
    static boolean success(JsonNode response) { return response.path("code").asInt(-1) == 1; }
    static Map<String, Object> submitBody() {
        return Map.of("addressBookId", owner, "amount", "0.01", "packAmount", -99, "payMethod", 1,
                "remark", "Issue57 retained form", "tablewareStatus", 0, "tablewareNumber", 3);
    }
    static JsonNode submit(HttpClient client, String auth) throws Exception {
        return request(client, "POST", "/user/order/submit", auth, submitBody());
    }
    static Map<String, Object> paymentBody(long order) {
        return Map.of("orderNumber", RUN + "-" + (order - oldOrder), "payMethod", 1);
    }
    static void seed() {
        sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,?),(?,?,?)", owner, RUN + "-a", RUN + "-a", other, RUN + "-b", RUN + "-b");
        sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue57 A','00000000000',?,1),(?,?,'Issue57 B','00000000001',?,1)", owner, owner, RUN + " Isolation street", other, other, RUN + " Other street");
        sql.update("INSERT INTO dish(id,name,category_id,price,image,status) VALUES(?,?,900001,18.00,'/static/issue57.png',1)", dish, RUN);
        var created = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusMinutes(2).withNano(0);
        for (long order : List.of(oldOrder, cancelPending, cancelPaid, adminCancel, foreignOrder)) {
            long user = order == foreignOrder ? other : owner;
            int status = order == cancelPaid ? 2 : order == adminCancel ? 3 : 1;
            sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,amount,pack_amount,estimated_delivery_time) VALUES(?,?,?,?,?,?,?,40.00,2,?)",
                    order, RUN + "-" + (order - oldOrder), status, user, user, created, status == 1 ? 0 : 1, created.plusMinutes(17));
            sql.update("INSERT INTO order_detail(id,name,image,order_id,dish_id,dish_flavor,number,amount) VALUES(?,'Issue57 historical','/static/old57.png',?,?,'mild',2,18.00)", order, order, dish);
        }
        sql.update("INSERT INTO shopping_cart(id,name,image,user_id,dish_id,dish_flavor,number,amount,create_time) VALUES(?,'Issue57 cart','/static/cart57.png',?,?,'mild',2,0.01,NOW()),(?,'Issue57 other',NULL,?,?,NULL,1,18.00,NOW())", owner, owner, dish, other, other, dish);
    }
    static void cleanup() {
        // Freshness checks cover all cleanup scopes before seed; never delete historical fixed IDs.
        sql.update("DELETE d FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM orders WHERE user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM shopping_cart WHERE user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM address_book WHERE (user_id=? AND id=?) OR (user_id=? AND id=?)", owner, owner, other, other);
        sql.update("DELETE FROM dish WHERE id=?", dish);
        sql.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }
    static void refusal(HttpClient client, String name, String message) throws Exception {
        var before = snapshot(); var cart = carts(owner); int writes = coreWrites.get(), maps = mapCalls.get();
        var response = submit(client, token);
        check(name + "_understandable_actual_business_response", !success(response) && response.path("success").isBoolean()
                && !response.path("success").asBoolean() && message.equals(response.path("message").asText()) && message.equals(response.path("msg").asText()));
        check(name + "_zero_core_write_attempts", writes == coreWrites.get());
        check(name + "_full_sorted_sql_snapshot_unchanged", before.equals(snapshot()));
        check(name + "_exact_old_cart_retained", cart.equals(carts(owner)) && cart.size() == 1);
        check(name + "_no_map_call", maps == mapCalls.get());
    }
    static void oldOrders(HttpClient client) throws Exception {
        var before = snapshot();
        var detail = request(client,"GET","/user/order/orderDetail/"+oldOrder,token,null);
        var storedTime = sql.queryForObject("SELECT order_time FROM orders WHERE id=?",java.time.LocalDateTime.class,oldOrder);
        var apiTime = java.time.LocalDateTime.parse(detail.path("data").path("orderTime").asText(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        long age = Duration.between(apiTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant(),java.time.Instant.now()).getSeconds();
        check("OLD_fixture_explicit_plus08_SQL_API_time_matches_UI_payment_window",success(detail) && apiTime.equals(storedTime) && age>=0 && age<900);
        check("closed_old_payment_anonymous_refused", !success(request(client,"PUT","/user/order/payment",null,paymentBody(oldOrder))));
        check("closed_old_payment_other_owner_refused", !success(request(client,"PUT","/user/order/payment",otherToken,paymentBody(oldOrder))));
        check("closed_foreign_order_payment_refused", !success(request(client,"PUT","/user/order/payment",token,paymentBody(foreignOrder))));
        check("closed_old_cancel_other_owner_refused", !success(request(client,"PUT","/user/order/cancel/" + oldOrder,otherToken,null)));
        check("closed_old_cancel_anonymous_refused", !success(request(client,"PUT","/user/order/cancel/" + oldOrder,null,null)));
        check("closed_admin_confirm_anonymous_refused", !success(request(client,"PUT","/admin/order/confirm",null,Map.of("id",oldOrder))));
        check("closed_admin_confirm_user_credential_refused", !success(request(client,"PUT","/admin/order/confirm",token,Map.of("id",oldOrder))));
        check("closed_invalid_access_preserves_all_sql", before.equals(snapshot()));
        var details = sql.queryForList("SELECT * FROM order_detail WHERE order_id BETWEEN ? AND ? ORDER BY id",oldOrder,foreignOrder);
        var payment = request(client,"PUT","/user/order/payment",token,paymentBody(oldOrder));
        check("closed_OLD_actual_mock_payment_succeeds", success(payment) && "mock".equals(payment.path("data").path("paySign").asText())
                && count("SELECT COUNT(*) FROM orders WHERE id=? AND status=2 AND pay_status=1 AND checkout_time IS NOT NULL AND amount=40.00 AND pack_amount=2",oldOrder) == 1);
        var paid = snapshot();
        check("closed_OLD_mock_payment_retry_succeeds", success(request(client,"PUT","/user/order/payment",token,paymentBody(oldOrder))));
        check("closed_OLD_paid_retry_no_sql_write", paid.equals(snapshot()));
        check("closed_OLD_confirm_succeeds", success(request(client,"PUT","/admin/order/confirm",adminToken,Map.of("id",oldOrder))) && orderStatus(oldOrder,3));
        check("closed_OLD_delivery_succeeds", success(request(client,"PUT","/admin/order/delivery/"+oldOrder,adminToken,null)) && orderStatus(oldOrder,4));
        check("closed_OLD_complete_succeeds", success(request(client,"PUT","/admin/order/complete/"+oldOrder,adminToken,null)) && orderStatus(oldOrder,5)
                && count("SELECT COUNT(*) FROM orders WHERE id=? AND delivery_time IS NOT NULL",oldOrder) == 1);
        for (long order : List.of(cancelPending,cancelPaid)) {
            check("closed_OLD_legal_user_cancel_state_" + (order == cancelPending ? 1 : 2), success(request(client,"PUT","/user/order/cancel/"+order,token,null)) && orderStatus(order,6));
        }
        check("closed_OLD_legal_admin_cancel", success(request(client,"PUT","/admin/order/cancel",adminToken,Map.of("id",adminCancel,"cancelReason","fixture cancel"))) && orderStatus(adminCancel,6));
        check("closed_OLD_history_details_unchanged", details.equals(sql.queryForList("SELECT * FROM order_detail WHERE order_id BETWEEN ? AND ? ORDER BY id",oldOrder,foreignOrder)));
        check("closed_status_remains_closed_during_old_lifecycle", "0".equals(redis.opsForValue().get(RedisKeys.shopStatusKey())));
    }
    static boolean orderStatus(long order, int status) { return count("SELECT COUNT(*) FROM orders WHERE id=? AND status=?",order,status) == 1; }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(), run = Path.of(args[1]).toAbsolutePath();
        check("outputs_in_project", run.startsWith(root.resolve(".scratch/issue57")));
        long id = 5_700_000_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        owner=id; other=id+1; dish=id+2; oldOrder=id+10; cancelPending=id+11; cancelPaid=id+12; adminCancel=id+13; foreignOrder=id+14;
        org.springframework.context.ConfigurableApplicationContext context = null;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try (client) {
            try {
                context = new SpringApplication(App.class, Fixtures.class).run(
                        "--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                        "--spring.profiles.active=prod,afk", "--server.port=0", "--logging.level.root=OFF", "--spring.main.banner-mode=off",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--spring.datasource.druid.url=jdbc:mysql://mysql:3306/sandcastle_fixture?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                        "--spring.datasource.druid.username=root", "--spring.datasource.druid.password=",
                        "--spring.data.redis.host=redis", "--spring.data.redis.port=6379", "--spring.data.redis.database=0",
                        "--analysis.task.enabled=false", "--analysis.task.snapshot-cron=-", "--analysis.task.prediction-cron=-", "--analysis.task.training-cron=-",
                        "--order-risk.task.scoring-enabled=false", "--order-risk.task.training-cron=-", "--analysis.xgboost.enabled=false",
                        "--analysis.xgboost.model-dir="+run.resolve("unused-analysis"), "--order-risk.model-dir="+run.resolve("unused-risk"));
                sql=context.getBean(JdbcTemplate.class); redis=context.getBean(StringRedisTemplate.class);
                check("dedicated_database_before_writes", "sandcastle_fixture".equals(sql.queryForObject("SELECT DATABASE()",String.class)));
                check("synthetic900001_markers_before_writes", count("SELECT COUNT(*) FROM user WHERE id=900001 AND openid='sandbox-user-a'")==1
                        && count("SELECT COUNT(*) FROM employee WHERE id=900001 AND username='sandbox_admin' AND name='Sandcastle fixture'")==1
                        && count("SELECT COUNT(*) FROM category WHERE id=900001 AND name='Fixture category'")==1);
                var factory=redis.getConnectionFactory();
                check("forced_internal_redis_only", factory instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuce && "redis".equals(lettuce.getHostName()) && lettuce.getPort()==6379 && lettuce.getDatabase()==0);
                var shop=context.getBean(ShopStatusService.class);
                check("actual_prod_status_bean_not_fake", shop.getClass()==ShopStatusServiceImpl.class && context.getBeansOfType(ShopStatusService.class).size()==1);
                check("actual_spring_transaction_proxy", AopUtils.isAopProxy(context.getBean(OrdersApplicationService.class)));
                check("all_scheduled_jobs_disabled", !context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                inspectSchema(run);
                for (String table : List.of("user","address_book","dish","orders","order_detail","shopping_cart")) {
                    check("fresh_UUID_numeric_ids_"+table, count("SELECT COUNT(*) FROM "+identifier(table)+" WHERE id BETWEEN ? AND ?",id,id+14)==0);
                }
                check("fresh_cleanup_scopes", count("SELECT COUNT(*) FROM orders WHERE user_id IN (?,?)",owner,other)==0
                        && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id IN (?,?)",owner,other)==0
                        && count("SELECT COUNT(*) FROM address_book WHERE user_id IN (?,?)",owner,other)==0
                        && count("SELECT COUNT(*) FROM order_detail WHERE order_id BETWEEN ? AND ?",oldOrder,foreignOrder)==0);
                check("fresh_observation_context_absent", !Boolean.TRUE.equals(redis.hasKey(RedisKeys.aiRecommendContextKey(owner))));
                var original=snapshot(); var originalRedis=unrelatedRedis();
                String key=RedisKeys.shopStatusKey(); var originalStatus=RedisValue.read(key);
                var jwt=context.getBean(SkyProperties.class).getJwt();
                token=JwtUtil.createToken(jwt.getUserSecretKey(),600000,owner,"fixture","user");
                otherToken=JwtUtil.createToken(jwt.getUserSecretKey(),600000,other,"fixture","user");
                adminToken=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","admin");
                base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
                try {
                    seed();
                    // The internal service's status key is temporarily owned; dump/absolute expiry restored below.
                    check("existing_admin_switch_uses_real_prod_service", success(request(client,"PUT","/admin/shop/0",adminToken,null)));
                    check("real_prod_closed_redis_read",shop.getStatus()==0 && "0".equals(redis.opsForValue().get(key)));
                    check("anonymous_admin_switch_refused", !success(request(client,"PUT","/admin/shop/1",null,null)) && shop.getStatus()==0);
                    refusal(client,"closed","店铺已打烊，暂时无法下单");
                    var before=snapshot();
                    check("anonymous_submit_refused",!success(submit(client,null)));
                    check("anonymous_submit_sql_unchanged",before.equals(snapshot()));
                    oldOrders(client);
                    redis.delete(key); redis.opsForList().rightPush(key,RUN); redis.expire(key,Duration.ofMinutes(10));
                    boolean unreadable=false;
                    try { shop.getStatus(); } catch (BusinessException ex) { unreadable=ex.getCause()!=null; }
                    check("real_prod_WRONGTYPE_status_throws_with_cause",unreadable);
                    refusal(client,"unreadable","店铺状态读取失败，请稍后再试");
                    check("existing_admin_reopen_succeeds",success(request(client,"PUT","/admin/shop/1",adminToken,null)));
                    check("real_prod_open_redis_read",shop.getStatus()==1 && "1".equals(redis.opsForValue().get(key)));
                    var otherCart=carts(other); var beforeForeignSubmit=snapshot();
                    check("open_foreign_address_submit_refused",!success(submit(client,otherToken)));
                    check("open_foreign_address_refusal_preserves_all_SQL_and_cart",beforeForeignSubmit.equals(snapshot()) && otherCart.equals(carts(other)));
                    long beforeOrders=count("SELECT COUNT(*) FROM orders WHERE user_id=?",owner), beforeDetails=count("SELECT COUNT(*) FROM order_detail");
                    int writes=coreWrites.get(); var submitted=submit(client,token);
                    check("open_real_HTTP_submit_success",success(submitted));
                    long newOrder=submitted.path("data").path("id").asLong();
                    check("open_trusted_total_40_not_client_claim",submitted.path("data").path("orderAmount").decimalValue().compareTo(new BigDecimal("40.00"))==0
                            && count("SELECT COUNT(*) FROM orders WHERE id=? AND user_id=? AND status=1 AND pay_status=0 AND amount=40.00 AND pack_amount=2 AND remark='Issue57 retained form'",newOrder,owner)==1);
                    check("open_expected_order_and_detail_only",count("SELECT COUNT(*) FROM orders WHERE user_id=?",owner)==beforeOrders+1
                            && count("SELECT COUNT(*) FROM order_detail")==beforeDetails+1
                            && count("SELECT COUNT(*) FROM order_detail WHERE order_id=? AND dish_id=? AND number=2 AND amount=18.00 AND image='/static/cart57.png' AND dish_flavor='mild'",newOrder,dish)==1);
                    check("open_actual_transactional_core_writes_observed",coreWrites.get()-writes>=3);
                    check("open_clears_only_owner_cart",carts(owner).isEmpty() && otherCart.equals(carts(other)));
                    var actualCart=request(client,"GET","/user/shoppingCart/list",token,null);
                    check("open_cart_GET_empty",success(actualCart) && actualCart.path("data").isEmpty());
                    check("unrelated_Redis_state_preserved",sameUnrelatedRedis(originalRedis));
                    businessPassed=true;
                } finally {
                    try { cleanup(); } finally { originalStatus.restore(key); }
                    cleanupPassed=original.equals(snapshot()) && sameUnrelatedRedis(originalRedis) && originalStatus.matches(key);
                    check("scoped_cleanup_all_original_SQL_Redis_status_TTL_preserved",cleanupPassed);
                }
            } finally {
                if(context!=null) { context.close(); springClosed=!context.isActive(); }
            }
        } catch(Throwable ex) {
            failure=ex instanceof AssertionError ? String.valueOf(ex.getMessage()) : ex.getClass().getSimpleName();
            System.out.println("ISSUE57 FAIL "+failure);
        } finally {
            httpClosed=client.isTerminated();
            var result=new LinkedHashMap<String,Object>();
            result.put("businessPassed",businessPassed); result.put("cleanupPassed",cleanupPassed);
            result.put("springClosed",springClosed); result.put("httpClientClosed",httpClosed);
            result.put("assertionsPassed",checks); result.put("failure",failure);
            result.put("devEvidence","ordinary Issue19ShopStatusTest.devStatusStillStaysOpen; prod probe does not claim a dev Spring context");
            Files.writeString(run.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
        if(failure!=null || !businessPassed || !cleanupPassed || !springClosed || !httpClosed) System.exit(1);
        System.out.println("ISSUE57 ALL_SCENARIOS_PASS assertions="+checks.size());
    }
}
