import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.entity.Orders;
import com.codeying.properties.OrderRiskProperties;
import com.codeying.properties.SkyProperties;
import com.codeying.service.OrdersApplicationService;
import com.codeying.service.OrderRiskService;
import com.codeying.task.OrderRiskTask;
import com.codeying.utils.JwtUtil;
import com.codeying.websocket.WebSocketServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** New synthetic orders only. Real HTTP, Spring proxy, MyBatis and task-specific MySQL. */
public class Issue11Probe {
    static final long OWNER=911100, OTHER=911101, MATRIX=911010, MISSING=911199;
    static final String IMAGE="issue11-stored-image-A";
    static final List<String> TABLES=List.of("orders","order_detail","shopping_cart","user",
            "order_risk_feature_snapshot","order_risk_result");
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static JdbcTemplate sql;
    static StringRedisTemplate redis;
    static OrdersApplicationService service;
    static String base, user, other, admin;
    static int assertions;
    static volatile long raceId=-1;
    static volatile CyclicBarrier barrier;
    static AtomicInteger raceReads=new AtomicInteger();
    static final List<Boolean> writeTransactions=new CopyOnWriteArrayList<>();
    static final List<String> writes=new CopyOnWriteArrayList<>();
    static final List<String> triggers=new ArrayList<>();

