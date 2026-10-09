import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.service.*;
import com.codeying.utils.BaiduMapUtil;
import com.codeying.utils.JwtUtil;
import com.codeying.properties.SkyProperties;
import com.codeying.vo.common.ai_assistant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Standalone synthetic fixture probe. Never run against original data. */
public class Issue13Probe {
    static final List<String> sqlFailures = new ArrayList<>();
    static Path projectRoot;
    static FaultRedis fault;
    static String mode = "off";
    static String model;
    static long user, other;
    static String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<Map<String,Object>> results = new ArrayList<>();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static AnalysisObservationService observation;
    static OrdersApplicationService orders;
    static ShoppingCartService cart;
    static String token, adminToken, baseUrl;
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @Configuration
    public static class Fixtures {
        @Bean public org.apache.ibatis.plugin.Interceptor failureEvidence() { return new SqlEvidence(); }
        @Bean @Primary public BaiduMapUtil localMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String a,String b,String c) { return 5; }
            };
        }
        @Bean @Primary public com.codeying.utils.AliOssUtil localUpload() {
            return new com.codeying.utils.AliOssUtil("http://127.0.0.1", "", "", "fixture", "http://127.0.0.1", false, 60) {
                @Override public String upload(byte[] bytes,String objectName) {
                    Path directory=projectRoot.resolve(".scratch/issue13-check/uploads");
                    Path target=directory.resolve(objectName).normalize();
                    require(target.startsWith(directory),"fixture upload stays in scratch");
                    try {Files.createDirectories(target.getParent());Files.write(target,bytes);return target.toUri().toString();}
                    catch(java.io.IOException ex) {throw new IllegalStateException("fixture upload failure",ex);}
                }
            };
        }
        @Bean @Primary public StringRedisTemplate faultRedis(RedisConnectionFactory factory) {
            fault = new FaultRedis(factory); return fault;
        }
    }
    @org.apache.ibatis.plugin.Intercepts({@org.apache.ibatis.plugin.Signature(
        type=org.apache.ibatis.executor.Executor.class, method="update",
        args={org.apache.ibatis.mapping.MappedStatement.class,Object.class}),
        @org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class, method="flushStatements", args={})})
    public static class SqlEvidence implements org.apache.ibatis.plugin.Interceptor {
        private final ThreadLocal<String> lastWrite = new ThreadLocal<>();
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            if (invocation.getMethod().getName().equals("update"))
                lastWrite.set(((org.apache.ibatis.mapping.MappedStatement)invocation.getArgs()[0]).getId());
            try {return invocation.proceed();}
            catch(Throwable ex) {
                sqlFailures.add(String.valueOf(lastWrite.get()));
                throw ex;
            }
        }
    }
    static class FaultRedis extends StringRedisTemplate {
        final StringRedisTemplate real;
        FaultRedis(RedisConnectionFactory factory) { super(factory); real = new StringRedisTemplate(factory); }
        boolean owned(Object key) {
            return key instanceof String s && (s.equals(RedisKeys.aiRecommendContextKey(user)) || s.contains(model));
        }
        Object invoke(Object delegate,Method method,Object[] args) throws Throwable {
            String name=method.getName(); boolean target=args!=null && args.length>0 && owned(args[0]);
            String operation = name.equals("get") ? "get" : name.equals("set") ? "context" : name.equals("add") ? "set" : name.equals("increment") ? "counter" : "other";
            if (target && mode.equals(operation+"-before")) throw new IllegalStateException("fixture pre-write failure");
            Object result;
            try { result=method.invoke(delegate,args); } catch(InvocationTargetException ex) {throw ex.getCause();}
            if(target && mode.equals(operation+"-after")) throw new IllegalStateException("fixture post-write failure");
            return result;
        }
        @SuppressWarnings("unchecked") <T> T wrap(Class<T> type,T delegate) {
            return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->invoke(delegate,m,a));
        }
        @Override public ValueOperations<String,String> opsForValue() {return wrap(ValueOperations.class,real.opsForValue());}
        @Override public SetOperations<String,String> opsForSet() {return wrap(SetOperations.class,real.opsForSet());}
        @Override public Boolean expire(String key,Duration ttl) {
            if(owned(key) && mode.equals("expiry-before")) return false;
            return real.expire(key,ttl);
        }
    }
    static void require(boolean ok,String message) {if(!ok) throw new AssertionError(message);}
    static void pass(String scenario,Object evidence) {
        results.add(Map.of("scenario",scenario,"result","PASS","evidence",evidence));
        System.out.println("ISSUE13 PASS "+scenario+" "+evidence);
    }
    static List<String> keys() {
        return List.of(RedisKeys.aiRecommendContextKey(user),
            RedisKeys.aiRecommendExposureUsersKey(date,model),RedisKeys.aiRecommendExposureCountKey(date,model),
            RedisKeys.aiRecommendClickUsersKey(date,model),RedisKeys.aiRecommendClickCountKey(date,model),
            RedisKeys.aiRecommendConversionUsersKey(date,model),RedisKeys.aiRecommendConversionCountKey(date,model));
    }
    static Map<String,Object> residues() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:keys()) {
            var type=redis.type(key);
            Object value=type==org.springframework.data.redis.connection.DataType.SET ? redis.opsForSet().members(key) : type==org.springframework.data.redis.connection.DataType.STRING ? redis.opsForValue().get(key) : "absent";
            result.put(key,Map.of("value",value==null?"absent":value,"ttlSeconds",redis.getExpire(key)));
        }
        return result;
    }
    static Map<String,Object> redisValues() {
        Map<String,Object> values=new LinkedHashMap<>();
        residues().forEach((key,value)->values.put(key,((Map<?,?>)value).get("value")));
        return values;
    }
    static void resetRedis() {mode="off";redis.delete(keys());}
    static AiAssistantSendReplyVO reply() {
        var reply=new AiAssistantSendReplyVO();var dish=new AiAssistantDishCardVO();dish.setDishId(user);
        reply.setDishes(List.of(dish));reply.setPayload(Map.of("analysisModelVersion",model,"analysisStrategy","fixture"));return reply;
    }
    static void expose() {observation.recordRecommendationExposure(user,1L,1L,reply());}
    static void click() {observation.recordRecommendationClick(user,user);}
    static void convert() {observation.recordRecommendationConversion(user,123L,List.of(user));}
    static void seedContext(boolean clicked) {resetRedis();expose();if(clicked)click();}
    static Map<String,Object> snapshot() {
        return Map.of("orders",sql.queryForList("SELECT * FROM orders WHERE user_id=? ORDER BY id",user),
            "details",sql.queryForList("SELECT d.* FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id=? ORDER BY d.id",user),
            "carts",sql.queryForList("SELECT * FROM shopping_cart WHERE user_id IN (?,?) ORDER BY id",user,other));
    }
    static void seedCart() {
        mode="off";sql.update("DELETE FROM shopping_cart WHERE user_id=?",user);
        sql.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Issue13 dish',?,?,2,18,NOW())",user,user);
    }
    static OrdersSubmitDTO dto() {var d=new OrdersSubmitDTO();d.setAddressBookId(user);d.setPayMethod(1);d.setPackAmount(0);return d;}
    static void assertOrder(long id) {
        require(sql.queryForObject("SELECT COUNT(*) FROM orders WHERE id=? AND user_id=? AND amount=36",Integer.class,id,user)==1,"correct committed order");
        require(sql.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id=? AND dish_id=? AND number=2 AND amount=18",Integer.class,id,user)==1,"correct committed detail");
        require(cart.listForUser(user).isEmpty(),"only submitted user cart cleared");
        require(cart.listForUser(other).size()==1,"other user cart unchanged");
    }
    static Map<?,?> http(String method,String path,String body,String auth,boolean admin) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(20));
        if(auth!=null) request.header(admin?"token":"authentication",auth);
        if(body==null) request.method(method,HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        var response=HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
        return JSON.readValue(response.body(),Map.class);
    }
    static boolean success(Map<?,?> response) {return Integer.valueOf(1).equals(response.get("code"));}
    static void coreFailure(String table,String event,String condition,String name) throws Exception {
        seedCart();seedContext(true);var before=snapshot();var cache=redisValues();
        String trigger="issue13_"+name;
        sql.execute("CREATE TRIGGER "+trigger+" BEFORE "+event+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue13 fixture core failure'; END IF; END");
        try {
            sqlFailures.clear();
            require(!success(http("POST","/user/order/submit",JSON.writeValueAsString(dto()),token,false)),"core interface failure");
            String expected=table.equals("orders")?"OrdersMapper.insert":table.equals("order_detail")?"OrderDetailMapper.insert":"ShoppingCartMapper.delete";
            require(sqlFailures.stream().anyMatch(id->id.endsWith(expected)),"actual injected Mapper SQL failure reached: "+expected);
            require(before.equals(snapshot()),"all core SQL rows unchanged after rollback");
            require(cache.equals(redisValues()),"core failure before optional stats");
            pass("core-"+name,"HTTP failure; full SQL rows and Redis unchanged");
        } finally {sql.execute("DROP TRIGGER "+trigger);}
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();projectRoot=root;
        user=910000000L+System.currentTimeMillis()%100000000L;other=user+1;model="issue13-"+user;
        var app=new SpringApplication(App.class,Fixtures.class);
        try(var context=app.run("--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk","--server.port=0","--logging.level.root=ERROR",
                "--analysis.xgboost.model-dir="+root.resolve(".scratch/issue13-check/models/analysis"),
                "--order-risk.model-dir="+root.resolve(".scratch/issue13-check/models/risk"))) {
            sql=context.getBean(JdbcTemplate.class);
            require("sandcastle_fixture".equals(sql.queryForObject("SELECT DATABASE()",String.class)),"dedicated MySQL before any writes");
            redis=fault.real;observation=context.getBean(AnalysisObservationService.class);
            orders=context.getBean(OrdersApplicationService.class);cart=context.getBean(ShoppingCartService.class);
            require(AopUtils.isAopProxy(orders) && AopUtils.isAopProxy(cart),"actual Spring use case proxies");
            var props=context.getBean(SkyProperties.class);
            token=JwtUtil.createToken(props.getJwt().getUserSecretKey(),600000,user,"fixture","user");
            adminToken=JwtUtil.createToken(props.getJwt().getAdminSecretKey(),600000,900001L,"fixture","admin");
            baseUrl="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
            try {
                sql.update("INSERT INTO user(id,openid,name) VALUES(?,?,'Issue13 A'),(?,?,'Issue13 B')",user,"issue13-a-"+user,other,"issue13-b-"+user);
                sql.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(?,?,'Issue13 A','00000000000','Isolation',0),(?,?,'Issue13 B','00000000001','Isolation',0)",user,user,other,other);
                sql.update("INSERT INTO dish(id,name,category_id,price,status,create_time,update_time,create_user,update_user) VALUES(?,'Issue13 dish',900001,18,1,NOW(),NOW(),900001,900001)",user);
                sql.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Issue13 other',?,?,1,18,NOW())",other,user);
                pass("isolation-and-proxies","sandcastle_fixture; real MyBatis; real Redis; AOP orders/cart");
                for(String operation:List.of("exposure","click","conversion")) {
                    for(String failure:List.of("context-before","context-after","set-before","set-after","counter-before","counter-after","expiry-before")) {
                        if(operation.equals("exposure"))resetRedis();else seedContext(operation.equals("conversion"));
                        mode=failure;
                        Runnable action=operation.equals("exposure")?Issue13Probe::expose:operation.equals("click")?Issue13Probe::click:Issue13Probe::convert;
                        action.run();mode="off";var after=residues();
                        int idx=operation.equals("exposure")?2:operation.equals("click")?4:6;
                        String count=redis.opsForValue().get(keys().get(idx));
                        require(failure.equals("counter-after")?"1".equals(count):count==null,"failed write stops later counters");
                        action.run();var retry=residues();
                        if(!operation.equals("exposure") && !failure.equals("context-before")) require(Objects.equals(count,redis.opsForValue().get(keys().get(idx))),"context dedup prevents repeated click/conversion counter");
                        pass(operation+"-"+failure,Map.of("afterFailure",after,"afterRetry",retry));
                    }
                }
                for(String failure:List.of("get-before","context-before","context-after","set-before","set-after","counter-before","counter-after","off")) {
                    seedCart();if(failure.equals("off"))resetRedis();else seedContext(true);
                    mode=failure;
                    var submitted=http("POST","/user/order/submit",JSON.writeValueAsString(dto()),token,false);
                    mode="off";require(success(submitted),"HTTP submit succeeds during optional failure");
                    long orderId=((Number)((Map<?,?>)submitted.get("data")).get("id")).longValue();
                    assertOrder(orderId);
                    pass("submit-"+failure,Map.of("orderId",orderId,"redis",residues()));
                }
                var change=new ShoppingCartChangeDTO();change.setDishId(user);
                for(String failure:List.of("get-before","context-before","context-after","set-before","set-after","counter-before","counter-after")) {
                    seedCart();seedContext(false);mode=failure;
                    require(success(http("POST","/user/shoppingCart/add","{\"dishId\":"+user+"}",token,false)),"HTTP cart success during optional failure");
                    mode="off";
                    require(cart.listForUser(user).get(0).getNumber()==3,"cart core quantity 3 during optional fault");
                    require(cart.listForUser(other).size()==1,"other cart untouched");
                    pass("cart-"+failure,Map.of("quantity",3,"redis",residues()));
                }
                coreFailure("orders","INSERT","NEW.user_id="+user,"order");
                coreFailure("order_detail","INSERT","EXISTS(SELECT 1 FROM orders WHERE id=NEW.order_id AND user_id="+user+")","detail");
                coreFailure("shopping_cart","DELETE","OLD.user_id="+user,"cart");
                seedCart();var before=snapshot();var foreign=dto();foreign.setAddressBookId(other);
                boolean rejected=false;try{orders.submit(user,foreign);}catch(RuntimeException expected){rejected=true;}
                require(rejected && before.equals(snapshot()),"foreign address rejected without SQL effects");
                require(!success(http("POST","/user/order/submit",JSON.writeValueAsString(dto()),null,false)),"unauthenticated HTTP rejected");
                require(!success(http("POST","/user/order/submit",JSON.writeValueAsString(foreign),token,false)),"HTTP foreign address rejected");
                sql.update("UPDATE dish SET status=0 WHERE id=?",user);
                rejected=false;try{orders.submit(user,dto());}catch(RuntimeException expected){rejected=true;}
                require(rejected && before.equals(snapshot()),"unsaleable submit rejected");
                rejected=false;try{cart.addItem(user,change);}catch(RuntimeException expected){rejected=true;}
                require(rejected && before.equals(snapshot()),"existing cart add unsaleable rejected");
                sql.update("UPDATE dish SET status=1 WHERE id=?",user);
                pass("auth-address-saleability","unauthenticated/foreign address/unsaleable existing cart rejected; SQL unchanged");
                context.getBean(com.codeying.properties.OrderRiskProperties.class).getTask().setScoringEnabled(true);
                sql.execute("CREATE TRIGGER issue13_risk BEFORE INSERT ON order_risk_feature_snapshot FOR EACH ROW BEGIN IF EXISTS(SELECT 1 FROM orders WHERE id=NEW.order_id AND user_id="+user+") THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue13 risk mapper failure'; END IF; END");
                try {seedCart();resetRedis();sqlFailures.clear();var order=orders.submit(user,dto());assertOrder(order.getId());
                    require(sqlFailures.stream().anyMatch(id->id.endsWith("OrderRiskFeatureSnapshotMapper.insert")),"actual risk Mapper SQL failure reached");
                    require(sql.queryForObject("SELECT COUNT(*) FROM order_risk_feature_snapshot WHERE order_id=?",Integer.class,order.getId())==0,"risk Mapper failure reached");
                    pass("risk-mapper-failure","core committed; risk snapshot absent");
                } finally {sql.execute("DROP TRIGGER issue13_risk");context.getBean(com.codeying.properties.OrderRiskProperties.class).getTask().setScoringEnabled(false);}
                seedCart();resetRedis();
                require(success(http("POST","/user/shoppingCart/add","{\"dishId\":"+user+"}",token,false)),"HTTP cart add");
                require(success(http("POST","/user/shoppingCart/sub","{\"dishId\":"+user+"}",token,false)),"HTTP cart subtract");
                require(success(http("GET","/user/shoppingCart/list",null,token,false)),"HTTP cart list");
                var response=http("POST","/user/order/submit",JSON.writeValueAsString(dto()),token,false);require(success(response),"HTTP normal submit");
                long id=((Number)((Map<?,?>)response.get("data")).get("id")).longValue();assertOrder(id);
                require(success(http("GET","/user/order/orderDetail/"+id,null,token,false)),"HTTP user details");
                require(success(http("GET","/admin/order/details/"+id,null,adminToken,true)),"HTTP admin order read");
                pass("http-roundtrip","cart add/sub/list, submit, user details, admin read with fixture JWT (no password/native UI proof)");
            } finally {
                mode="off";redis.delete(keys());
                sql.update("DELETE f FROM order_risk_feature_snapshot f JOIN orders o ON o.id=f.order_id WHERE o.user_id=?",user);
                sql.update("DELETE r FROM order_risk_result r JOIN orders o ON o.id=r.order_id WHERE o.user_id=?",user);
                sql.update("DELETE d FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id=?",user);
                sql.update("DELETE FROM orders WHERE user_id=?",user);
                sql.update("DELETE FROM shopping_cart WHERE user_id IN (?,?)",user,other);
                sql.update("DELETE FROM address_book WHERE id IN (?,?) AND user_id IN (?,?)",user,other,user,other);
                sql.update("DELETE FROM dish WHERE id=?",user);
                sql.update("DELETE FROM user WHERE id IN (?,?)",user,other);
                Files.writeString(root.resolve(".scratch/issue13-check/probe-results-"+user+".json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(results));
            }
        }
        System.out.println("ISSUE13 ALL_SCENARIOS_PASS");
    }
}
