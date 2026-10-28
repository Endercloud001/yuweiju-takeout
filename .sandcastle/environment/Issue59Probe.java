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
public class Issue59Probe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String RUN = "I59-" + UUID.randomUUID().toString().substring(0, 12);
    static final List<String> checks = new ArrayList<>();
    static final Map<String, List<String>> primaryKeys = new TreeMap<>();
    static final AtomicInteger coreWrites = new AtomicInteger(), mapCalls = new AtomicInteger();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static long owner, other, dish, oldOrder, foreignOrder, badDish, badMeal;
    static volatile String fault = "off";
    static volatile boolean realFaultWrite;
    static String base, token, otherToken, adminToken;
    static boolean businessPassed, cleanupPassed, springClosed, httpClosed;
    static String failure;

    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks.add(name);
        System.out.println("ISSUE59 PASS " + name);
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
            Object result = invocation.proceed();
            String target = switch (fault) {
                case "order" -> "com.codeying.mapper.OrdersMapper.insert";
                case "detail" -> "com.codeying.mapper.OrderDetailMapper.insert";
                case "clear" -> "com.codeying.mapper.ShoppingCartMapper.delete";
                default -> "";
            };
            if (!target.isEmpty() && id.equals(target)) {
                ((Executor) invocation.getTarget()).flushStatements();
                check("injected_"+fault+"_after_actual_SQL_in_proxy_transaction", TransactionSynchronizationManager.isActualTransactionActive()
                        && count("SELECT COUNT(*) FROM orders WHERE user_id=? AND id NOT IN (?,?)",owner,oldOrder,foreignOrder)==1
                        && (!fault.equals("detail") || count("SELECT COUNT(*) FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id=? AND o.id NOT IN (?,?)",owner,oldOrder,foreignOrder)==1)
                        && (!fault.equals("clear") || carts(owner).isEmpty()));
                realFaultWrite = true;
                throw new IllegalStateException("issue59 isolated transaction failure");
            }
            return result;
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
        for (String table : List.of("user", "address_book", "dish", "setmeal", "orders", "order_detail", "shopping_cart")) {
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
            String order = String.join(",", entry.getValue().stream().map(Issue59Probe::identifier).toList());
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
        var body = new LinkedHashMap<String,Object>();
        body.put("addressBookId", owner); body.put("amount", "0.01"); body.put("packAmount", -99);
        body.put("payMethod",1); body.put("remark","Issue59 retained form");
        body.put("tablewareStatus",0); body.put("tablewareNumber",3);
        body.put("deliveryStatus",1);
        body.put("estimatedDeliveryTime",null);
        return body;
    }
    static JsonNode submit(HttpClient client, String auth) throws Exception {
        return request(client,"POST","/user/order/submit",auth,submitBody());
    }
    static void seedCart() {
        sql.update("INSERT INTO shopping_cart(id,name,image,user_id,dish_id,dish_flavor,number,amount,create_time) VALUES(?,'Issue59 cart','/static/cart59.png',?,?,'mild',2,0.01,NOW())",owner,owner,dish);
    }
    static void seed() {
        sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,?),(?,?,?)",owner,RUN+"-a",RUN+"-a",other,RUN+"-b",RUN+"-b");
        sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue59 A','00000000000',?,1),(?,?,'Issue59 B','00000000001',?,1)",owner,owner,RUN+" Isolation street",other,other,RUN+" Other street");
        sql.update("INSERT INTO dish(id,name,category_id,price,image,status) VALUES(?,?,900001,18.00,'/static/issue59.png',1)",dish,RUN);
        for (long order : List.of(oldOrder,foreignOrder)) {
            long user = order==foreignOrder ? other : owner;
            sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,amount,pack_amount,delivery_status,estimated_delivery_time) VALUES(?,?,5,?,?,'2020-01-01 12:00:00',1,40.00,2,0,'2020-01-02 08:30:00')",order,RUN+"-"+(order-oldOrder),user,user);
            sql.update("INSERT INTO order_detail(id,name,image,order_id,dish_id,dish_flavor,number,amount) VALUES(?,'Issue59 historical','/static/old59.png',?,?,'mild',2,18.00)",order,order,dish);
        }
        seedCart();
        sql.update("INSERT INTO shopping_cart(id,name,user_id,dish_id,number,amount,create_time) VALUES(?,'Issue59 other',?,?,1,18.00,NOW())",other,other,dish);
    }
    static void cleanup() {
        fault="off";
        // Freshness checks cover all cleanup scopes before seed; never delete historical fixed IDs.
        sql.update("DELETE d FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM orders WHERE user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM shopping_cart WHERE user_id IN (?,?)", owner, other);
        sql.update("DELETE FROM address_book WHERE (user_id=? AND id=?) OR (user_id=? AND id=?)", owner, owner, other, other);
        sql.update("DELETE FROM dish WHERE id IN (?,?)", dish,badDish);
        sql.update("DELETE FROM setmeal WHERE id=?",badMeal);
        sql.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }
    static JsonNode readCart(HttpClient client, String auth) throws Exception {
        return request(client,"GET","/user/shoppingCart/list",auth,null);
    }
    static void refusal(HttpClient client, String name) throws Exception {
        var before=snapshot(); var cart=carts(owner); int writes=coreWrites.get(), maps=mapCalls.get();
        var response=submit(client,token);
        check(name+"_submit_refused_with_reason",!success(response) && !response.path("message").asText().isBlank());
        check(name+"_no_core_write_attempt_or_map_call",writes==coreWrites.get() && maps==mapCalls.get());
        check(name+"_all_SQL_unchanged_and_exact_cart_retained",before.equals(snapshot()) && cart.equals(carts(owner)));
    }
    static void badCatalog(boolean meal, String scenario) {
        String table=meal ? "setmeal" : "dish"; long id=meal ? badMeal : badDish;
        sql.update("DELETE FROM "+table+" WHERE id=?",id);
        if (!scenario.equals("deleted")) {
            sql.update("INSERT INTO "+table+"(id,name,category_id,price,image,status) VALUES(?,?,900001,?, '/static/current59.png',1)",id,RUN+"-"+table,
                    scenario.equals("invalid_price") ? new BigDecimal("-1.00") : new BigDecimal("9.00"));
        }
        sql.update("INSERT INTO shopping_cart(id,name,image,user_id,dish_id,setmeal_id,dish_flavor,number,amount,create_time) VALUES(?,'Issue59 historical B','/static/oldB59.png',?,?,?,'hot',2,0.01,NOW())",owner+20,owner,meal?null:id,meal?id:null);
    }
    static void recover(HttpClient client, boolean meal, String scenario) throws Exception {
        if(carts(owner).isEmpty()) seedCart();
        badCatalog(meal,scenario);
        long bad=meal?badMeal:badDish;
        if(scenario.equals("stopped")) check("existing_admin_stops_"+(meal?"setmeal":"dish"),success(request(client,"POST","/admin/"+(meal?"setmeal":"dish")+"/status/0?id="+bad,adminToken,null)));
        var before=snapshot(); var originalCart=carts(owner); var foreign=carts(other);
        var read=readCart(client,token);
        String name=(meal?"setmeal_":"dish_")+scenario;
        check(name+"_GET_retains_all_owned_rows",success(read) && read.path("data").size()==2);
        var a=read.path("data").get(0); var b=read.path("data").get(1);
        check(name+"_A_trusted_current_price",a.path("dishId").asLong()==dish && a.path("available").asBoolean()
                && a.path("amount").decimalValue().compareTo(new BigDecimal("18.00"))==0 && a.path("number").asInt()==2);
        String reason=scenario.equals("deleted")?"商品已删除":scenario.equals("stopped")?"商品已停售":"商品或订单金额无效或超出范围";
        check(name+"_B_identity_quantity_historical_display_reason_without_trusted_amount",b.path("id").asLong()==owner+20
                && b.path(meal?"setmealId":"dishId").asLong()==bad && b.path("number").asInt()==2
                && "Issue59 historical B".equals(b.path("name").asText()) && "/static/oldB59.png".equals(b.path("image").asText())
                && "hot".equals(b.path("dishFlavor").asText()) && b.path("available").isBoolean() && !b.path("available").asBoolean()
                && reason.equals(b.path("unavailableReason").asText()) && b.path("amount").isNull());
        check(name+"_GET_no_SQL_writes",before.equals(snapshot()) && originalCart.equals(carts(owner)));
        refusal(client,name);
        var change=Map.of(meal?"setmealId":"dishId",bad,"dishFlavor","hot","userId",owner,"id",owner+20);
        check(name+"_foreign_sub_cannot_target_owner",!success(request(client,"POST","/user/shoppingCart/sub",otherToken,change))
                && originalCart.equals(carts(owner)) && foreign.equals(carts(other)));
        check(name+"_anonymous_sub_refused",!success(request(client,"POST","/user/shoppingCart/sub",null,change)) && before.equals(snapshot()));
        check(name+"_explicit_subtract_preserves_B_until_zero",success(request(client,"POST","/user/shoppingCart/sub",token,change))
                && count("SELECT COUNT(*) FROM shopping_cart WHERE id=? AND user_id=? AND number=1",owner+20,owner)==1 && carts(owner).size()==2);
        refusal(client,name+"_one_remaining");
        check(name+"_explicit_last_subtract_removes_only_B",success(request(client,"POST","/user/shoppingCart/sub",token,change)) && carts(owner).size()==1
                && foreign.equals(carts(other)) && originalCart.get(0).equals(carts(owner).get(0)));
        var refreshed=readCart(client,token);
        check(name+"_recovered_GET_one_valid_A",success(refreshed) && refreshed.path("data").size()==1 && refreshed.path("data").get(0).path("available").asBoolean());
        var purchased=submit(client,token); long created=purchased.path("data").path("id").asLong();
        check(name+"_trusted_purchase_after_explicit_removal",success(purchased)
                && purchased.path("data").path("orderAmount").decimalValue().compareTo(new BigDecimal("40.00"))==0
                && count("SELECT COUNT(*) FROM orders WHERE id=? AND user_id=? AND amount=40.00 AND pack_amount=2",created,owner)==1
                && count("SELECT COUNT(*) FROM order_detail WHERE order_id=? AND dish_id=? AND number=2 AND amount=18.00 AND image='/static/cart59.png' AND dish_flavor='mild'",created,dish)==1
                && count("SELECT COUNT(*) FROM order_detail WHERE order_id=?",created)==1 && carts(owner).isEmpty() && foreign.equals(carts(other)));
        // Remove only this scenario's new order so each injected transaction has a fresh observable write scope.
        sql.update("DELETE FROM order_detail WHERE order_id=?",created);
        sql.update("DELETE FROM orders WHERE id=? AND user_id=?",created,owner);
    }
    static void historicalReads(HttpClient client) throws Exception {
        var before = snapshot();
        var detail = request(client,"GET","/user/order/orderDetail/"+oldOrder,token,null);
        var history = request(client,"GET","/user/order/historyOrders?page=1&pageSize=100",token,null);
        check("historical_reserved_detail_preserves_exact_status_ETA",success(detail)
                && detail.path("data").path("deliveryStatus").asInt(-1)==0
                && "2020-01-02 08:30:00".equals(detail.path("data").path("estimatedDeliveryTime").asText()));
        var items=detail.path("data").path("orderDetailList");
        check("historical_image_quantity_flavor_amount_preserved",success(detail) && items.size()==1
                && "/static/old59.png".equals(items.get(0).path("image").asText())
                && items.get(0).path("number").asInt()==2 && "mild".equals(items.get(0).path("dishFlavor").asText())
                && items.get(0).path("amount").decimalValue().compareTo(new BigDecimal("18.00"))==0);
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
        check("outputs_in_project", (run.startsWith(root.resolve(".scratch/issue59")) || run.startsWith(root.resolve(".scratch/issue60"))));
        long id = 5_900_000_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        owner=id; other=id+1; dish=id+2; badDish=id+3; badMeal=id+4; oldOrder=id+10; foreignOrder=id+14;
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
                for (String table : List.of("user","address_book","dish","setmeal","orders","order_detail","shopping_cart")) {
                    check("fresh_UUID_numeric_ids_"+table, count("SELECT COUNT(*) FROM "+identifier(table)+" WHERE id BETWEEN ? AND ?",id,id+20)==0);
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
                    check("existing_admin_shop_OPEN",success(request(client,"PUT","/admin/shop/1",adminToken,null)) && shop.getStatus()==1);
                    var initial=snapshot();
                    check("anonymous_GET_refused",!success(readCart(client,null)));
                    var foreignRead=readCart(client,otherToken);
                    check("GET_uses_auth_owner_ignores_query_owner",success(foreignRead) && foreignRead.path("data").size()==1
                            && foreignRead.path("data").get(0).path("userId").asLong()==other
                            && request(client,"GET","/user/shoppingCart/list?userId="+other,token,null).path("data").get(0).path("userId").asLong()==owner);
                    check("anonymous_and_owned_GET_no_writes",initial.equals(snapshot()));
                    historicalReads(client);
                    for(boolean meal:List.of(false,true)) for(String scenario:List.of("stopped","deleted","invalid_price")) recover(client,meal,scenario);
                    seedCart();
                    check("read_before_stop_A_is_valid",readCart(client,token).path("data").get(0).path("available").asBoolean());
                    check("admin_stops_A_after_read",success(request(client,"POST","/admin/dish/status/0?id="+dish,adminToken,null)));
                    refusal(client,"read_then_stop_A");
                    check("admin_reopens_A",success(request(client,"POST","/admin/dish/status/1?id="+dish,adminToken,null)));
                    for(String injected:List.of("order","detail","clear")) {
                        var before=snapshot(); realFaultWrite=false; fault=injected;
                        boolean refused;
                        try { refused=!success(submit(client,token)); } finally { fault="off"; }
                        check(injected+"_actual_transaction_failure_refused",refused && realFaultWrite);
                        check(injected+"_rollback_restores_every_SQL_row_and_cart",before.equals(snapshot()));
                    }
                    check("valid_submit_recovers_after_rollback",success(submit(client,token)) && carts(owner).isEmpty());
                    historicalReads(client);
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
            System.out.println("ISSUE59 FAIL "+failure);
        } finally {
            httpClosed=client.isTerminated();
            var result=new LinkedHashMap<String,Object>();
            result.put("businessPassed",businessPassed); result.put("cleanupPassed",cleanupPassed);
            result.put("springClosed",springClosed); result.put("httpClientClosed",httpClosed);
            result.put("assertionsPassed",checks); result.put("failure",failure);
            result.put("acceptanceScope","worker isolated SQL/HTTP; independent and official tool acceptance pending host");
            Files.writeString(run.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
        if(failure!=null || !businessPassed || !cleanupPassed || !springClosed || !httpClosed) System.exit(1);
        System.out.println("ISSUE59 ALL_SCENARIOS_PASS assertions="+checks.size());
    }
}
