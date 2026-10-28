import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.properties.SkyProperties;
import com.codeying.service.DishApplicationService;
import com.codeying.service.SetmealApplicationService;
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

/** F4 real HTTP, SQL constraints and Spring transaction acceptance. No mock Mapper. */
public class Issue60Probe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String RUN = "I60-" + UUID.randomUUID().toString().substring(0,12);
    static final List<String> checks = new ArrayList<>();
    static final Map<String,List<String>> primaryKeys = new TreeMap<>();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static long owner, dish, meal, dishCategory, mealCategory;
    static String base, token, adminToken, wrongScope;
    static volatile String fault = "off", faultName;
    static volatile boolean realFaultWrite, realConstraintFailure;
    static boolean businessPassed, cleanupPassed, springClosed, httpClosed;
    static String failure;
    static void check(String name, boolean passed) {
        if (!passed) throw new AssertionError(name);
        checks.add(name); System.out.println("ISSUE60 PASS "+name);
    }
    @Configuration(proxyBeanMethods = false)
    public static class Fixtures {
        @Bean ChildFailure childFailure() { return new ChildFailure(); }
        @Bean @Primary BaiduMapUtil isolatedMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop, String address, String unused) {
                    check("local_map_only_synthetic_address", "Isolation street".equals(shop) && address.contains(RUN)); return 7;
                }
            };
        }
        @Bean static BeanFactoryPostProcessor disableJobs() {
            return factory -> {
                var registry=(BeanDefinitionRegistry)factory;
                String scheduler="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if(registry.containsBeanDefinition(scheduler)) registry.removeBeanDefinition(scheduler);
            };
        }
    }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    public static class ChildFailure implements Interceptor {
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result=invocation.proceed();
            String statement=((MappedStatement)invocation.getArgs()[0]).getId();
            String expected=fault.equals("dish") ? "com.codeying.mapper.DishFlavorMapper.insert"
                    : fault.equals("setmeal") ? "com.codeying.mapper.SetmealDishMapper.insert" : "";
            if(!expected.isEmpty() && statement.equals(expected)) {
                ((Executor)invocation.getTarget()).flushStatements();
                String child=fault.equals("dish")?"dish_flavor":"setmeal_dish";
                String parent=fault.equals("dish")?"dish":"setmeal";
                String foreignKey=fault.equals("dish")?"dish_id":"setmeal_id";
                Long id=sql.queryForObject("SELECT id FROM "+parent+" WHERE name=?",Long.class,faultName);
                check(fault+"_main_and_child_actual_SQL_inside_transaction",
                        TransactionSynchronizationManager.isActualTransactionActive()
                        && count("SELECT COUNT(*) FROM "+child+" WHERE "+foreignKey+"=?",id)>0);
                realFaultWrite=true;
                // Actual later MySQL duplicate-PK failure after main+child persistence, no schema manipulation.
                try {
                    sql.update("INSERT INTO "+child+" SELECT * FROM "+child+" WHERE "+foreignKey+"=? LIMIT 1",id);
                } catch(org.springframework.dao.DuplicateKeyException ex) {
                    realConstraintFailure=true; throw ex;
                }
                throw new AssertionError("actual primary key failure required");
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
        for (String table : List.of("user", "address_book", "dish", "setmeal", "orders", "order_detail", "shopping_cart", "category", "dish_flavor", "setmeal_dish")) {
            check("expected_actual_id_key_" + table, List.of("id").equals(primaryKeys.get(table)));
            check("transactional_engine_" + table, "InnoDB".equals(sql.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", String.class, table)));
        }
        check("actual_detail_amount_nonnull_decimal", count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_detail' AND column_name='amount' AND is_nullable='NO' AND data_type='decimal'") == 1);
        check("actual_dish_unique_name", count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dish' AND column_name='name' AND non_unique=0") > 0);
        check("actual_catalog_decimal_10_2", count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('dish','setmeal') AND column_name='price' AND numeric_precision=10 AND numeric_scale=2 AND data_type='decimal'")==2);
        check("actual_copies_int", count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='setmeal_dish' AND column_name='copies' AND data_type='int'")==1);
        Files.writeString(run.resolve("schema-inspection.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }
    static Map<String, String> snapshot() throws Exception {
        var result = new TreeMap<String, String>();
        for (var entry : primaryKeys.entrySet()) {
            String order = String.join(",", entry.getValue().stream().map(Issue60Probe::identifier).toList());
            result.put(entry.getKey(), JSON.writeValueAsString(sql.queryForList("SELECT * FROM " + identifier(entry.getKey()) + " ORDER BY " + order)));
        }
        return result;
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
    static Map<String,Object> catalogBody(boolean isMeal) {
        var body=new LinkedHashMap<String,Object>();
        body.put("id",isMeal?meal:dish); body.put("name",RUN+(isMeal?"-meal-edit":"-dish-edit"));
        body.put("categoryId",isMeal?mealCategory:dishCategory); body.put("price",new BigDecimal("18.00"));
        body.put("image","/static/issue60.png"); body.put("status",1);
        if(isMeal) body.put("setmealDishes",List.of(Map.of("dishId",dish,"copies",3)));
        else body.put("flavors",List.of(Map.of("name","taste","value","[\"mild\"]")));
        return body;
    }
    static void refuse(HttpClient client, boolean isMeal, String method, String name, Map<String,Object> body) throws Exception {
        var before=snapshot();
        var response=request(client,method,"/admin/"+(isMeal?"setmeal":"dish"),adminToken,body);
        check((isMeal?"meal":"dish")+"_"+method+"_"+name+"_refused_existing_error_shape",
                !success(response) && !response.path("success").asBoolean()
                && !response.path("msg").asText().isBlank() && response.path("message").equals(response.path("msg")));
        check(name+"_no_SQL_writes",before.equals(snapshot()));
        if(name.equals("amount_-0.01")) check("DTO_validation_actually_triggered_"+(isMeal?"meal":"dish")+method,
                "金额不能为负数".equals(response.path("msg").asText()));
    }
    static void invalidInputs(HttpClient client) throws Exception {
        for(boolean isMeal:List.of(false,true)) for(String method:List.of("POST","PUT")) {
            for(String amount:List.of("-0.01","0.001","18.375","99999999.991","100000000","1E+20")) {
                var body=catalogBody(isMeal); body.put("price",new BigDecimal(amount));
                refuse(client,isMeal,method,"amount_"+amount,body);
            }
            for(long category:List.of(owner+99,isMeal?dishCategory:mealCategory)) {
                var body=catalogBody(isMeal); body.put("categoryId",category);
                refuse(client,isMeal,method,"missing_or_mismatched_category_"+category,body);
            }
            if(method.equals("POST")) for(String missing:List.of("price","categoryId")) {
                var body=catalogBody(isMeal); body.remove(missing); refuse(client,isMeal,method,"missing_"+missing,body);
            }
            if(!isMeal) continue;
            List<Object> cases=new ArrayList<>();
            cases.add(List.of()); cases.add(Arrays.asList((Object)null));
            cases.add(List.of(Map.of("copies",1)));
            cases.add(List.of(Map.of("dishId",owner+99,"copies",1)));
            cases.add(List.of(Map.of("dishId",dish)));
            for(Object copies:List.of(0,-1,new BigDecimal("1.5"),2147483648L,"1.5","bad"))
                cases.add(List.of(Map.of("dishId",dish,"copies",copies)));
            var nullCopies=new LinkedHashMap<String,Object>(); nullCopies.put("dishId",dish); nullCopies.put("copies",null);
            cases.add(List.of(nullCopies));
            int n=0;
            for(Object bad:cases) {
                var body=catalogBody(true); body.put("setmealDishes",bad);
                refuse(client,true,method,"invalid_details_"+(n++),body);
            }
            var mixed=new ArrayList<Object>(); mixed.add(Map.of("dishId",dish,"copies",3)); mixed.add(null);
            var body=catalogBody(true); body.put("setmealDishes",mixed); refuse(client,true,method,"no_silent_filter",body);
            body=catalogBody(true); body.put("setmealDishes",List.of(Map.of("dishId",dish,"copies",3),Map.of("dishId",owner+99,"copies",1)));
            refuse(client,true,method,"no_missing_dish_filter",body);
            if(method.equals("POST")) { body=catalogBody(true); body.remove("setmealDishes"); refuse(client,true,method,"missing_details",body); }
        }
    }
    static void retainedInvalidInputs(HttpClient client) throws Exception {
        // These states fit the inspected existing schema; impossible precision/null-price states stay in unit tests.
        sql.update("DELETE FROM setmeal_dish WHERE setmeal_id=?",meal);
        var partial=new LinkedHashMap<String,Object>(); partial.put("id",meal); partial.put("name",RUN+"-retained");
        refuse(client,true,"PUT","retained_empty_details",partial);
        sql.update("INSERT INTO setmeal_dish(id,setmeal_id,dish_id,copies) VALUES(?,?,?,0)",owner,meal,dish);
        refuse(client,true,"PUT","retained_bad_copies",partial);
        sql.update("UPDATE setmeal_dish SET copies=3 WHERE setmeal_id=?",meal);
        for(boolean isMeal:List.of(false,true)) {
            String table=isMeal?"setmeal":"dish"; long id=isMeal?meal:dish;
            var body=new LinkedHashMap<String,Object>(); body.put("id",id); body.put("name",RUN+"-retained");
            sql.update("UPDATE "+table+" SET price=-1 WHERE id=?",id);
            refuse(client,isMeal,"PUT","retained_invalid_money",body);
            sql.update("UPDATE "+table+" SET price=18 WHERE id=?",id);
            sql.update("UPDATE "+table+" SET category_id=? WHERE id=?",isMeal?dishCategory:mealCategory,id);
            refuse(client,isMeal,"PUT","retained_wrong_category",body);
            sql.update("UPDATE "+table+" SET category_id=? WHERE id=?",isMeal?mealCategory:dishCategory,id);
        }
    }
    static void seed() {
        sql.update("INSERT INTO category(id,type,name,sort,status) VALUES(?,1,?,1,1),(?,2,?,1,1)",dishCategory,RUN+"-dc",mealCategory,RUN+"-mc");
        sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,?)",owner,RUN,RUN);
        sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue60','00000000000',?,1)",owner,owner,RUN+" Isolation street");
        sql.update("INSERT INTO dish(id,name,category_id,price,image,status) VALUES(?,?,?,18.00,'/static/original60.png',0)",dish,RUN+"-dish-original",dishCategory);
        sql.update("INSERT INTO dish_flavor(id,dish_id,name,value) VALUES(?,?,'original','[\"mild\"]')",owner,dish);
        sql.update("INSERT INTO setmeal(id,name,category_id,price,image,status) VALUES(?,?,?,18.00,'/static/meal60.png',1)",meal,RUN+"-meal-original",mealCategory);
        sql.update("INSERT INTO setmeal_dish(id,setmeal_id,dish_id,copies) VALUES(?,?,?,3)",owner,meal,dish);
    }
    static void cleanup() {
        fault="off";
        sql.update("DELETE d FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id=?",owner);
        sql.update("DELETE FROM orders WHERE user_id=?",owner);
        sql.update("DELETE FROM shopping_cart WHERE user_id=?",owner);
        sql.update("DELETE FROM address_book WHERE id=? AND user_id=?",owner,owner);
        // Every prefix was proved absent before writes. Only this run's successfully created catalogs qualify.
        sql.update("DELETE d FROM dish_flavor d JOIN dish p ON p.id=d.dish_id WHERE p.name LIKE ?",RUN+"-%");
        sql.update("DELETE d FROM setmeal_dish d JOIN setmeal p ON p.id=d.setmeal_id WHERE p.name LIKE ?",RUN+"-%");
        sql.update("DELETE FROM setmeal WHERE name LIKE ?",RUN+"-%");
        sql.update("DELETE FROM dish WHERE name LIKE ?",RUN+"-%");
        sql.update("DELETE FROM category WHERE id IN (?,?)",dishCategory,mealCategory);
        sql.update("DELETE FROM user WHERE id=?",owner);
    }
    static void validAndPartial(HttpClient client) throws Exception {
        for(boolean isMeal:List.of(false,true)) {
            String table=isMeal?"setmeal":"dish";
            for(String method:List.of("POST","PUT")) for(String amount:List.of("0","18.50","99999999.99","1.000")) {
                var body=catalogBody(isMeal); String name=RUN+"-"+table.charAt(0)+"-"+method+"-"+List.of("0","18.50","99999999.99","1.000").indexOf(amount);
                body.put("name",name); body.put("price",new BigDecimal(amount));
                if(!isMeal) body.put("status",0);
                var response=request(client,method,"/admin/"+table,adminToken,body);
                check(table+"_"+method+"_exact_"+amount,success(response)
                        && sql.queryForObject("SELECT price FROM "+table+" WHERE name=?",BigDecimal.class,name).compareTo(new BigDecimal(amount))==0);
                if(isMeal) check("stopped_component_and_copies3_persisted",count("SELECT COUNT(*) FROM setmeal_dish d JOIN setmeal s ON s.id=d.setmeal_id WHERE s.name=? AND d.dish_id=? AND d.copies=3",name,dish)==1 && count("SELECT COUNT(*) FROM dish WHERE id=? AND status=0",dish)==1);
            }
            // Explicit nulls match omitted fields in the established partial update contract.
            var before=sql.queryForList("SELECT category_id,price FROM "+table+" WHERE id=?",isMeal?meal:dish);
            var children=isMeal?sql.queryForList("SELECT * FROM setmeal_dish WHERE setmeal_id=? ORDER BY id",meal):List.of();
            var partial=new LinkedHashMap<String,Object>(); partial.put("id",isMeal?meal:dish);
            partial.put("name",RUN+"-"+table+"-partial"); partial.put("price",null); partial.put("categoryId",null);
            if(isMeal) partial.put("setmealDishes",null);
            check(table+"_partial_update_compatible",success(request(client,"PUT","/admin/"+table,adminToken,partial))
                    && before.equals(sql.queryForList("SELECT category_id,price FROM "+table+" WHERE id=?",isMeal?meal:dish))
                    && (!isMeal || children.equals(sql.queryForList("SELECT * FROM setmeal_dish WHERE setmeal_id=? ORDER BY id",meal))));
        }
        for(Object copies:List.of(1,3,Integer.MAX_VALUE,"3")) {
            var value=catalogBody(true); value.put("setmealDishes",List.of(Map.of("dishId",dish,"copies",copies)));
            check("positive_int_copies_"+copies,success(request(client,"PUT","/admin/setmeal",adminToken,value))
                    && count("SELECT COUNT(*) FROM setmeal_dish WHERE setmeal_id=? AND copies=?",meal,Integer.valueOf(copies.toString()))==1);
        }
        // Re-establish stopped composition through public status entry, then independently saleable setmeal.
        check("stop_constituent_without_cascade",success(request(client,"POST","/admin/dish/status/0?id="+dish,adminToken,null)));
        var body=catalogBody(true); body.put("price",BigDecimal.ZERO); body.put("name",RUN+"-zero-meal");
        check("saleable_zero_setmeal_with_stopped_dish",success(request(client,"PUT","/admin/setmeal",adminToken,body)));
        body=catalogBody(false); body.put("price",BigDecimal.ZERO); body.put("name",RUN+"-zero-dish");
        check("saleable_zero_dish_saved",success(request(client,"PUT","/admin/dish",adminToken,body)));
        check("re_stop_component",success(request(client,"POST","/admin/dish/status/0?id="+dish,adminToken,null)));
        var before=snapshot();
        check("linked_dish_deletion_still_refused",!success(request(client,"DELETE","/admin/dish?ids="+dish,adminToken,null)) && before.equals(snapshot()));
        var catalog=request(client,"GET","/user/setmeal/list?categoryId="+mealCategory,token,null);
        boolean found=false; for(var item:catalog.path("data")) if(item.path("id").asLong()==meal) found=item.path("price").decimalValue().signum()==0;
        check("legal_user_catalog_zero_setmeal_visible_despite_stopped_component",success(catalog)&&found);
        var components=request(client,"GET","/user/setmeal/dish/"+meal,token,null);
        check("user_stopped_component_details_and_copies3_readable",success(components) && components.path("data").size()==1
                && components.path("data").get(0).path("copies").asInt()==3
                && (RUN+"-zero-dish").equals(components.path("data").get(0).path("name").asText()));
        check("zero_setmeal_added_via_real_owned_cart",success(request(client,"POST","/user/shoppingCart/add",token,Map.of("setmealId",meal))));
        var cart=request(client,"GET","/user/shoppingCart/list",token,null);
        check("zero_setmeal_trusted_cart",success(cart) && cart.path("data").size()==1 && cart.path("data").get(0).path("available").asBoolean()
                && cart.path("data").get(0).path("amount").decimalValue().signum()==0);
        var submit=new LinkedHashMap<String,Object>(); submit.put("addressBookId",owner); submit.put("amount",99999); submit.put("packAmount",-99);
        submit.put("payMethod",1); submit.put("tablewareStatus",0); submit.put("tablewareNumber",1); submit.put("deliveryStatus",1);
        var order=request(client,"POST","/user/order/submit",token,submit);
        check("zero_catalog_keeps_trusted_billing_1_packing_plus_2_delivery",success(order)
                && order.path("data").path("orderAmount").decimalValue().compareTo(new BigDecimal("3.00"))==0);
        long orderId=order.path("data").path("id").asLong();
        check("zero_item_persisted_with_server_fees_and_cart_cleared",count("SELECT COUNT(*) FROM orders WHERE id=? AND user_id=? AND amount=3.00 AND pack_amount=1",orderId,owner)==1
                && count("SELECT COUNT(*) FROM order_detail WHERE order_id=? AND setmeal_id=? AND amount=0.00",orderId,meal)==1
                && count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?",owner)==0);
        check("reopen_zero_dish",success(request(client,"POST","/admin/dish/status/1?id="+dish,adminToken,null)));
        check("zero_dish_added_via_owned_cart",success(request(client,"POST","/user/shoppingCart/add",token,Map.of("dishId",dish))));
        var dishOrder=request(client,"POST","/user/order/submit",token,submit);
        check("zero_dish_same_trusted_billing",success(dishOrder)&&dishOrder.path("data").path("orderAmount").decimalValue().compareTo(new BigDecimal("3.00"))==0);
    }
    static void rollback(HttpClient client) throws Exception {
        for(boolean isMeal:List.of(false,true)) for(String method:List.of("POST","PUT")) {
            var before=snapshot(); var body=catalogBody(isMeal);
            faultName=RUN+"-fault-"+(isMeal?"meal":"dish")+"-"+method;
            body.put("name",faultName); fault=isMeal?"setmeal":"dish"; realFaultWrite=false; realConstraintFailure=false;
            JsonNode response;
            try { response=request(client,method,"/admin/"+(isMeal?"setmeal":"dish"),adminToken,body); }
            finally { fault="off"; }
            check((isMeal?"meal":"dish")+"_"+method+"_actual_later_child_SQL_failure",!success(response)&&realFaultWrite&&realConstraintFailure);
            check(method+"_full_main_and_original_children_rollback",before.equals(snapshot()));
        }
        check("valid_save_recovers_after_rollback",success(request(client,"PUT","/admin/setmeal",adminToken,catalogBody(true))));
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(), run = Path.of(args[1]).toAbsolutePath();
        check("outputs_in_project", run.startsWith(root.resolve(".scratch/issue60")));
        long id = 6_000_000_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        owner=id; dish=id+1; meal=id+2; dishCategory=id+3; mealCategory=id+4;
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
                check("actual_spring_catalog_transaction_proxies", AopUtils.isAopProxy(context.getBean(DishApplicationService.class))
                        && AopUtils.isAopProxy(context.getBean(SetmealApplicationService.class)));
                check("all_scheduled_jobs_disabled", !context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
                inspectSchema(run);
                check("both_synthetic_markers",count("SELECT COUNT(*) FROM user WHERE id=900002 AND openid='sandbox-user-b'")==1);
                for(String table:List.of("user","address_book","dish","setmeal","category","dish_flavor","setmeal_dish","shopping_cart","orders","order_detail")) {
                    check("fresh_UUID_scope_"+table,count("SELECT COUNT(*) FROM "+identifier(table)+" WHERE id BETWEEN ? AND ?",id,id+20)==0);
                }
                for(String table:List.of("dish","setmeal","category")) check("fresh_name_prefix_"+table,count("SELECT COUNT(*) FROM "+table+" WHERE name LIKE ?",RUN+"-%")==0);
                check("fresh_cleanup_owner_scopes",count("SELECT COUNT(*) FROM orders WHERE user_id=?",owner)==0
                        &&count("SELECT COUNT(*) FROM shopping_cart WHERE user_id=?",owner)==0
                        &&count("SELECT COUNT(*) FROM address_book WHERE user_id=?",owner)==0);
                var original=snapshot(); var originalRedis=unrelatedRedis();
                String key=RedisKeys.shopStatusKey(); var originalStatus=RedisValue.read(key);
                var jwt=context.getBean(SkyProperties.class).getJwt();
                token=JwtUtil.createToken(jwt.getUserSecretKey(),600000,owner,"fixture","user");
                wrongScope=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,owner,"fixture","user");
                base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
                try {
                    seed();
                    var beforeLogin=snapshot();
                    // Read only the verified synthetic fixture credential into memory; never retain it or the token.
                    String fixtureCredential=sql.queryForObject("SELECT password FROM employee WHERE id=900001 AND username='sandbox_admin' AND name='Sandcastle fixture'",String.class);
                    var login=request(client,"POST","/admin/employee/login",null,Map.of("username","sandbox_admin","password",fixtureCredential));
                    check("real_fixture_admin_login_identity",success(login) && login.path("data").path("id").asLong()==900001
                            && !login.path("data").path("token").asText().isBlank());
                    adminToken=login.path("data").path("token").asText();
                    check("legitimate_login_admin_subject_and_no_SQL_writes","admin".equals(JwtUtil.parseClaims(adminToken,jwt.getAdminSecretKey()).getSubject())
                            && beforeLogin.equals(snapshot()));
                    check("existing_admin_shop_OPEN",success(request(client,"PUT","/admin/shop/1",adminToken,null)) && shop.getStatus()==1);
                    var initial=snapshot();
                    for(boolean isMeal:List.of(false,true)) for(String method:List.of("POST","PUT")) {
                        String path="/admin/"+(isMeal?"setmeal":"dish");
                        check("anonymous_"+method+path+"_refused",!success(request(client,method,path,null,catalogBody(isMeal))));
                        check("wrong_scope_"+method+path+"_refused",!success(request(client,method,path,wrongScope,catalogBody(isMeal))));
                        check("normal_user_credential_"+method+path+"_refused",!success(request(client,method,path,token,catalogBody(isMeal))));
                    }
                    for(boolean isMeal:List.of(false,true)) for(String method:List.of("POST","PUT")) {
                        String path="/admin/"+(isMeal?"setmeal":"dish")+";x=1";
                        for(String credential:List.of("anonymous","user","wrong-scope","admin")) {
                            String auth=switch(credential) { case "anonymous" -> null; case "user" -> token;
                                case "wrong-scope" -> wrongScope; default -> adminToken; };
                            var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
                                    .header("Content-Type","application/json");
                            if(auth!=null) builder.header("token",auth);
                            var response=client.send(builder.method(method,HttpRequest.BodyPublishers.ofString(credential.equals("admin")
                                            ? "{\"price\":-1}" : JSON.writeValueAsString(catalogBody(isMeal))))
                                    .build(),HttpResponse.BodyHandlers.ofString());
                            check("matrix_"+credential+"_"+method+path,
                                    credential.equals("admin") ? response.statusCode()==200 && !success(JSON.readTree(response.body()))
                                            : response.statusCode()==401);
                        }
                    }
                    check("auth_refusals_no_SQL_writes",initial.equals(snapshot()));
                    // A legitimate administrator must also persist valid payloads on MVC-equivalent matrix URLs.
                    for(boolean isMeal:List.of(false,true)) for(String method:List.of("POST","PUT")) {
                        String table=isMeal?"setmeal":"dish";
                        var body=catalogBody(isMeal);
                        String name=RUN+"-mx-"+table.charAt(0)+"-"+method;
                        body.put("name",name);
                        if(!isMeal) body.put("status",0);
                        check("matrix_admin_valid_"+method+"_"+table+"_persists",
                                success(request(client,method,"/admin/"+table+";x=1",adminToken,body))
                                && count("SELECT COUNT(*) FROM "+table+" WHERE name=? AND price=18.00",name)==1);
                    }
                    invalidInputs(client);
                    retainedInvalidInputs(client);
                    rollback(client);
                    validAndPartial(client);
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
            System.out.println("ISSUE60 FAIL "+failure);
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
        System.out.println("ISSUE60 ALL_SCENARIOS_PASS assertions="+checks.size());
    }
}
