import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.service.OrdersApplicationService;
import com.codeying.service.WechatService;
import com.codeying.utils.BaiduMapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;

/** One connected acceptance chain; only fresh synthetic rows on the dedicated internal fixture. */
public class Issue21Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String RUN = "i21_" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
    static final String PASSWORD = "issue21-synthetic-password";
    static final List<String> TABLES = List.of("employee","user","category","dish","dish_flavor","setmeal",
            "setmeal_dish","address_book","shopping_cart","orders","order_detail",
            "order_risk_result","order_risk_feature_snapshot");
    static final List<Map<String,Object>> scenarios = new ArrayList<>();
    static final List<String> cleanupFailures = new ArrayList<>();
    static final Map<String,Object> resources = new LinkedHashMap<>();
    static JdbcTemplate sql;
    static String base;
    static long owner, other, employee, category, dish, order;
    static Path out;

    @Configuration(proxyBeanMethods=false)
    public static class Fixtures {
        @Bean @Primary WechatService syntheticLogin() {
            return code -> {
                if (!code.startsWith("mock_"+RUN)) throw new IllegalArgumentException("synthetic login only");
                return "mock_openid_"+code;
            };
        }
        @Bean @Primary BaiduMapUtil localMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop,String address,String key) {
                    check("map_fixture_boundary", "Isolation street".equals(shop) && address.contains(RUN));
                    return 7;
                }
            };
        }
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor disableSchedules() {
            return factory -> {
                var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
                String name="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if(registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            };
        }
    }
    static void check(String name, boolean passed) {
        scenarios.add(Map.of("scenario",name,"passed",passed));
        System.out.println("ISSUE21 "+(passed?"PASS ":"FAIL ")+name);
        if(!passed) throw new AssertionError(name);
    }
    static long insert(String statement,Object... values) {
        var keys=new GeneratedKeyHolder();
        sql.update(c -> {
            var s=c.prepareStatement(statement,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]);
            return s;
        },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    static HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var r=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
        if(token!=null) r.header(path.startsWith("/admin/")?"token":"authentication",token);
        if(body!=null) r.header("Content-Type","application/json");
        r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(r.build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode ok(String method,String path,String token,Object body) throws Exception {
        var result=JSON.readTree(call(method,path,token,body).body());
        check("HTTP_"+method+"_"+path.split("\\?")[0],result.path("code").asInt(-1)==1);
        return result.path("data");
    }
    static void denied(String method,String path,String token,Object body) throws Exception {
        var result=JSON.readTree(call(method,path,token,body).body());
        check("refused_"+method+"_"+path.split("\\?")[0],result.path("code").asInt(-1)==0);
    }
    static Map<String,List<Map<String,Object>>> snapshot() {
        var rows=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:TABLES) {
            String key=switch(table) {
                case "order_risk_result" -> "order_id,model_version";
                case "order_risk_feature_snapshot" -> "order_id,feature_version";
                default -> "id";
            };
            rows.put(table,sql.queryForList("SELECT * FROM `"+table+"` ORDER BY "+key));
        }
        return rows;
    }
    static boolean money(JsonNode n,String amount) { return n.isNumber() && n.decimalValue().compareTo(new BigDecimal(amount))==0; }
    static void state(String user,String admin,int status) throws Exception {
        var u=ok("GET","/user/order/orderDetail/"+order,user,null);
        var a=ok("GET","/admin/order/details/"+order,admin,null);
        var row=sql.queryForMap("SELECT * FROM orders WHERE id=?",order);
        check("state_"+status+"_same_user_admin_SQL",u.path("status").asInt()==status && a.path("status").asInt()==status
                && ((Number)row.get("status")).intValue()==status && money(u.path("amount"),"21") && money(a.path("amount"),"21")
                && ((BigDecimal)row.get("amount")).compareTo(new BigDecimal("21"))==0);
        check("state_"+status+"_paid",u.path("payStatus").asInt()==1 && a.path("payStatus").asInt()==1 && row.get("checkout_time")!=null);
        if(status==5) check("completed_delivery_time",row.get("delivery_time")!=null && !u.path("deliveryTime").isNull() && !a.path("deliveryTime").isNull());
    }
    static void cleanup(String name,Runnable action) {
        try { action.run(); }
        catch(Exception|AssertionError failure) { cleanupFailures.add(name+":"+failure.getClass().getSimpleName()); }
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(); out=Path.of(args[1]).toAbsolutePath();
        String started=Instant.now().toString(),failure="";
        boolean isolated=false,business=false,closed=false;
        Map<String,List<Map<String,Object>>> original=null;
        Map<String,byte[]> cache=new TreeMap<>();
        var app=new SpringApplication(App.class,Fixtures.class);
        var context=app.run("--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk","--server.address=127.0.0.1","--server.port=0","--logging.level.root=OFF",
                "--server.tomcat.basedir="+out.resolve("tomcat"),"--analysis.task.enabled=false","--order-risk.task.scoring-enabled=false",
                "--analysis.xgboost.model-dir="+out.resolve("analysis-models"),"--order-risk.model-dir="+out.resolve("risk-models"));
        sql=context.getBean(JdbcTemplate.class); var redis=context.getBean(StringRedisTemplate.class);
        base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
        try {
            try(var c=sql.getDataSource().getConnection()) {
                check("dedicated_catalog_before_writes","sandcastle_fixture".equals(c.getCatalog()) && c.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture?"));
            }
            check("dedicated_redis_before_writes","redis".equals(context.getEnvironment().getProperty("spring.data.redis.host")) && "0".equals(context.getEnvironment().getProperty("spring.data.redis.database")));
            check("real_order_transaction_proxy",AopUtils.isAopProxy(context.getBean(OrdersApplicationService.class)));
            original=snapshot(); for(String k:Objects.requireNonNull(redis.keys("*"))) cache.put(k,redis.dump(k));
            isolated=true;
            resources.put("catalog","sandcastle_fixture"); resources.put("redis","redis:6379/db0, owned user context keys only");
            resources.put("files",out.toString()); resources.put("externalRequests",false); resources.put("run",RUN);
            var ddl=sql.queryForMap("SHOW CREATE TABLE orders").values().toString();
            check("actual_schema_amount_decimal_ids_bigint",ddl.contains("decimal(10,2)") && ddl.contains("`id` bigint") && ddl.contains("ENGINE=InnoDB"));
            employee=insert("INSERT INTO employee(username,password,name,phone,sex,id_number,status,create_time,update_time,create_user,update_user) VALUES(?,?,?,'00000000000','1','synthetic',1,NOW(),NOW(),900001,900001)",RUN,PASSWORD,RUN);
            var invalid=JSON.readTree(call("POST","/admin/employee/login",null,Map.of("username",RUN,"password","wrong")).body());
            check("normal_password_invalid_refusal_no_token",invalid.path("code").asInt()==0 && invalid.path("data").path("token").asText().isEmpty());
            String admin=ok("POST","/admin/employee/login",null,Map.of("username",RUN,"password",PASSWORD)).path("token").asText();
            check("normal_password_token_issued",!admin.isBlank());
            var ua=ok("POST","/user/user/login",null,Map.of("code","mock_"+RUN+"a")); owner=ua.path("id").asLong(); String user=ua.path("token").asText();
            var ub=ok("POST","/user/user/login",null,Map.of("code","mock_"+RUN+"b")); other=ub.path("id").asLong(); String foreign=ub.path("token").asText();
            check("synthetic_login_distinct_identities",owner>0 && other>0 && owner!=other);
            category=insert("INSERT INTO category(type,name,sort,status,create_time,update_time,create_user,update_user) VALUES(1,?,1,1,NOW(),NOW(),?,?)",RUN,employee,employee);
            dish=insert("INSERT INTO dish(name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(?,?,18,'',1,NOW(),NOW(),?,?)",RUN,category,employee,employee);
            var cats=ok("GET","/user/category/list?type=1",null,null); boolean found=false;
            for(var c:cats) if(c.path("id").asLong()==category) found=true;
            check("public_catalog_owned_category",found);
            var dishes=ok("GET","/user/dish/list?categoryId="+category,null,null);
            check("public_catalog_saleable18_missing_image",dishes.size()==1 && dishes.get(0).path("id").asLong()==dish && money(dishes.get(0).path("price"),"18") && dishes.get(0).path("image").asText().isEmpty());
            ok("POST","/user/shoppingCart/add",user,Map.of("dishId",dish,"amount",0.01,"userId",other));
            var cart=ok("GET","/user/shoppingCart/list",user,null);
            check("cart_owned_trusted18",cart.size()==1 && money(cart.get(0).path("amount"),"18") && cart.get(0).path("userId").asLong()==owner);
            check("other_cart_empty",ok("GET","/user/shoppingCart/list",foreign,null).isEmpty());
            ok("POST","/user/addressBook",user,Map.of("consignee",RUN,"phone","00000000000","detail",RUN+" door","sex","1","isDefault",1));
            var address=ok("GET","/user/addressBook/default",user,null); long aid=address.path("id").asLong();
            check("owned_default_address",aid>0 && address.path("userId").asLong()==owner && address.path("isDefault").asInt()==1);
            denied("GET","/user/addressBook/"+aid,foreign,null);
            // An absent historical record is replaced only by an explicitly synthetic isolated fixture.
            boolean synthetic974=sql.queryForObject("SELECT COUNT(*) FROM orders WHERE id=974",Integer.class)==0;
            if(synthetic974) {
                sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,pay_method,amount,pack_amount,remark) VALUES(974,?,5,?,?, '2020-01-01 12:00:00',1,1,19,1,?)",RUN+"-historical",owner,aid,RUN);
                insert("INSERT INTO order_detail(name,order_id,dish_id,number,amount,image) VALUES(?,974,?,1,18,'issue21-historical-only.png')",RUN,dish);
            }
            resources.put("historical974",synthetic974?"synthetic isolated replacement, not missing original evidence":"existing dedicated fixture, unchanged");
            var historicalRows=sql.queryForList("SELECT * FROM orders WHERE id=974");
            var historicalDetails=sql.queryForList("SELECT * FROM order_detail WHERE order_id=974 ORDER BY id");
            check("historical974_stored19",((BigDecimal)historicalRows.get(0).get("amount")).compareTo(new BigDecimal("19"))==0);
            check("historical974_admin19",money(ok("GET","/admin/order/details/974",admin,null).path("amount"),"19"));
            if(synthetic974) {
                var old=ok("GET","/user/order/orderDetail/974",user,null);
                check("historical974_owner19_original_image",money(old.path("amount"),"19") && "issue21-historical-only.png".equals(old.path("orderDetailList").get(0).path("image").asText()));
            }
            Map<String,Object> body=Map.of("addressBookId",aid,"payMethod",1,"amount",0.01,"packAmount",0,"userId",other,"deliveryStatus",1,"tablewareStatus",0,"remark",RUN);
            var before=snapshot(); denied("POST","/user/order/submit",foreign,body);
            check("foreign_submit_no_write",before.equals(snapshot()));
            check("anonymous_submit_401",call("POST","/user/order/submit",null,body).statusCode()==401);
            var submitted=ok("POST","/user/order/submit",user,body); order=submitted.path("id").asLong();
            check("trusted_submit21",order>0 && money(submitted.path("orderAmount"),"21"));
            var detail=sql.queryForMap("SELECT * FROM order_detail WHERE order_id=?",order);
            var stored=sql.queryForMap("SELECT * FROM orders WHERE id=?",order);
            check("SQL_trusted_items_pack_delivery",((Number)stored.get("user_id")).longValue()==owner && ((Number)stored.get("pack_amount")).intValue()==1 && ((BigDecimal)stored.get("amount")).compareTo(new BigDecimal("21"))==0 && ((Number)detail.get("number")).intValue()==1 && ((BigDecimal)detail.get("amount")).compareTo(new BigDecimal("18"))==0);
            check("submitted_cart_cleared",ok("GET","/user/shoppingCart/list",user,null).isEmpty());
            var pay=Map.of("orderNumber",submitted.path("orderNumber").asText()); before=snapshot();
            denied("PUT","/user/order/payment",foreign,pay); denied("GET","/user/order/orderDetail/"+order,foreign,null);
            check("foreign_payment_detail_no_write",before.equals(snapshot()));
            check("anonymous_history_401",call("GET","/user/order/historyOrders?page=1&pageSize=10",null,null).statusCode()==401);
            check("user_admin_scope_401",call("PUT","/admin/order/confirm",user,Map.of("id",order)).statusCode()==401);
            check("anonymous_admin_401",call("PUT","/admin/order/confirm",null,Map.of("id",order)).statusCode()==401);
            var paid=ok("PUT","/user/order/payment",user,pay);
            check("mock_payment_response",paid.path("nonceStr").asText().equals("mock") && paid.path("packageStr").asText().equals("prepay_id=mock")); state(user,admin,2);
            ok("PUT","/admin/order/confirm",admin,Map.of("id",order)); state(user,admin,3);
            ok("PUT","/admin/order/delivery/"+order,admin,null); state(user,admin,4);
            ok("PUT","/admin/order/complete/"+order,admin,null); state(user,admin,5);
            var history=ok("GET","/user/order/historyOrders?page=1&pageSize=10",user,null);
            check("same_user_completed_history21",history.path("total").asInt()==(synthetic974?2:1) && history.path("records").get(0).path("id").asLong()==order && history.path("records").get(0).path("status").asInt()==5 && money(history.path("records").get(0).path("amount"),"21"));
            check("foreign_history_empty",ok("GET","/user/order/historyOrders?page=1&pageSize=10",foreign,null).path("total").asInt()==0);
            before=snapshot(); ok("GET","/user/order/orderDetail/"+order,user,null); ok("GET","/admin/order/details/"+order,admin,null);
            check("history_details_images_never_written",before.equals(snapshot()) && sql.queryForObject("SELECT image FROM order_detail WHERE order_id=?",String.class,order).isEmpty());
            check("historical974_amount_images_never_written",historicalRows.equals(sql.queryForList("SELECT * FROM orders WHERE id=974")) && historicalDetails.equals(sql.queryForList("SELECT * FROM order_detail WHERE order_id=974 ORDER BY id")));
            String today=LocalDate.now().toString(); String range="?begin="+today+"&end="+today;
            long completed=sql.queryForObject("SELECT COUNT(*) FROM orders WHERE status=5 AND DATE(order_time)=?",Long.class,today);
            BigDecimal turnover=sql.queryForObject("SELECT COALESCE(SUM(amount),0) FROM orders WHERE status=5 AND DATE(order_time)=?",BigDecimal.class,today);
            var report=ok("GET","/admin/report/turnoverStatistics"+range,admin,null);
            check("report_turnover_real_SQL",new BigDecimal(report.path("turnoverList").asText()).compareTo(turnover)==0);
            var stats=ok("GET","/admin/report/ordersStatistics"+range,admin,null);
            check("report_completed_real_SQL",stats.path("validOrderCount").asLong()==completed);
            var top=ok("GET","/admin/report/top10"+range,admin,null);
            check("report_top10_connected_order",top.path("nameList").asText().contains(RUN));
            var workspace=ok("GET","/admin/workspace/businessData",admin,null);
            check("workspace_turnover_completed_real_SQL",money(workspace.path("turnover"),turnover.toPlainString()) && workspace.path("validOrderCount").asLong()==completed);
            var overview=ok("GET","/admin/workspace/overviewOrders",admin,null);
            check("workspace_all_orders_real_SQL",overview.path("allOrders").asLong()==sql.queryForObject("SELECT COUNT(*) FROM orders",Long.class));
            var goods=ok("GET","/admin/workspace/overviewDishes",admin,null);
            check("workspace_saleable_real_SQL",goods.path("sold").asLong()==sql.queryForObject("SELECT COUNT(*) FROM dish WHERE status=1",Long.class));
            check("dev_shop_open_explicit_profile",ok("GET","/user/shop/status",null,null).asInt(-1)==1 && ok("GET","/admin/shop/status",admin,null).asInt(-1)==1);
            denied("GET","/admin/report/ordersStatistics?begin=2026-10-15&end=2026-10-14",admin,null);
            business=true;
        } catch(Exception|AssertionError ex) {
            failure=ex.getClass().getSimpleName(); System.out.println("ISSUE21 FAILURE class="+failure);
            for(var f:ex.getStackTrace()) if(f.getClassName().equals("Issue21Probe")) {System.out.println("ISSUE21 LOCATION "+f.getMethodName()+":"+f.getLineNumber());break;}
        } finally {
            if(isolated) {
                cleanup("owned_rows",()-> {
                    var ownedUsers=sql.queryForList("SELECT id FROM user WHERE openid IN (?,?)",Long.class,"mock_openid_mock_"+RUN+"a","mock_openid_mock_"+RUN+"b");
                    for(long uid:ownedUsers) {
                        for(long oid:sql.queryForList("SELECT id FROM orders WHERE user_id=? AND remark=?",Long.class,uid,RUN)) {
                            sql.update("DELETE FROM order_risk_result WHERE order_id=?",oid); sql.update("DELETE FROM order_risk_feature_snapshot WHERE order_id=?",oid);
                            sql.update("DELETE FROM order_detail WHERE order_id=?",oid); sql.update("DELETE FROM orders WHERE id=? AND user_id=? AND remark=?",oid,uid,RUN);
                        }
                        sql.update("DELETE FROM shopping_cart WHERE user_id=?",uid); sql.update("DELETE FROM address_book WHERE user_id=?",uid);
                        redis.delete(RedisKeys.aiRecommendContextKey(uid));
                    }
                    sql.update("DELETE FROM user WHERE openid IN (?,?)","mock_openid_mock_"+RUN+"a","mock_openid_mock_"+RUN+"b");
                    sql.update("DELETE FROM dish WHERE name=? AND category_id=?",RUN,category);
                    sql.update("DELETE FROM category WHERE name=?",RUN); sql.update("DELETE FROM employee WHERE username=? AND name=?",RUN,RUN);
                });
                var saved=original;
                cleanup("original_rows",()->check("original_fixture_rows_preserved",saved.equals(snapshot())));
                cleanup("original_redis",()-> {
                    check("original_redis_keys_preserved",cache.keySet().equals(redis.keys("*")));
                    for(var e:cache.entrySet()) check("original_redis_value_preserved",Arrays.equals(e.getValue(),redis.dump(e.getKey())));
                });
            }
            cleanup("spring_context",context::close); closed=!context.isActive();
            cleanup("http_client",HTTP::close);
            resources.put("springClosed",closed); resources.put("ownedRowsRemoved",isolated && cleanupFailures.isEmpty());
            resources.put("httpClientClosed",HTTP.isTerminated());
            resources.put("fixtureIds",Map.of("employee",employee,"category",category,"dish",dish,"owner",owner,"other",other,"order",order));
            var result=new LinkedHashMap<String,Object>();
            result.put("started",started); result.put("finished",Instant.now().toString()); result.put("cwd",root.toString());
            result.put("businessPassed",business); result.put("cleanupPassed",isolated && closed && cleanupFailures.isEmpty());
            result.put("failureClass",failure); result.put("cleanupFailures",cleanupFailures); result.put("scenarios",scenarios);
            result.put("resources",resources); result.put("userIdentity","synthetic WechatService fixture, no real wx.login");
            Files.writeString(out.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            Files.writeString(out.resolve("owned-resources.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(resources));
        }
        if(!business || !closed || !cleanupFailures.isEmpty()) System.exit(1);
    }
}
