import com.codeying.App;
import com.codeying.entity.ShoppingCart;
import com.codeying.properties.SkyProperties;
import com.codeying.service.OrdersApplicationService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Issue55 only: real HTTP/auth -> Spring transaction proxy -> MyBatis -> isolated MySQL. */
public class Issue55Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> TABLES = List.of("user", "address_book", "dish", "setmeal", "orders", "order_detail", "shopping_cart");
    static final List<String> checks = new ArrayList<>();
    static JdbcTemplate sql;
    static long owner, other, oldDish, targetDish, targetSetmeal, ownOrder, foreignOrder, missingOrder;
    static volatile String mode = "off";
    static String base, token;
    static volatile boolean cleared, rebuilt;
    static boolean businessPassed, cleanupPassed, springClosed, httpClosed;
    static String failure;

    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks.add(name);
        System.out.println("ISSUE55 PASS " + name);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Fixtures {
        @Bean CartWrites cartWrites() { return new CartWrites(); }
        @Bean static BeanFactoryPostProcessor disableJobs() {
            // Also disable the fixed-delay customer-service task; no background work in this probe.
            return factory -> {
                var registry = (BeanDefinitionRegistry) factory;
                String scheduler = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(scheduler)) registry.removeBeanDefinition(scheduler);
            };
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    public static class CartWrites implements Interceptor {
        @Override public Object intercept(Invocation invocation) throws Throwable {
            String id = ((MappedStatement) invocation.getArgs()[0]).getId();
            Object result = invocation.proceed();
            if (mode.equals("off") || !id.startsWith("com.codeying.mapper.ShoppingCartMapper.")) return result;
            if (id.endsWith(".delete")) {
                cleared = TransactionSynchronizationManager.isActualTransactionActive()
                        && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?", owner) == 0;
                check("real_clear_inside_transaction_" + mode, cleared);
                if (mode.equals("clear")) throw new IllegalStateException("issue55 injected after real clear");
            }
            if (mode.equals("rebuild") && id.endsWith(".insert")
                    && invocation.getArgs()[1] instanceof ShoppingCart cart && Objects.equals(cart.getUserId(), owner)) {
                // saveBatch uses a real batch Executor. Flush it before injecting failure,
                // so this is a database write/rollback proof, not a mocked Mapper result.
                ((Executor) invocation.getTarget()).flushStatements();
                rebuilt = cleared && TransactionSynchronizationManager.isActualTransactionActive()
                        && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?", owner) == 1
                        && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=? AND dish_id=?", owner, oldDish) == 0;
                check("real_rebuild_after_clear_inside_transaction", rebuilt);
                throw new IllegalStateException("issue55 injected after real rebuild");
            }
            return result;
        }
    }

    static int count(String query, Object... args) { return sql.queryForObject(query, Integer.class, args); }
    static Map<String, Object> snapshot() {
        var snapshot = new LinkedHashMap<String, Object>();
        // Every table's actual PK is inspected before writes. Fixed table names only.
        for (String table : TABLES) snapshot.put(table, sql.queryForList("SELECT * FROM `" + table + "` ORDER BY id"));
        return snapshot;
    }
    static List<Map<String, Object>> carts(long user) {
        return sql.queryForList("SELECT * FROM shopping_cart WHERE user_id=? ORDER BY id", user);
    }
    static void inspectSchema(Path run) throws Exception {
        var schema = new LinkedHashMap<String, Object>();
        for (String table : TABLES) {
            var keys = sql.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position", String.class, table);
            check("actual_primary_key_" + table, keys.equals(List.of("id")));
            var columns = sql.queryForList("SELECT column_name,is_nullable,column_default,column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position", table);
            check("schema_columns_present_" + table, !columns.isEmpty());
            check("transactional_engine_" + table, "InnoDB".equals(sql.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", String.class, table)));
            schema.put(table, Map.of("primaryKey", keys, "columns", columns));
        }
        // Metadata only, no fixture rows or HTTP payloads in evidence.
        Files.writeString(run.resolve("schema-inspection.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }
    static JsonNode request(HttpClient client, String method, String path, String auth) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
        if (auth != null) builder.header("authentication", auth);
        var response = client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body());
    }
    static boolean success(JsonNode response) { return response.path("code").asInt() == 1; }
    static JsonNode repeat(HttpClient client, long id, String auth) throws Exception {
        return request(client, "POST", "/user/order/repetition/" + id, auth);
    }
    static void seed() {
        String marker = "I55-" + UUID.randomUUID().toString().substring(0, 12);
        sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,?),(?,?,?)", owner, marker + "-a", marker + "-a", other, marker + "-b", marker + "-b");
        sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue55 A','00000000000','Isolation',0),(?,?,'Issue55 B','00000000001','Isolation',0)", owner, owner, other, other);
        sql.update("INSERT INTO dish(id,name,category_id,price,status) VALUES(?,?,900001,5,1),(?,?,900001,18,1)", oldDish, marker + "-old", targetDish, marker + "-dish");
        sql.update("INSERT INTO setmeal(id,name,category_id,price,status) VALUES(?,?,900001,9,1)", targetSetmeal, marker + "-set");
        sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,amount,pack_amount) VALUES(?,?,5,?,?,NOW(),45,3),(?,?,5,?,?,NOW(),5,1)", ownOrder, marker + "-own", owner, owner, foreignOrder, marker + "-foreign", other, other);
        sql.update("INSERT INTO order_detail(id,name,image,order_id,dish_id,setmeal_id,dish_flavor,number,amount) VALUES(?,'Issue55 dish','/static/dish.png',?,?,NULL,'mild',2,18),(?,'Issue55 set','/static/set.png',?,NULL,?,NULL,1,9),(?,'Issue55 foreign',NULL,?,?,NULL,NULL,1,5)", ownOrder, ownOrder, targetDish, ownOrder + 1, ownOrder, targetSetmeal, foreignOrder, foreignOrder, oldDish);
        seedCart();
        sql.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Issue55 other',?,?,4,5,NOW())", other, oldDish);
    }
    static void seedCart() {
        sql.update("DELETE FROM shopping_cart WHERE user_id=?", owner);
        sql.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Issue55 old',?,?,3,5,NOW())", owner, oldDish);
    }
    static void cleanup() {
        mode = "off";
        sql.update("DELETE FROM shopping_cart WHERE user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM order_detail WHERE order_id IN (?,?)", ownOrder, foreignOrder);
        sql.update("DELETE FROM orders WHERE id IN (?,?) AND user_id IN (?,?)", ownOrder, foreignOrder, owner, other);
        sql.update("DELETE FROM address_book WHERE id IN (?,?) AND user_id IN (?,?)", owner, other, owner, other);
        sql.update("DELETE FROM dish WHERE id IN (?,?)", oldDish, targetDish);
        sql.update("DELETE FROM setmeal WHERE id=?", targetSetmeal);
        sql.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path run = Path.of(args[1]).toAbsolutePath();
        check("outputs_in_project", run.startsWith(root.resolve(".scratch/issue55")));
        long id = ThreadLocalRandom.current().nextLong(5_500_000_000_000L, 8_000_000_000_000L);
        owner = id; other = id + 1; oldDish = id + 2; targetDish = id + 3; targetSetmeal = id + 4;
        ownOrder = id + 5; foreignOrder = id + 7; missingOrder = id + 8;
        var app = new SpringApplication(App.class, Fixtures.class);
        org.springframework.context.ConfigurableApplicationContext context = null;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try (client) {
            try {
                context = app.run("--spring.config.location=file:" + root.resolve(".sandcastle/environment/application-afk.yml"),
                        "--spring.profiles.active=dev,afk", "--server.port=0", "--logging.level.root=OFF", "--spring.main.banner-mode=off",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--spring.datasource.druid.url=jdbc:mysql://mysql:3306/sandcastle_fixture?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                        "--spring.datasource.druid.username=root", "--spring.datasource.druid.password=",
                        "--spring.data.redis.host=redis", "--spring.data.redis.port=6379", "--spring.data.redis.database=0",
                        "--analysis.task.enabled=false", "--analysis.task.snapshot-cron=-", "--analysis.task.prediction-cron=-", "--analysis.task.training-cron=-",
                        "--order-risk.task.scoring-enabled=false", "--order-risk.task.training-cron=-", "--analysis.xgboost.enabled=false",
                        "--analysis.xgboost.model-dir=" + run.resolve("unused-analysis"), "--order-risk.model-dir=" + run.resolve("unused-risk"));
                sql = context.getBean(JdbcTemplate.class);
                check("dedicated_sandcastle_fixture_before_writes", "sandcastle_fixture".equals(sql.queryForObject("SELECT DATABASE()", String.class)));
                check("synthetic900001_markers_before_writes", count("SELECT COUNT(*) FROM user WHERE id=900001 AND openid='sandbox-user-a'") == 1
                        && count("SELECT COUNT(*) FROM employee WHERE id=900001 AND username='sandbox_admin' AND name='Sandcastle fixture'") == 1
                        && count("SELECT COUNT(*) FROM category WHERE id=900001 AND name='Fixture category'") == 1);
                var redis = context.getBean(StringRedisTemplate.class);
                try (var connection = redis.getConnectionFactory().getConnection()) { check("internal_redis_ping_read_only", "PONG".equals(connection.ping())); }
                inspectSchema(run);
                var service = context.getBean(OrdersApplicationService.class);
                check("actual_spring_transaction_proxy", AopUtils.isAopProxy(service));
                check("all_scheduled_jobs_disabled", !context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                for (String table : TABLES) check("fresh_owned_ids_" + table, count("SELECT COUNT(*) FROM `" + table + "` WHERE id BETWEEN ? AND ?", id, id + 8) == 0);
                check("fresh_owned_cleanup_scopes", count("SELECT COUNT(*) FROM shopping_cart WHERE user_id IN (?,?)", owner, other) == 0
                        && count("SELECT COUNT(*) FROM orders WHERE user_id IN (?,?)", owner, other) == 0
                        && count("SELECT COUNT(*) FROM address_book WHERE user_id IN (?,?)", owner, other) == 0
                        && count("SELECT COUNT(*) FROM order_detail WHERE order_id IN (?,?)", ownOrder, foreignOrder) == 0);
                var original = snapshot();
                var jwt = context.getBean(SkyProperties.class).getJwt();
                token = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, owner, "fixture", "user");
                String otherToken = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, other, "fixture", "user");
                base = "http://127.0.0.1:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
                try {
                    seed();
                    var before = snapshot();
                    check("anonymous_refusal", !success(repeat(client, ownOrder, null)));
                    check("anonymous_sql_unchanged", before.equals(snapshot()));
                    check("missing_order_refusal", !success(repeat(client, missingOrder, token)));
                    check("missing_sql_unchanged", before.equals(snapshot()));
                    check("foreign_order_refusal", !success(repeat(client, foreignOrder, token)));
                    check("foreign_sql_unchanged", before.equals(snapshot()));
                    check("other_identity_refusal", !success(repeat(client, ownOrder, otherToken)));
                    check("other_identity_sql_unchanged", before.equals(snapshot()));
                    var otherCart = carts(other);
                    var history = sql.queryForList("SELECT * FROM order_detail WHERE order_id IN (?,?) ORDER BY id", ownOrder, foreignOrder);
                    check("replacement_http_success", success(repeat(client, ownOrder, token)));
                    check("replacement_only_two_target_rows", carts(owner).size() == 2
                            && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=? AND dish_id=?", owner, oldDish) == 0
                            && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=? AND dish_id=? AND setmeal_id IS NULL AND number=2 AND amount=18 AND dish_flavor='mild' AND image='/static/dish.png'", owner, targetDish) == 1
                            && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=? AND setmeal_id=? AND dish_id IS NULL AND number=1 AND amount=9 AND image='/static/set.png'", owner, targetSetmeal) == 1);
                    check("foreign_cart_unchanged_on_success", otherCart.equals(carts(other)));
                    check("history_unchanged_on_success", history.equals(sql.queryForList("SELECT * FROM order_detail WHERE order_id IN (?,?) ORDER BY id", ownOrder, foreignOrder)));
                    var actualCart = request(client, "GET", "/user/shoppingCart/list", token);
                    check("actual_cart_get_after_replacement", success(actualCart) && actualCart.path("data").size() == 2);
                    for (String fault : List.of("clear", "rebuild")) {
                        seedCart(); before = snapshot(); cleared = false; rebuilt = false; mode = fault;
                        boolean refused;
                        try { refused = !success(repeat(client, ownOrder, token)); } finally { mode = "off"; }
                        check(fault + "_write_failure_reported", refused);
                        check(fault + "_real_database_write_reached", cleared && (!fault.equals("rebuild") || rebuilt));
                        check(fault + "_rollback_restores_full_sql_snapshot", before.equals(snapshot()));
                    }
                    check("replacement_recovers_after_fault", success(repeat(client, ownOrder, token)) && carts(owner).size() == 2);
                    businessPassed = true;
                } finally {
                    cleanup();
                    cleanupPassed = original.equals(snapshot());
                    check("owned_cleanup_and_all_original_rows_preserved", cleanupPassed);
                }
            } finally {
                if (context != null) { context.close(); springClosed = !context.isActive(); }
            }
        } catch (Throwable ex) {
            // Never print throwable messages/causes: JDBC/HTTP exceptions may contain payloads.
            failure = ex instanceof AssertionError ? String.valueOf(ex.getMessage()) : ex.getClass().getSimpleName();
            System.out.println("ISSUE55 FAIL " + failure);
        } finally {
            httpClosed = client.isTerminated();
            var result = new LinkedHashMap<String, Object>();
            result.put("businessPassed", businessPassed); result.put("cleanupPassed", cleanupPassed);
            result.put("springClosed", springClosed); result.put("httpClientClosed", httpClosed);
            result.put("assertionsPassed", checks); result.put("failure", failure);
            Files.writeString(run.resolve("scenario-results.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
        if (failure != null || !businessPassed || !cleanupPassed || !springClosed || !httpClosed) System.exit(1);
        System.out.println("ISSUE55 ALL_SCENARIOS_PASS assertions=" + checks.size());
    }
}