    @Configuration
    public static class Fixtures {
        @Bean public Interceptor transactionAndRaceEvidence() {return new Evidence();}
    }
    @Intercepts({@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
        @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class})})
    public static class Evidence implements Interceptor {
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement=(MappedStatement)invocation.getArgs()[0];
            if(invocation.getMethod().getName().equals("update")) {
                String command=statement.getBoundSql(invocation.getArgs()[1]).getSql().trim().toLowerCase(Locale.ROOT);
                writes.add(command.split("\\s+")[0]);
                writeTransactions.add(TransactionSynchronizationManager.isActualTransactionActive());
            }
            Object result=invocation.proceed();
            if(barrier!=null && result instanceof List<?> rows && rows.size()==1
                    && rows.get(0) instanceof Orders o && o.getId()==raceId) {
                raceReads.incrementAndGet();
                barrier.await(15,TimeUnit.SECONDS); // Both requests must read the same old state.
            }
            return result;
        }
    }
    static void check(String name,boolean condition) {
        if(!condition) throw new AssertionError(name);
        assertions++;System.out.println("ASSERT "+name+" PASS");
    }
    static JsonNode call(String method,String path,String body,String token,boolean isAdmin) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
        if(token!=null) request.header(isAdmin?"token":"authentication",token);
        if(body==null)request.method(method,HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        return JSON.readTree(HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString()).body());
    }
    static boolean success(JsonNode response) {return response.path("code").asInt()==1;}
    static JsonNode accepted(String name,JsonNode response) {
        check(name+"_business_success",success(response));return response.path("data");
    }
    static Map<String,Object> row(long id) {return sql.queryForMap("SELECT * FROM orders WHERE id=?",id);}
    static List<Map<String,Object>> cart(long owner) {return sql.queryForList("SELECT * FROM shopping_cart WHERE user_id=? ORDER BY id",owner);}
    static Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String t:TABLES)result.put(t,sql.queryForList("SELECT * FROM `"+t+"` ORDER BY id"));
        return result;
    }
    static void createOrder(long id,int status,long owner) {
        sql.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,pay_method,amount,phone,estimated_delivery_time,pack_amount) VALUES(?,?,?,?,900001,'2020-01-01 12:00:00',?,1,19,'00000000011','2020-01-01 12:20:00',2)",
                id,"ISSUE11-"+id,status,owner,status==1?0:1);
        if(status!=1)sql.update("UPDATE orders SET checkout_time='2020-01-01 12:01:00' WHERE id=?",id);
    }
    static void reset(int state) {
        sql.update("UPDATE orders SET status=?,pay_status=?,checkout_time=?,cancel_time=NULL,delivery_time=NULL,cancel_reason=NULL,rejection_reason=NULL WHERE id=?",
            state,state==1?0:1,state==1?null:"2020-01-01 12:01:00",MATRIX);
    }
    static String body(String op,long id) {
        return switch(op) {
            case "payment" -> "{\"orderNumber\":\"ISSUE11-"+id+"\"}";
            case "reject" -> "{\"id\":"+id+",\"rejectionReason\":\"  fixture rejection  \"}";
            case "adminCancel" -> "{\"id\":"+id+",\"cancelReason\":\"  fixture cancellation  \"}";
            case "confirm" -> "{\"id\":"+id+"}";
            default -> null;
        };
    }
    static String path(String op,long id) {
        return switch(op) {
            case "payment" -> "/user/order/payment";
            case "confirm" -> "/admin/order/confirm";
            case "reject" -> "/admin/order/rejection";
            case "adminCancel" -> "/admin/order/cancel";
            case "userCancel" -> "/user/order/cancel/"+id;
            case "deliver" -> "/admin/order/delivery/"+id;
            case "complete" -> "/admin/order/complete/"+id;
            case "reminder" -> "/user/order/reminder/"+id;
            case "repetition" -> "/user/order/repetition/"+id;
            default -> throw new IllegalArgumentException(op);
        };
    }
    static boolean adminOp(String op) {return Set.of("confirm","reject","adminCancel","deliver","complete").contains(op);}
    static JsonNode operation(String op,long id,String auth) throws Exception {
        return call(op.equals("reminder")?"GET":op.equals("repetition")?"POST":"PUT",path(op,id),body(op,id),auth,adminOp(op));
    }
    static void unchangedExcept(String name,Map<String,Object> before,Map<String,Object> after,Set<String> fields) {
        var b=new LinkedHashMap<>(before);var a=new LinkedHashMap<>(after);
        fields.forEach(b::remove);fields.forEach(a::remove);check(name+"_unrelated_fields_unchanged",b.equals(a));
    }
    static void assertTransition(String op,int old,Map<String,Object> before) {
        var after=row(MATRIX);
        int expected=switch(op) {case "payment"->2;case "confirm"->3;case "deliver"->4;case "complete"->5;default->6;};
        check(op+"_state_"+old+"_persisted",((Number)after.get("status")).intValue()==expected);
        Set<String> changes=switch(op) {
            case "payment"->Set.of("status","pay_status","checkout_time");
            case "reject"->Set.of("status","rejection_reason","cancel_time");
            case "userCancel","adminCancel"->Set.of("status","cancel_reason","cancel_time");
            case "complete"->Set.of("status","delivery_time");
            default->Set.of("status");
        };
        unchangedExcept(op+"_state_"+old,before,after,changes);
        if(op.equals("payment"))check("payment_paid_and_checkout",((Number)after.get("pay_status")).intValue()==1 && after.get("checkout_time")!=null);
        if(op.equals("complete"))check("complete_delivery_time",after.get("delivery_time")!=null);
        if(op.equals("reject"))check("reject_trimmed_reason_and_time","fixture rejection".equals(after.get("rejection_reason")) && after.get("cancel_time")!=null);
        if(op.endsWith("Cancel"))check(op+"_reason_and_time",(op.equals("userCancel")?"用户取消订单":"fixture cancellation").equals(after.get("cancel_reason")) && after.get("cancel_time")!=null);
    }
    static void matrix() throws Exception {
        Map<String,Set<Integer>> allowed=new LinkedHashMap<>();
        allowed.put("payment",Set.of(1,2,3,4,5));allowed.put("confirm",Set.of(2));allowed.put("reject",Set.of(2));
        allowed.put("userCancel",Set.of(1,2));allowed.put("adminCancel",Set.of(1,2,3));allowed.put("deliver",Set.of(3));allowed.put("complete",Set.of(4));
        for(var e:allowed.entrySet()) {
            String op=e.getKey();String auth=adminOp(op)?admin:user;
            for(int state=1;state<=6;state++) {
                reset(state);var before=row(MATRIX);var response=operation(op,MATRIX,auth);
                check(op+"_state_"+state+"_allowed_matches",success(response)==e.getValue().contains(state));
                if(!success(response) || op.equals("payment") && state!=1)
                    check(op+"_state_"+state+"_no_write",before.equals(row(MATRIX)));
                else {
                    assertTransition(op,state,before);
                    var persisted=row(MATRIX);var repeated=operation(op,MATRIX,auth);
                    check(op+"_repeat_response",success(repeated)==op.equals("payment"));
                    check(op+"_repeat_no_regression",persisted.equals(row(MATRIX)));
                }
            }
            var before=snapshot();check(op+"_missing_order_denied",!success(operation(op,MISSING,auth)));
            check(op+"_missing_order_no_write",before.equals(snapshot()));
            check(op+"_anonymous_denied",!success(operation(op,MATRIX,null)));
            if(adminOp(op))check(op+"_user_token_to_admin_denied",!success(operation(op,MATRIX,user)));
            else {
                reset(1);before=snapshot();check(op+"_foreign_owner_denied",!success(operation(op,MATRIX,other)));
                check(op+"_foreign_no_write",before.equals(snapshot()));
            }
        }
        for(String op:List.of("reject","adminCancel")) {
            reset(2);var before=row(MATRIX);
            String field=op.equals("reject")?"rejectionReason":"cancelReason";
            check(op+"_blank_reason_denied",!success(call("PUT",path(op,MATRIX),"{\"id\":"+MATRIX+",\""+field+"\":\" \"}",admin,true)));
            check(op+"_blank_reason_no_write",before.equals(row(MATRIX)));
        }
    }
    static void matchingReads(long id,int status) throws Exception {
        var u=accepted("chain_user_read_"+status,call("GET","/user/order/orderDetail/"+id,null,user,false));
        var a=accepted("chain_admin_read_"+status,call("GET","/admin/order/details/"+id,null,admin,true));
        check("chain_two_consumers_same_checkout_"+status,u.path("checkoutTime").equals(a.path("checkoutTime")));
        if(status==5)check("chain_two_consumers_same_delivery",u.path("deliveryTime").equals(a.path("deliveryTime")));
        for(var data:List.of(u,a)) {
            check("chain_read_status_"+status,data.path("status").asInt()==status && data.path("payStatus").asInt()==1);
            check("chain_read_amount_image_"+status,data.path("amount").decimalValue().compareTo(new java.math.BigDecimal("19"))==0
                && IMAGE.equals(data.path("orderDetailList").get(0).path("image").asText())
                && data.path("orderDetailList").get(0).path("amount").decimalValue().compareTo(new java.math.BigDecimal("7.50"))==0);
            check("chain_checkout_visible_"+status,!data.path("checkoutTime").isNull() && !data.path("checkoutTime").isMissingNode());
            if(status==5)check("chain_delivery_visible",!data.path("deliveryTime").isNull() && !data.path("deliveryTime").isMissingNode());
        }
        var stored=row(id);
        check("chain_sql_status_"+status,((Number)stored.get("status")).intValue()==status && stored.get("checkout_time")!=null);
    }
    static void chain() throws Exception {
        long id=911001;
        var detail=sql.queryForList("SELECT * FROM order_detail WHERE order_id=? ORDER BY id",id);
        var result=accepted("chain_payment",operation("payment",id,user));
        check("mock_response_compatible",result.path("nonceStr").asText().equals("mock") && result.path("signType").asText().equals("RSA")
            && result.path("paySign").asText().equals("mock") && result.path("packageStr").asText().equals("prepay_id=mock"));
        matchingReads(id,2);
        int state=3;
        for(String op:List.of("confirm","deliver","complete")) {
            accepted("chain_"+op,operation(op,id,admin));matchingReads(id,state++);
            var before=row(id);accepted("chain_payment_retry_after_"+op,operation("payment",id,user));
            check("chain_payment_retry_after_"+op+"_unchanged",before.equals(row(id)));
        }
        check("chain_details_exactly_preserved",detail.equals(sql.queryForList("SELECT * FROM order_detail WHERE order_id=? ORDER BY id",id)));
    }
    static void markPaid() {
        for(int state=1;state<=6;state++) {
            reset(state);var before=row(MATRIX);boolean ok=true;
            try {service.markPaid(MATRIX);}catch(com.codeying.exception.OrderBusinessException failure){ok=false;}
            check("markPaid_state_"+state,ok==(state!=6));
            if(state==1)assertTransition("payment",1,before);
            else check("markPaid_state_"+state+"_no_regression",before.equals(row(MATRIX)));
        }
        boolean missing=false;try {service.markPaid(MISSING);}catch(com.codeying.exception.OrderBusinessException expected){missing=true;}
        check("markPaid_missing_denied",missing);
        for(Long invalid:new Long[]{null,0L,-1L}) {
            boolean rejected=false;try{service.markPaid(invalid);}catch(com.codeying.exception.OrderBusinessException expected){rejected=true;}
            check("markPaid_invalid_id_denied",rejected);
        }
        reset(3);sql.update("UPDATE orders SET pay_status=0 WHERE id=?",MATRIX);var before=row(MATRIX);
        boolean rejected=false;try{service.markPaid(MATRIX);}catch(com.codeying.exception.OrderBusinessException expected){rejected=true;}
        check("markPaid_inconsistent_unpaid_state_denied",rejected && before.equals(row(MATRIX)));
    }
    static void reminder() throws Exception {
        for(int state=1;state<=6;state++) {
            reset(state);redis.delete(RedisKeys.orderReminderThrottleKey(OWNER,MATRIX));var before=snapshot();
            check("reminder_state_"+state,success(operation("reminder",MATRIX,user))==(state==2));
            check("reminder_state_"+state+"_sql_unchanged",before.equals(snapshot()));
            if(state==2) {
                check("reminder_repeat_throttled",!success(operation("reminder",MATRIX,user)));
                check("reminder_redis_ttl",redis.getExpire(RedisKeys.orderReminderThrottleKey(OWNER,MATRIX))>0);
            } else check("reminder_wrong_state_no_throttle",!Boolean.TRUE.equals(redis.hasKey(RedisKeys.orderReminderThrottleKey(OWNER,MATRIX))));
        }
        reset(2);for(String auth:Arrays.asList(other,null))check("reminder_foreign_anonymous_denied",!success(operation("reminder",MATRIX,auth)));
        check("reminder_missing_denied",!success(operation("reminder",MISSING,user)));
        boolean denied=false;try{service.reminder(OTHER,MATRIX);}catch(com.codeying.exception.OrderBusinessException expected){denied=true;}
        check("reminder_reusable_ownership",denied);
    }
    static void createTrigger(String name,String definition) {sql.execute("CREATE TRIGGER "+name+" "+definition);triggers.add(name);}
    static void dropTrigger(String name) {sql.execute("DROP TRIGGER "+name);triggers.remove(name);}
    static void transactionEvidence(String name) {
        check(name+"_real_sql_writes_reached",!writeTransactions.isEmpty());
        check(name+"_all_sql_writes_inside_proxy_transaction",writeTransactions.stream().allMatch(Boolean::booleanValue));
    }
    static void repetition() throws Exception {
        var before=snapshot();for(String auth:Arrays.asList(other,null))check("repetition_foreign_anonymous_denied",!success(operation("repetition",911001,auth)));
        check("repetition_missing_denied",!success(operation("repetition",MISSING,user)));
        check("repetition_rejections_no_writes",before.equals(snapshot()));
        var oldCart=cart(OWNER);var otherCart=cart(OTHER);
        createTrigger("issue11_cart_failure","BEFORE INSERT ON shopping_cart FOR EACH ROW BEGIN IF NEW.user_id=911100 AND NEW.name='issue11-second' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue11 second insert'; END IF; END");
        // Task-only MyISAM evidence survives rollback and proves clear/first insert occurred.
        createTrigger("issue11_cart_observe","AFTER INSERT ON shopping_cart FOR EACH ROW BEGIN IF NEW.user_id=911100 THEN INSERT INTO issue11_write_evidence(operation_name) VALUES ('cart_insert'); END IF; END");
        createTrigger("issue11_cart_clear_observe","AFTER DELETE ON shopping_cart FOR EACH ROW BEGIN IF OLD.user_id=911100 THEN INSERT INTO issue11_write_evidence(operation_name) VALUES ('cart_clear'); END IF; END");
        try {
            writes.clear();writeTransactions.clear();
            check("repetition_second_insert_http_failed",!success(operation("repetition",911001,user)));
            transactionEvidence("repetition_rollback");
            check("repetition_clear_and_first_insert_reached",sql.queryForObject("SELECT COUNT(*) FROM issue11_write_evidence WHERE operation_name='cart_clear'",Integer.class)>0
                && sql.queryForObject("SELECT COUNT(*) FROM issue11_write_evidence WHERE operation_name='cart_insert'",Integer.class)==1);
            check("repetition_old_cart_exactly_restored",oldCart.equals(cart(OWNER)));
            check("repetition_other_cart_unchanged",otherCart.equals(cart(OTHER)));
            boolean failed=false;try{service.repetition(OWNER,911001L);}catch(RuntimeException expected){failed=true;}
            check("repetition_reusable_proxy_failure_rolled_back",failed && oldCart.equals(cart(OWNER)));
        } finally {dropTrigger("issue11_cart_failure");dropTrigger("issue11_cart_observe");dropTrigger("issue11_cart_clear_observe");}
        writes.clear();writeTransactions.clear();accepted("repetition_valid",operation("repetition",911001,user));transactionEvidence("repetition_success");
        var values=cart(OWNER);var details=sql.queryForList("SELECT * FROM order_detail WHERE order_id=911001 ORDER BY id");
        check("repetition_cart_size",values.size()==details.size());
        for(int i=0;i<values.size();i++)for(String field:List.of("dish_id","setmeal_id","name","image","amount","number","dish_flavor"))
            check("repetition_stored_"+field,Objects.equals(values.get(i).get(field),details.get(i).get(field)));
        check("repetition_valid_other_unchanged",otherCart.equals(cart(OTHER)));
        accepted("repetition_repeat",operation("repetition",911001,user));
        check("repetition_repeat_no_duplicate_cart",cart(OWNER).size()==details.size());
        // Preserve established semantics: every own existing status, even empty details, may repeat.
        for(int state=1;state<=6;state++){reset(state);accepted("repetition_own_state_"+state,operation("repetition",MATRIX,user));check("repetition_empty_details_clears_only_own",cart(OWNER).isEmpty() && otherCart.equals(cart(OTHER)));}
    }
    static void coreFailures() throws Exception {
        createTrigger("issue11_status_failure","BEFORE UPDATE ON orders FOR EACH ROW BEGIN IF OLD.id=911010 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue11 core status failure'; END IF; END");
        try {
            // Reset cannot run while rejecting trigger exists; suspend between each synthetic state setup.
            for(var pair:List.of(new Object[]{"payment",1},new Object[]{"confirm",2},new Object[]{"reject",2},new Object[]{"userCancel",1},new Object[]{"adminCancel",3},new Object[]{"deliver",3},new Object[]{"complete",4})) {
                dropTrigger("issue11_status_failure");reset((Integer)pair[1]);
                createTrigger("issue11_status_failure","BEFORE UPDATE ON orders FOR EACH ROW BEGIN IF OLD.id=911010 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue11 core status failure'; END IF; END");
                String op=(String)pair[0];var before=snapshot();writes.clear();writeTransactions.clear();
                var response=operation(op,MATRIX,adminOp(op)?admin:user);
                check(op+"_core_sql_http_failed",!success(response));
                check(op+"_core_full_rows_unchanged",before.equals(snapshot()));transactionEvidence(op+"_core_failure");
            }
            dropTrigger("issue11_status_failure");reset(1);
            createTrigger("issue11_status_failure","BEFORE UPDATE ON orders FOR EACH ROW BEGIN IF OLD.id=911010 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue11 core status failure'; END IF; END");
            var before=row(MATRIX);writeTransactions.clear();boolean failed=false;
            try{service.markPaid(MATRIX);}catch(RuntimeException expected){failed=true;}
            check("markPaid_core_failed_and_unchanged",failed && before.equals(row(MATRIX)));transactionEvidence("markPaid_core_failure");
        } finally {if(triggers.contains("issue11_status_failure"))dropTrigger("issue11_status_failure");}
    }
    static void race(long id,String first,String second) throws Exception {
        barrier=new CyclicBarrier(2);raceId=id;raceReads.set(0);
        var executor=Executors.newFixedThreadPool(2);JsonNode a,b;
        try {
            var x=executor.submit(()->operation(first,id,adminOp(first)?admin:user));
            var y=executor.submit(()->operation(second,id,adminOp(second)?admin:user));
            a=x.get(30,TimeUnit.SECONDS);b=y.get(30,TimeUnit.SECONDS);
        } finally {barrier=null;raceId=-1;executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}
        check(first+"_vs_"+second+"_both_read_same_old_state",raceReads.get()==2);
        check(first+"_vs_"+second+"_exactly_one_winner",success(a)!=success(b));
        var after=row(id);int state=((Number)after.get("status")).intValue();
        check(first+"_vs_"+second+"_persisted_winner",state==(success(a)?(first.equals("confirm")?3:2):6));
        if(state==6)check(first+"_cancelled_retry_cannot_regress",!success(operation("payment",id,user)) && row(id).equals(after));
        else {accepted(first+"_paid_retry_compatible",operation("payment",id,user));check(first+"_retry_no_regression",after.equals(row(id)));}
    }
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(App.class,Fixtures.class);
        try(var context=app.run("--spring.config.location=file:"+Path.of(args[0]).toAbsolutePath(),
                "--spring.profiles.active=dev,afk","--server.port=0","--server.address=127.0.0.1","--logging.level.root=WARN")) {
            sql=context.getBean(JdbcTemplate.class);redis=context.getBean(StringRedisTemplate.class);service=context.getBean(OrdersApplicationService.class);
            check("DATABASE_is_sandcastle_fixture","sandcastle_fixture".equals(sql.queryForObject("SELECT DATABASE()",String.class)));
            for(String table:TABLES) {
                check("unused_task_ids_"+table,sql.queryForObject("SELECT COUNT(*) FROM `"+table+"` WHERE id BETWEEN 911001 AND 911199",Integer.class)==0);
                var ddl=sql.queryForMap("SHOW CREATE TABLE `"+table+"`");check("transactional_"+table,ddl.values().stream().anyMatch(v->v.toString().contains("ENGINE=InnoDB")));
            }
            check("unused_task_child_rows",sql.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id BETWEEN 911001 AND 911199",Integer.class)==0
                && sql.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id IN (?,?)",Integer.class,OWNER,OTHER)==0);
            check("unused_task_triggers",sql.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME LIKE 'issue11\\_%'",Integer.class)==0);
            check("unused_task_evidence_table",sql.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue11_write_evidence'",Integer.class)==0);
            check("real_Service_Spring_proxy",AopUtils.isAopProxy(service));
            check("background_training_scoring_disabled",!Boolean.TRUE.equals(context.getBean(OrderRiskProperties.class).getTask().getScoringEnabled()));
            var original=snapshot();var jwt=context.getBean(SkyProperties.class).getJwt();
            user=JwtUtil.createToken(jwt.getUserSecretKey(),600000,OWNER,"fixture","user");
            other=JwtUtil.createToken(jwt.getUserSecretKey(),600000,OTHER,"fixture","user");
            admin=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","admin");
            base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
            boolean evidenceCreated=false;
            try {
                sql.update("INSERT INTO user(id,openid,name) VALUES(911100,'issue11-owner','Issue11'),(911101,'issue11-other','Issue11 Other')");
                createOrder(911001,1,OWNER);createOrder(MATRIX,1,OWNER);createOrder(911080,2,OWNER);createOrder(911081,1,OWNER);
                createOrder(911002,5,OTHER);
                sql.update("INSERT INTO order_detail(id,order_id,name,image,dish_id,number,amount,dish_flavor) VALUES(911021,911001,'issue11-first',?,900001,2,7.5,'stored-flavor'),(911022,911001,'issue11-second',NULL,900001,1,4,NULL)",IMAGE);
                sql.update("INSERT INTO shopping_cart(id,name,user_id,dish_id,number,amount,create_time) VALUES(911031,'issue11-old-cart',911100,900001,3,18,NOW()),(911032,'issue11-other-cart',911101,900001,1,18,NOW())");
                sql.execute("CREATE TABLE issue11_write_evidence(operation_name VARCHAR(32) NOT NULL) ENGINE=MyISAM");evidenceCreated=true;
                var untouched=row(911002);var otherCart=cart(OTHER);
                matrix();chain();markPaid();reminder();repetition();coreFailures();
                race(911080,"confirm","reject");race(911081,"payment","userCancel");
                accepted("admin_statistics",call("GET","/admin/order/statistics",null,admin,true));
                var stats=call("GET","/admin/order/statistics",null,admin,true).path("data");
                check("statistics_named_counts_correct",stats.path("toBeConfirmed").asLong()==sql.queryForObject("SELECT COUNT(*) FROM orders WHERE status=2",Long.class)
                    && stats.path("confirmed").asLong()==sql.queryForObject("SELECT COUNT(*) FROM orders WHERE status=3",Long.class)
                    && stats.path("deliveryInProgress").asLong()==sql.queryForObject("SELECT COUNT(*) FROM orders WHERE status=4",Long.class));
                var before=snapshot();context.getBean(OrderRiskTask.class).runTraining();context.getBean(OrderRiskService.class).scoreOrder(911001L,"paid");
                check("existing_disabled_risk_task_hook_no_mutation",before.equals(snapshot()));
                new WebSocketServer().onMessage("{\"orderId\":911001,\"status\":1}","issue11");
                check("websocket_transport_message_cannot_mutate_fulfillment",before.equals(snapshot()));
                check("other_order_cart_exactly_unchanged",untouched.equals(row(911002)) && otherCart.equals(cart(OTHER)));
                check("scoring_disabled_no_risk_writes",original.get("order_risk_result").equals(snapshot().get("order_risk_result")) && original.get("order_risk_feature_snapshot").equals(snapshot().get("order_risk_feature_snapshot")));
            } finally {
                barrier=null;
                for(String trigger:List.copyOf(triggers))dropTrigger(trigger);
                if(evidenceCreated)sql.execute("DROP TABLE issue11_write_evidence");
                redis.delete(List.of(RedisKeys.orderReminderThrottleKey(OWNER,MATRIX),RedisKeys.orderReminderThrottleKey(OTHER,MATRIX),RedisKeys.orderReminderThrottleKey(OWNER,MISSING)));
                sql.update("DELETE FROM shopping_cart WHERE user_id IN (?,?)",OWNER,OTHER);
                sql.update("DELETE FROM order_detail WHERE id IN (911021,911022) AND order_id=911001");
                sql.update("DELETE FROM orders WHERE id IN (911001,911002,911010,911080,911081)");
                sql.update("DELETE FROM user WHERE id IN (?,?)",OWNER,OTHER);
                check("cleanup_shared_rows_exactly_preserved",original.equals(snapshot()));
                check("cleanup_task_triggers_absent",sql.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME LIKE 'issue11\\_%'",Integer.class)==0);
                check("cleanup_task_evidence_table_absent",sql.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue11_write_evidence'",Integer.class)==0);
                check("cleanup_task_redis_absent",!Boolean.TRUE.equals(redis.hasKey(RedisKeys.orderReminderThrottleKey(OWNER,MATRIX))));
            }
        }
        System.out.println("ASSERT Spring_context_closed PASS");assertions++;
        System.out.println("ISSUE11 ACCEPTANCE PASS assertions="+assertions);
    }
}
