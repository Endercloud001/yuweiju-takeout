import com.codeying.App;
import com.codeying.entity.*;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.AiAssistantMessageMapper;
import com.codeying.properties.*;
import com.codeying.service.*;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.codeying.dto.common.ai_assistant.AiAssistantMessageSendDTO;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.aop.support.AopUtils;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.LoggerFactory;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real packaged Spring/MyBatis/MySQL/Redis and protected HTTP; no model, personal config or original DB. */
public class Issue16Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String BASE = "http://127.0.0.1:18096";
    static final String RUN = "i16_" + UUID.randomUUID().toString().replace("-", "").substring(0,8);
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final List<String> assertions = new ArrayList<>(), cleanupFailures = new ArrayList<>();
    static final List<Map<String,Object>> scenarios = new ArrayList<>();
    static final List<Long> ownedSessions = new ArrayList<>(), ownedDishes = new ArrayList<>();
    static final Set<String> ownedRedisKeys = new LinkedHashSet<>();
    static final Map<String,List<Map<String,Object>>> before = new LinkedHashMap<>();
    static final Map<Long,Integer> temporarySaleStates = new LinkedHashMap<>();
    static final AtomicInteger outbound = new AtomicInteger();
    static volatile String mode = "valid", weatherMode = "null";
    static long categoryId, dishA, dishB, stoppedDish, sessionId, ownedAddress, ownedHeat, ownedCluster;
    static final List<Long> ownedOrders = new ArrayList<>();
    static volatile long selectedA, selectedB;
    static String admin, userA, userB;
    static JdbcTemplate jdbc;
    static org.springframework.data.redis.core.StringRedisTemplate redis;
    static AiAssistantApplicationService service;
    static ConfigurableApplicationContext context;
    static HttpServer fixture;
    static ExecutorService fixtureExecutor;
    static Path out;
    static boolean isolated, redisIsolated, business, finished;
    static String failure = "";
    static final Logger aiLogger = (Logger) LoggerFactory.getLogger(com.codeying.service.impl.AiAssistantApplicationServiceImpl.class);
    static final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @Configuration(proxyBeanMethods=false)
    public static class ProbeConfig {
        @Bean @Primary AiWeatherService localWeather() {
            return new AiWeatherService() {
                public String getWeatherSummary(String location) {
                    if ("fail".equals(weatherMode)) throw new IllegalStateException("private fixture location must not be logged");
                    return null;
                }
                public String getWeatherCode(String location, LocalDate date) { return null; }
            };
        }
    }
    static void require(boolean ok,String label) {
        if (!ok) throw new AssertionError(label);
        assertions.add(label); System.out.println("ISSUE16 PASS " + label);
    }
    static List<Map<String,Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"); }
    static long insert(String sql,Object... parameters) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<parameters.length;i++) statement.setObject(i+1,parameters[i]); return statement; },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    static void startFixture() throws Exception {
        fixtureExecutor=Executors.newCachedThreadPool();
        fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); fixture.setExecutor(fixtureExecutor);
        fixture.createContext("/v1/chat/completions", exchange -> {
            outbound.incrementAndGet();
            exchange.getRequestBody().readAllBytes(); // Local private prompt is discarded, never recorded.
            String selectedMode=mode; int status=200; String body;
            try {
                if ("timeout".equals(selectedMode)) Thread.sleep(900);
                if ("stop-during-call".equals(selectedMode)) jdbc.update("UPDATE dish SET status=0 WHERE id=?",dishA);
                if ("402".equals(selectedMode) || "503".equals(selectedMode)) {
                    status=Integer.parseInt(selectedMode); body="private upstream error fixture";
                } else if ("non-json".equals(selectedMode)) body="invalid upstream fixture";
                else if ("bad-envelope".equals(selectedMode)) body="{\"choices\":{}}";
                else if ("missing-content".equals(selectedMode)) body="{\"choices\":[{\"message\":{}}]}";
                else if ("numeric-content".equals(selectedMode)) body="{\"choices\":[{\"message\":{\"content\":42}}]}";
                else if ("fenced-envelope".equals(selectedMode)) body="```json\n"+JSON.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content",recommendation(List.of(selectedA,selectedB)))))))+"\n```";
                else {
                    String content=switch(selectedMode) {
                        case "bad-json" -> "malformed model content";
                        case "missing-fields" -> "{\"reply\":\"fixture\"}";
                        case "oversized" -> recommendation(List.of(selectedA)).replace("为您推荐菜品。", "字".repeat(2001));
                        case "empty" -> recommendation(List.of());
                        case "unknown" -> recommendation(List.of(999999999L));
                        case "mixed" -> recommendation(List.of(selectedA,999999999L));
                        case "stopped" -> recommendation(List.of(stoppedDish));
                        case "fractional" -> recommendation(List.of(selectedA+0.5));
                        case "string-id" -> recommendation(List.of(Long.toString(selectedA)));
                        case "duplicate" -> recommendation(List.of(selectedB,selectedA,selectedB));
                        case "fenced" -> "```json\n"+recommendation(List.of(selectedA,selectedB))+"\n```";
                        case "invalid-intent" -> "{\"intent\":\"recommend_dish\",\"confidence\":\"1\"}";
                        default -> recommendation(List.of(selectedA,selectedB));
                    };
                    body=JSON.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content",content)))));
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (Exception ignored) { /* Read-timeout peer already closed. No private fixture body or error output. */ }
            finally { exchange.close(); }
        });
        fixture.start();
    }
    static String recommendation(List<?> ids) throws Exception {
        var dishes=new ArrayList<Map<String,Object>>();
        for(Object id:ids) dishes.add(Map.of("dishId",id,"name","untrusted fixture name","price",0,"imageUrl","untrusted"));
        var result=new LinkedHashMap<String,Object>(); result.put("reply","为您推荐菜品。"); result.put("dishes",dishes);
        result.put("action","suggest_dish"); result.put("payload",null); return JSON.writeValueAsString(result);
    }
    static HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(10));
        if(token!=null) request.header(path.startsWith("/admin/") ? "token" : "authentication",token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response=HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString()); captureRedis(); return response;
    }
    static JsonNode data(HttpResponse<String> response,String label) throws Exception {
        require(response.statusCode()==200,label+" HTTP200"); var body=JSON.readTree(response.body());
        require(body.path("code").asInt(-9)==1 && body.path("success").asBoolean(),label+" compatible success"); return body.path("data");
    }
    static void denied(HttpResponse<String> response,String label) throws Exception {
        require(response.statusCode()==401 && JSON.readTree(response.body()).path("code").asInt(-9)==0,label+" HTTP401 code0");
    }
    static void forbidden(HttpResponse<String> response,String label) throws Exception {
        var body=JSON.readTree(response.body());
        require(response.statusCode()==200 && body.path("code").asInt(-9)==0 && "无权限".equals(body.path("message").asText()),label+" forbidden code0");
    }
    static JsonNode send(String content) throws Exception {
        return data(call("POST","/user/aiAssistant/message/send",userA,Map.of("sessionId",sessionId,"content",content)),"send "+mode);
    }
    static void cards(JsonNode reply,List<Long> ids,String label) {
        var actual=new ArrayList<Long>(); reply.path("dishes").forEach(d -> actual.add(d.path("dishId").longValue()));
        require(actual.equals(ids) && "suggest_dish".equals(reply.path("action").asText()),label+" selected on-sale cards");
        for(JsonNode dish:reply.path("dishes")) {
            var row=jdbc.queryForMap("SELECT * FROM dish WHERE id=?",dish.path("dishId").longValue());
            require(row.get("name").equals(dish.path("name").asText())
                    && new java.math.BigDecimal(row.get("price").toString()).compareTo(dish.path("price").decimalValue())==0
                    && row.get("image").equals(dish.path("imageUrl").asText()),label+" trusted name price image");
        }
    }
    static void retry(JsonNode reply,String label) throws Exception {
        require(reply.path("reply").asText().contains("重试") && reply.path("dishes").isArray() && reply.path("dishes").isEmpty()
                && reply.path("action").isNull() && reply.path("payload").isNull(),label+" friendly retry without cards action payload");
        var stored=jdbc.queryForMap("SELECT content,metadata FROM ai_assistant_message WHERE session_id=? AND sender_type=2 ORDER BY id DESC LIMIT 1",sessionId);
        var meta=JSON.readTree(stored.get("metadata").toString());
        require(stored.get("content").toString().equals(reply.path("reply").asText()) && meta.path("dishIds").isEmpty()
                && meta.path("dishNames").isEmpty() && meta.path("dishCount").asInt()==0 && meta.path("action").isNull(),label+" persisted no fake recommendation");
        require(jdbc.queryForObject("SELECT pending_action FROM ai_assistant_session WHERE id=?",String.class,sessionId)==null,label+" pending action cleared");
    }
    static void failureScenario(String selectedMode) throws Exception {
        // A success after cooldown resets transport failures; every following case must hit actual outbound HTTP.
        mode="valid"; Thread.sleep(1600); cards(send("推荐菜品"),List.of(dishA,dishB),"reset before "+selectedMode);
        String observation=redis.opsForValue().get(com.codeying.constant.RedisKeys.aiRecommendContextKey(900001L));
        mode=selectedMode; int count=outbound.get(); long begin=System.nanoTime();
        retry(send("推荐菜品"),selectedMode);
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin);
        require(outbound.get()==count+1,selectedMode+" exactly one outbound request without retries");
        require(Objects.equals(observation,redis.opsForValue().get(com.codeying.constant.RedisKeys.aiRecommendContextKey(900001L))),selectedMode+" no false Redis recommendation exposure");
        if("timeout".equals(selectedMode)) require(elapsed>=100 && elapsed<850,"configured 180ms timeout bounds");
        scenarios.add(Map.of("name",selectedMode,"outboundRequests",outbound.get()-count,"elapsedMs",elapsed,"passed",true));
    }
    static void captureRedis() {
        if(redisIsolated && redis!=null) ownedRedisKeys.addAll(Objects.requireNonNull(redis.keys("*")));
    }
    static void restoreSaleStates() {
        for(var entry:temporarySaleStates.entrySet()) jdbc.update("UPDATE dish SET status=? WHERE id=?",entry.getValue(),entry.getKey());
        temporarySaleStates.clear();
    }
    static void noCandidates() throws Exception {
        // Only the verified isolated database. Restore exact fixture statuses even if the scenario fails.
        for(var row:jdbc.queryForList("SELECT id,status FROM dish WHERE status=1")) {
            temporarySaleStates.put(((Number)row.get("id")).longValue(),((Number)row.get("status")).intValue());
        }
        try {
            for(long id:temporarySaleStates.keySet()) jdbc.update("UPDATE dish SET status=0 WHERE id=?",id);
            int count=outbound.get(); var reply=send("推荐菜品");
            require(reply.path("reply").asText().contains("没有可推荐") && reply.path("dishes").isEmpty() && reply.path("action").isNull(),"no on-sale candidates truthful guide");
            require(outbound.get()==count,"no candidates uses no model request");
        } finally { restoreSaleStates(); }
    }
    static void realRecommendationHistory() throws Exception {
        // Ordinary synthetic history exercises changed named queries; no training or algorithm substitutes.
        String fallback=com.codeying.constant.RedisKeys.heatFallbackTopKey(5); redis.delete(fallback);
        ownedAddress=insert("INSERT INTO address_book(user_id,consignee,phone,detail,district_name,is_default) VALUES(900001,'Fixture A','00000000000','Isolation history',?,0)",RUN+" district");
        for(int i=0;i<5;i++) {
            long order=insert("INSERT INTO orders(number,status,user_id,address_book_id,order_time,amount,phone) VALUES(?,5,900001,?,DATE_SUB(NOW(),INTERVAL 1 DAY),56.00,'00000000000')",RUN+"_order_"+i,ownedAddress);
            ownedOrders.add(order);
            jdbc.update("INSERT INTO order_detail(order_id,name,image,dish_id,number,amount) VALUES(?,?,?, ?,2,18.00)",order,RUN+" A","historical-fixture.png",dishA);
            jdbc.update("INSERT INTO order_detail(order_id,name,image,dish_id,number,amount) VALUES(?,?,?, ?,1,20.00)",order,RUN+" B","historical-fixture.png",dishB);
        }
        var reply=send("一键推荐"); cards(reply,List.of(dishA,dishB),"real completed history and district recommendation queries");
        require(!reply.path("reply").asText().contains("暂无足够"),"available five-order history not described as absent");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        ownedHeat=insert("INSERT INTO dish_heat_prediction_result(window_start,window_end,dish_id,predicted_sales_qty,heat_score,model_version,feature_version,status) VALUES(?,?,?,10,10,?,'v1',1)",today,today.plusDays(7),dishA,RUN);
        selectedB=dishA;
        try { cards(send("最近热门的推荐菜品"),List.of(dishA),"real active heat prediction query"); }
        finally { selectedB=dishB; }
        ownedCluster=insert("INSERT INTO user_cluster_result(snapshot_date,user_id,cluster_id,cluster_score,model_version,feature_version,topn_dish_json) VALUES(?,900001,7,1,?,'v1',?)",today,RUN,JSON.writeValueAsString(List.of(dishA,dishB,stoppedDish)));
        cards(send("推荐菜品"),List.of(dishA,dishB),"real latest cluster query and on-sale filtering");
        captureRedis();
        require(Boolean.TRUE.equals(redis.hasKey(com.codeying.constant.RedisKeys.userClusterKey(900001L))),"real cluster cache populated in isolated Redis");
        scenarios.add(Map.of("name","named-history-heat-cluster-queries","syntheticOrders",ownedOrders.size(),"passed",true));
    }

    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize(); out=root.resolve(".scratch/issue16"); Files.createDirectories(out);
        Path runtime=out.resolve("runtime"); Files.createDirectories(runtime);
        logs.start(); aiLogger.addAppender(logs);
        Thread shutdown=new Thread(()-> { try { closeResources(); } catch(Exception ex) { System.out.println("ISSUE16 SHUTDOWN_FAIL "+ex.getClass().getSimpleName()); } });
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            startFixture();
            context=new SpringApplication(App.class,ProbeConfig.class).run(
                    "--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                    "--spring.profiles.active=dev,afk", "--server.address=127.0.0.1", "--server.port=18096", "--spring.main.banner-mode=off",
                    "--spring.data.redis.database=15", "--spring.ai.openai.api-key=sandbox-local-placeholder",
                    "--spring.ai.openai.base-url=http://127.0.0.1:"+fixture.getAddress().getPort(),
                    "--ai-assistant.model.connect-timeout-ms=120", "--ai-assistant.model.read-timeout-ms=180",
                    "--ai-assistant.model.failure-threshold=2", "--ai-assistant.model.cool-down-ms=1500",
                    "--logging.level.root=OFF", "--logging.level.com.codeying.service.impl.AiAssistantApplicationServiceImpl=WARN",
                    "--analysis.task.enabled=false", "--order-risk.task.scoring-enabled=false",
                    "--analysis.model-version-prefix="+RUN, "--analysis.xgboost.model-dir="+runtime.resolve("analysis-models"),
                    "--order-risk.model-dir="+runtime.resolve("risk-models"));
            jdbc=context.getBean(JdbcTemplate.class); redis=context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
            service=context.getBean(AiAssistantApplicationService.class);
            require(AopUtils.isAopProxy(service),"real Spring transaction proxy");
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"isolated database name before writes");
            try(var connection=jdbc.getDataSource().getConnection()) {
                require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture?"),"dedicated mysql alias before writes");
            }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host"))
                    && "6379".equals(context.getEnvironment().getProperty("spring.data.redis.port"))
                    && "15".equals(context.getEnvironment().getProperty("spring.data.redis.database")),"dedicated Redis alias and task test database");
            require(Objects.requireNonNull(redis.keys("*")).isEmpty(),"task Redis database initially empty; no existing resources overwritten"); redisIsolated=true;
            require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id IN (900001,900002) AND openid IN ('sandbox-user-a','sandbox-user-b')",Long.class)==2L,"two synthetic task identities");
            for(String table:List.of("employee","user","address_book","category","dish","dish_flavor","shopping_cart","ai_assistant_session","ai_assistant_message","customer_service_session","customer_service_message","orders","order_detail","dish_heat_prediction_result","user_cluster_result")) {
                String ddl=jdbc.queryForMap("SHOW CREATE TABLE "+table).values().stream().map(Object::toString).filter(v->v.contains("CREATE TABLE")).findFirst().orElseThrow();
                require(ddl.contains("ENGINE=InnoDB") && ddl.contains("PRIMARY KEY (`id`)"),"actual "+table+" engine PK");
                if(table.startsWith("ai_assistant")) require(ddl.contains("`id` bigint NOT NULL AUTO_INCREMENT") && ddl.contains("datetime"),"actual AI PK and dates");
                before.put(table,rows(table));
            }
            require(jdbc.queryForObject("SELECT COUNT(*) FROM ai_assistant_session WHERE user_id IN (900001,900002) AND status=1",Long.class)==0L,"no prior open synthetic AI session to mutate");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id IN (900001,900002)",Long.class)==0L,"actual absent synthetic order history"); isolated=true;
            var bounds=context.getBean(AiModelHttpProperties.class);
            require(bounds.getConnectTimeoutMs()==120 && bounds.getReadTimeoutMs()==180 && bounds.getFailureThreshold()==2 && bounds.getCoolDownMs()==1500,"actual configured model bounds");
            // Fixture identity tokens use only standalone sandbox keys and unchanged production interceptor.
            var jwt=context.getBean(SkyProperties.class).getJwt();
            userA=JwtUtil.createToken(jwt.getUserSecretKey(),72000000L,900001L,"sandbox-user-a","user");
            userB=JwtUtil.createToken(jwt.getUserSecretKey(),72000000L,900002L,"sandbox-user-b","user");
            var adminLogin=data(call("POST","/admin/employee/login",null,Map.of("username","sandbox_admin","password","sandbox-only-login")),"fixture admin login");
            require(adminLogin.path("id").asLong()==900001L,"actual synthetic administrator identity"); admin=adminLogin.path("token").asText();
            // These exact routes are included by WebMvcConfiguration and are not public/login excludes.
            for(String token:Arrays.asList(null,admin)) {
                denied(call("POST","/user/aiAssistant/session/open",token,null),"anonymous or wrong-scope open");
                denied(call("GET","/user/aiAssistant/message/list?sessionId=1",token,null),"anonymous or wrong-scope list");
                denied(call("POST","/user/aiAssistant/message/send",token,Map.of("sessionId",1,"content","fixture")),"anonymous or wrong-scope send");
            }
            for(String token:Arrays.asList(null,userA)) {
                denied(call("GET","/admin/aiAssistant/session/openList",token,null),"protected admin summary");
                denied(call("GET","/admin/aiAssistant/message/list?sessionId=1",token,null),"protected admin messages");
            }
            var opened=data(call("POST","/user/aiAssistant/session/open",userA,null),"open owned AI session");
            sessionId=opened.path("id").asLong(); ownedSessions.add(sessionId);
            require(sessionId>0 && opened.path("userId").asLong()==900001L && opened.path("status").asInt()==1,"stored open ownership status");
            require(data(call("POST","/user/aiAssistant/session/open",userA,null),"reopen").path("id").asLong()==sessionId,"reuses current-day session");
            var other=data(call("POST","/user/aiAssistant/session/open",userB,null),"open second user"); ownedSessions.add(other.path("id").asLong());
            forbidden(call("GET","/user/aiAssistant/message/list?sessionId="+sessionId,userB,null),"foreign history");
            forbidden(call("POST","/user/aiAssistant/message/send",userB,Map.of("sessionId",sessionId,"content","推荐菜品")),"foreign send");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM ai_assistant_message WHERE session_id=?",Long.class,sessionId)==0L && outbound.get()==0,"foreign scopes have no persistence or outbound side effects");
            try { service.listUserMessages(900002L,sessionId,null,50); throw new AssertionError("direct foreign history accepted"); }
            catch(BusinessException ex) { require("无权限".equals(ex.getMessage()),"reusable Service protects history owner"); }
            var foreignBody=new AiAssistantMessageSendDTO(); foreignBody.setSessionId(sessionId); foreignBody.setContent("推荐菜品");
            var direct=service.sendMessage(900002L,foreignBody);
            require(direct.getDishes().isEmpty() && direct.getAction()==null && outbound.get()==0,"reusable Service refuses foreign send before model");
            categoryId=insert("INSERT INTO category(type,name,sort,status,create_time,update_time,create_user,update_user) VALUES(1,?,1,1,NOW(),NOW(),900001,900001)",RUN+" category");
            dishA=insert("INSERT INTO dish(name,category_id,price,image,description,status,create_time,update_time,create_user,update_user) VALUES(?,?,18.00,?,'fixture',1,NOW(),NOW(),900001,900001)",RUN+" A",categoryId,"/fixture-a.png"); ownedDishes.add(dishA);
            dishB=insert("INSERT INTO dish(name,category_id,price,image,description,status,create_time,update_time,create_user,update_user) VALUES(?,?,20.00,?,'fixture',1,NOW(),NOW(),900001,900001)",RUN+" B",categoryId,"/fixture-b.png"); ownedDishes.add(dishB);
            stoppedDish=insert("INSERT INTO dish(name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(?,?,22.00,'',0,NOW(),NOW(),900001,900001)",RUN+" stopped",categoryId); ownedDishes.add(stoppedDish);
            jdbc.update("INSERT INTO dish_flavor(dish_id,name,value) VALUES(?,'fixture flavor','[\"不辣\",\"微辣\"]')",dishA);
            selectedA=dishA; selectedB=dishB;
            // Task-owned Redis fallback candidates select ordinary real named Mapper data without changing production ranking.
            String fallback=com.codeying.constant.RedisKeys.heatFallbackTopKey(5);
            ownedRedisKeys.add(fallback); redis.opsForValue().set(fallback,JSON.writeValueAsString(List.of(dishA,dishB,stoppedDish)));
            cards(send("推荐菜品"),List.of(dishA,dishB),"valid recommendation with weather null");
            var good=send("推荐一道菜"); cards(good,List.of(dishA),"single dish remains compatible");
            require(good.path("dishes").get(0).path("flavors").size()==2,"server-owned flavor fields");
            data(call("POST","/user/shoppingCart/add",userA,Map.of("dishId",dishA,"dishFlavor","不辣")),"existing cart accepts recommended ID");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=900001 AND dish_id=? AND dish_flavor='不辣'",Long.class,dishA)==1L,"real cart row uses owned valid candidate");
            mode="fenced"; cards(send("推荐菜品"),List.of(dishA,dishB),"DeepSeek fenced JSON");
            mode="duplicate"; cards(send("推荐菜品"),List.of(dishB,dishA),"deduplicated model order");
            weatherMode="fail"; mode="valid"; cards(send("推荐菜品"),List.of(dishA,dishB),"weather exception noncritical"); weatherMode="null";
            var oneKey=send("一键推荐"); cards(oneKey,List.of(dishA,dishB),"one-key cold-start candidate flow");
            require(oneKey.path("reply").asText().contains("暂无足够可用的历史偏好"),"absent history honestly described");
            for(String scenario:List.of("non-json","bad-envelope","missing-content","numeric-content","fenced-envelope","bad-json","missing-fields","oversized","empty","unknown","mixed","stopped","fractional","string-id","402","503","timeout")) failureScenario(scenario);
            mode="valid"; Thread.sleep(1600); cards(send("推荐菜品"),List.of(dishA,dishB),"reset before stop race");
            mode="stop-during-call"; try { retry(send("推荐菜品"),"stopped after candidate query"); }
            finally { jdbc.update("UPDATE dish SET status=1 WHERE id=?",dishA); }
            jdbc.update("UPDATE dish SET status=0 WHERE id=?",dishA);
            try {
                var cart=JSON.readTree(call("POST","/user/shoppingCart/add",userA,Map.of("dishId",dishA,"dishFlavor","不辣")).body());
                require(cart.path("code").asInt()==0,"existing cart rechecks stopped recommendation");
            } finally { jdbc.update("UPDATE dish SET status=1 WHERE id=?",dishA); }
            mode="valid"; noCandidates();
            mode="invalid-intent"; int count=outbound.get(); retry(send("随便聊点别的"),"invalid intent");
            require(outbound.get()==count+1,"invalid intent makes no downstream recommendation call");
            mode="valid"; Thread.sleep(1600); cards(send("推荐菜品"),List.of(dishA,dishB),"reset before circuit");
            mode="503"; count=outbound.get();
            retry(send("推荐菜品"),"circuit first failure"); retry(send("推荐菜品"),"circuit second failure");
            for(int i=0;i<3;i++) retry(send("推荐菜品"),"circuit suppressed "+i);
            require(outbound.get()==count+2,"continuous failure threshold2 suppresses three outbound calls");
            Thread.sleep(1600); retry(send("推荐菜品"),"failed cooldown recovery"); require(outbound.get()==count+3,"one failed recovery request");
            retry(send("推荐菜品"),"failed recovery suppression"); require(outbound.get()==count+3,"failed recovery reopens protection");
            Thread.sleep(1600); mode="valid"; cards(send("推荐菜品"),List.of(dishA,dishB),"cooldown recovery valid");
            cards(send("推荐菜品"),List.of(dishA,dishB),"normal requests after recovery");
            require(outbound.get()==count+5,"recovery resets failure counter without retries");
            scenarios.add(Map.of("name","continuous-failure-recovery","outboundRequests",outbound.get()-count,"suppressedRequests",4,"passed",true));
            count=outbound.get(); var manual=send("人工客服");
            require(manual.path("reply").asText().contains("客服消息") && manual.path("dishes").isEmpty() && manual.path("action").isNull() && outbound.get()==count,"manual customer-service boundary preserved");
            realRecommendationHistory();
            var history=data(call("GET","/user/aiAssistant/message/list?sessionId="+sessionId+"&limit=200",userA,null),"owned persisted history");
            var adminHistory=data(call("GET","/admin/aiAssistant/message/list?sessionId="+sessionId+"&limit=200",admin,null),"compatible admin message query");
            require(history.equals(adminHistory) && history.size()>30,"user and admin persisted views compatible");
            long last=0; for(JsonNode message:history) { require(message.path("id").asLong()>last,"ascending persisted message IDs"); last=message.path("id").asLong(); }
            var page=data(call("GET","/user/aiAssistant/message/list?sessionId="+sessionId+"&beforeId="+last+"&limit=1",userA,null),"exclusive cursor");
            require(page.size()==1 && page.get(0).path("id").asLong()<last,"real message cursor and limit");
            var summary=data(call("GET","/admin/aiAssistant/session/openList",admin,null),"compatible admin summaries");
            require(summary.findValues("id").stream().anyMatch(id->id.asLong()==sessionId),"owned AI session visible to administrator");
            require(logs.list.stream().allMatch(event->event.getThrowableProxy()==null && !event.getFormattedMessage().contains("private")
                    && !event.getFormattedMessage().contains(userA) && !event.getFormattedMessage().contains(admin)),"application failure logs contain no bodies locations tokens or exception details");
            business=true;
        } catch(Throwable ex) {
            failure=ex.getClass().getSimpleName(); System.out.println("ISSUE16 FAIL "+failure+(ex instanceof AssertionError ? " "+ex.getMessage() : ""));
        } finally {
            closeResources(); Runtime.getRuntime().removeShutdownHook(shutdown);
        }
        if(!business || !cleanupFailures.isEmpty()) throw new AssertionError("Issue16 incomplete; sanitized evidence retained");
        System.out.println("ISSUE16 ACCEPTANCE PASS");
    }
    static void cleanup(Runnable action,String label) {
        try { action.run(); require(true,label); } catch(Throwable ex) { cleanupFailures.add(label+":"+ex.getClass().getSimpleName()); }
    }
    static synchronized void closeResources() throws Exception {
        if(finished) return;
        if(fixture!=null) cleanup(()->fixture.stop(0),"local model HTTP fixture stopped");
        if(fixtureExecutor!=null) cleanup(()-> { fixtureExecutor.shutdownNow(); try { if(!fixtureExecutor.awaitTermination(5,TimeUnit.SECONDS)) throw new AssertionError("executor remains"); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); } },"local fixture executor stopped");
        if(isolated && jdbc!=null) {
            cleanup(Issue16Probe::restoreSaleStates,"temporary isolated sale statuses restored");
            for(long id:ownedSessions) cleanup(()-> { jdbc.update("DELETE FROM ai_assistant_message WHERE session_id=?",id); jdbc.update("DELETE FROM ai_assistant_session WHERE id=?",id); },"owned AI session messages removed");
            for(long id:ownedOrders) cleanup(()-> { jdbc.update("DELETE FROM order_detail WHERE order_id=?",id); jdbc.update("DELETE FROM orders WHERE id=?",id); },"owned synthetic recommendation history removed");
            if(ownedAddress>0) cleanup(()->jdbc.update("DELETE FROM address_book WHERE id=?",ownedAddress),"owned history address removed");
            if(ownedHeat>0) cleanup(()->jdbc.update("DELETE FROM dish_heat_prediction_result WHERE id=?",ownedHeat),"owned heat fixture removed");
            if(ownedCluster>0) cleanup(()->jdbc.update("DELETE FROM user_cluster_result WHERE id=?",ownedCluster),"owned cluster fixture removed");
            for(long id:ownedDishes) cleanup(()-> { jdbc.update("DELETE FROM shopping_cart WHERE user_id=900001 AND dish_id=?",id); jdbc.update("DELETE FROM dish_flavor WHERE dish_id=?",id); jdbc.update("DELETE FROM dish WHERE id=?",id); },"owned dish flavor cart rows removed");
            if(categoryId>0) cleanup(()->jdbc.update("DELETE FROM category WHERE id=?",categoryId),"owned category removed");
            for(var entry:before.entrySet()) cleanup(()-> { if(!entry.getValue().equals(rows(entry.getKey()))) throw new AssertionError("shared rows changed"); },"shared "+entry.getKey()+" rows preserved");
        }
        if(redisIsolated && redis!=null) {
            captureRedis(); for(String key:ownedRedisKeys) cleanup(()->redis.delete(key),"owned Redis resource removed");
            cleanup(()-> { if(!Objects.requireNonNull(redis.keys("*")).isEmpty()) throw new AssertionError("owned Redis resources remain"); },"task Redis database clean");
        }
        aiLogger.detachAppender(logs); logs.stop();
        if(context!=null) cleanup(()-> { context.close(); if(context.isActive()) throw new AssertionError("context active"); },"Spring backend context stopped");
        cleanup(HTTP::close,"probe HTTP client closed");
        boolean stopped=context==null || !context.isActive();
        JSON.writeValue(out.resolve("owned-resources.json").toFile(),Map.of("run",RUN,"syntheticUsers",List.of(900001,900002),"syntheticSessions",ownedSessions,
                "syntheticRows",Map.of("dishes",ownedDishes,"orders",ownedOrders,"address",ownedAddress,"heat",ownedHeat,"cluster",ownedCluster),"contextStopped",stopped,"fixtureStopped",fixtureExecutor==null || fixtureExecutor.isTerminated(),
                "redisDatabase",15,"ownedRedisResourceCount",ownedRedisKeys.size(),"resourcesRemoved",cleanupFailures.isEmpty(),"externalServicesUsed",false));
        JSON.writeValue(out.resolve("scenario-results.json").toFile(),Map.of("businessPassed",business,"cleanupPassed",cleanupFailures.isEmpty() && stopped,
                "failureClass",failure,"assertions",assertions,"scenarios",scenarios,"outboundModelRequests",outbound.get(),"cleanupFailures",cleanupFailures,
                "limits",List.of("local model/weather only; host owns real DeepSeek and official miniapp acceptance","user fixture JWTs use standalone sandbox keys; production identity interceptor unchanged","in-process protection does not coordinate multiple backend nodes")));
        finished=true;
    }
}
