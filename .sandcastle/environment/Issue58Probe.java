import com.codeying.App;
import com.codeying.constant.RedisKeys;
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
public class Issue58Probe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String RUN = "I58-" + UUID.randomUUID().toString().substring(0, 12);
    static final List<String> checks = new ArrayList<>();
    static final Map<String, List<String>> primaryKeys = new TreeMap<>();
    static final AtomicInteger coreWrites = new AtomicInteger(), mapCalls = new AtomicInteger();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static long owner, other, dish, oldOrder, foreignOrder;
    static String base, token, otherToken, adminToken;
    static boolean businessPassed, cleanupPassed, springClosed, httpClosed;
    static String failure;

    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks.add(name);
        System.out.println("ISSUE58 PASS " + name);
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
            String order = String.join(",", entry.getValue().stream().map(Issue58Probe::identifier).toList());
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
    static Map<String, Object> submitBody(Integer marker) {
        var body = new LinkedHashMap<String,Object>();
        body.put("addressBookId", owner); body.put("amount", "0.01"); body.put("packAmount", -99);
        body.put("payMethod",1); body.put("remark","Issue58 retained form");
        body.put("tablewareStatus",0); body.put("tablewareNumber",3);
        if (marker != null) body.put("deliveryStatus",marker);
        body.put("estimatedDeliveryTime",Integer.valueOf(0).equals(marker) ? "2001-01-01 01:01:01" : null);
        return body;
    }
    static JsonNode submit(HttpClient client, String auth) throws Exception {
        return request(client,"POST","/user/order/submit",auth,submitBody(0));
    }
    static void seedCart() {
        sql.update("INSERT INTO shopping_cart(id,name,image,user_id,dish_id,dish_flavor,number,amount,create_time) VALUES(?,'Issue58 cart','/static/cart58.png',?,?,'mild',2,0.01,NOW())",owner,owner,dish);
    }
    static void seed() {
        sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,?),(?,?,?)",owner,RUN+"-a",RUN+"-a",other,RUN+"-b",RUN+"-b");
        sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue58 A','00000000000',?,1),(?,?,'Issue58 B','00000000001',?,1)",owner,owner,RUN+" Isolation street",other,other,RUN+" Other street");
        sql.update("INSERT INTO dish(id,name,category_id,price,image,status) VALUES(?,?,900001,18.00,'/static/issue58.png',1)",dish,RUN);
        for (long order : List.of(oldOrder,foreignOrder)) {
            long user = order==foreignOrder ? other : owner;
            sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,amount,pack_amount,delivery_status,estimated_delivery_time) VALUES(?,?,5,?,?,'2020-01-01 12:00:00',1,40.00,2,0,'2020-01-02 08:30:00')",order,RUN+"-"+(order-oldOrder),user,user);
            sql.update("INSERT INTO order_detail(id,name,image,order_id,dish_id,dish_flavor,number,amount) VALUES(?,'Issue58 historical','/static/old58.png',?,?,'mild',2,18.00)",order,order,dish);
        }
        seedCart();
        sql.update("INSERT INTO shopping_cart(id,name,user_id,dish_id,number,amount,create_time) VALUES(?,'Issue58 other',?,?,1,18.00,NOW())",other,other,dish);
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
    static void historicalReads(HttpClient client) throws Exception {
        var before = snapshot();
        var detail = request(client,"GET","/user/order/orderDetail/"+oldOrder,token,null);
        var history = request(client,"GET","/user/order/historyOrders?page=1&pageSize=100",token,null);
        check("historical_reserved_detail_preserves_exact_status_ETA",success(detail)
                && detail.path("data").path("deliveryStatus").asInt(-1)==0
                && "2020-01-02 08:30:00".equals(detail.path("data").path("estimatedDeliveryTime").asText()));
        boolean found=false, foreign=false;
        for(var order : history.path("data").path("records")) {
            if(order.path("id").asLong()==oldOrder) found=order.path("deliveryStatus").asInt(-1)==0
                    && "2020-01-02 08:30:00".equals(order.path("estimatedDeliveryTime").asText());
            if(order.path("id").asLong()==foreignOrder) foreign=true;
        }
        check("historical_reserved_pagination_preserves_exact_status_ETA_and_owner",success(history) && found && !foreign);
        check("foreign_history_detail_refused",!success(request(client,"GET","/user/order/orderDetail/"+oldOrder,otherToken,null)));
        check("history_reads_do_not_write_any_SQL",before.equals(snapshot()));
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(), run = Path.of(args[1]).toAbsolutePath();
        check("outputs_in_project", run.startsWith(root.resolve(".scratch/issue58")));
        long id = 5_800_000_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        owner=id; other=id+1; dish=id+2; oldOrder=id+10; foreignOrder=id+14;
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
                    // Temporarily own only the isolated status key; restore its DUMP and remaining TTL.
                    check("existing_admin_switch_closed",success(request(client,"PUT","/admin/shop/0",adminToken,null)) && shop.getStatus()==0);
                    refusal(client,"closed","店铺已打烊，暂时无法下单");
                    redis.delete(key); redis.opsForList().rightPush(key,RUN); redis.expire(key,Duration.ofMinutes(10));
                    refusal(client,"unreadable","店铺状态读取失败，请稍后再试");
                    check("existing_admin_switch_OPEN_real_prod_service",success(request(client,"PUT","/admin/shop/1",adminToken,null)) && shop.getStatus()==1);
                    var beforeUnauthorized=snapshot();
                    check("anonymous_submit_refused",!success(submit(client,null)));
                    check("foreign_address_submit_refused",!success(submit(client,otherToken)));
                    check("unauthorized_submit_preserves_SQL",beforeUnauthorized.equals(snapshot()));
                    historicalReads(client);
                    var historicalRows=sql.queryForList("SELECT * FROM orders WHERE id IN (?,?) ORDER BY id",oldOrder,foreignOrder);
                    var historicalDetails=sql.queryForList("SELECT * FROM order_detail WHERE order_id IN (?,?) ORDER BY id",oldOrder,foreignOrder);
                    var otherCart=carts(other);
                    for(Integer marker : Arrays.asList(0,1,null)) {
                        if(carts(owner).isEmpty()) seedCart();
                        int beforeOrders=count("SELECT COUNT(*) FROM orders WHERE user_id=?",owner);
                        int beforeDetails=count("SELECT COUNT(*) FROM order_detail");
                        int writes=coreWrites.get(), maps=mapCalls.get();
                        var submitted=request(client,"POST","/user/order/submit",token,submitBody(marker));
                        String scenario=marker==null ? "ordinary_omitted" : marker==0 ? "legacy_reserved0" : "ordinary_immediate1";
                        check(scenario+"_real_HTTP_accepted",success(submitted));
                        long created=submitted.path("data").path("id").asLong();
                        check(scenario+"_NEW_SQL_immediate_and_trusted_amount",count("SELECT COUNT(*) FROM orders WHERE id=? AND user_id=? AND delivery_status=1 AND status=1 AND pay_status=0 AND amount=40.00 AND pack_amount=2 AND tableware_number=3 AND remark='Issue58 retained form'",created,owner)==1
                                && submitted.path("data").path("orderAmount").decimalValue().compareTo(new BigDecimal("40.00"))==0);
                        var ordered=sql.queryForObject("SELECT order_time FROM orders WHERE id=?",java.time.LocalDateTime.class,created);
                        var estimated=sql.queryForObject("SELECT estimated_delivery_time FROM orders WHERE id=?",java.time.LocalDateTime.class,created);
                        check(scenario+"_server_ETA_10prep_plus7map_ignores_client_time",estimated.equals(ordered.plusMinutes(17)) && mapCalls.get()==maps+1);
                        var apiEstimate=java.time.LocalDateTime.parse(submitted.path("data").path("estimatedDeliveryTime").asText(),
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        var zone=java.time.ZoneId.of("Asia/Shanghai");
                        // JSON omits fractions; MySQL DATETIME(0) can round the original millisecond value.
                        long precisionDelta=Duration.between(estimated.atZone(zone).toInstant(),apiEstimate.atZone(zone).toInstant()).abs().toMillis();
                        check(scenario+"_response_ETA_matches_SQL_at_existing_second_precision",precisionDelta<=1000);
                        check(scenario+"_expected_order_and_detail_only",count("SELECT COUNT(*) FROM orders WHERE user_id=?",owner)==beforeOrders+1
                                && count("SELECT COUNT(*) FROM order_detail")==beforeDetails+1
                                && count("SELECT COUNT(*) FROM order_detail WHERE order_id=? AND dish_id=? AND number=2 AND amount=18.00 AND image='/static/cart58.png' AND dish_flavor='mild'",created,dish)==1);
                        check(scenario+"_real_transactional_core_writes",coreWrites.get()-writes>=3);
                        check(scenario+"_only_owner_cart_cleared",carts(owner).isEmpty() && otherCart.equals(carts(other)));
                        historicalReads(client);
                        check(scenario+"_historical_orders_and_details_exactly_unchanged",historicalRows.equals(sql.queryForList("SELECT * FROM orders WHERE id IN (?,?) ORDER BY id",oldOrder,foreignOrder))
                                && historicalDetails.equals(sql.queryForList("SELECT * FROM order_detail WHERE order_id IN (?,?) ORDER BY id",oldOrder,foreignOrder)));
                    }
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
            System.out.println("ISSUE58 FAIL "+failure);
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
        System.out.println("ISSUE58 ALL_SCENARIOS_PASS assertions="+checks.size());
    }
}
