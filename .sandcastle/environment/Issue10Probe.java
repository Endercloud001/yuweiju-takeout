import com.codeying.App;
import com.codeying.constant.RedisKeys;
import com.codeying.dto.user.addressbook.AddressBookDTO;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.entity.*;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.*;
import com.codeying.service.*;
import com.codeying.utils.BaiduMapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fresh isolated acceptance; real Spring proxies, MyBatis, MySQL, Redis and authenticated HTTP. */
public class Issue10Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18090";
    static final String RUN = "i10_" + UUID.randomUUID().toString().replace("-", "").substring(0,10);
    static final List<String> assertions = new ArrayList<>(), cleanupFailures = new ArrayList<>();
    static final List<String> triggers = new ArrayList<>(), keys = new ArrayList<>();
    static final List<Long> users = new ArrayList<>(), addresses = new ArrayList<>(), orders = new ArrayList<>();
    static final List<Long> cartIds = new ArrayList<>();
    static final Map<String,List<Long>> fixtureIds = new LinkedHashMap<>();
    static void track(String table, long id) { fixtureIds.computeIfAbsent(table, ignored -> new ArrayList<>()).add(id); }
    static final AtomicInteger mapCalls = new AtomicInteger();
    static JdbcTemplate jdbc;
    static String tokenA, tokenB, tokenAdmin;
    static long userA, userB;
    static void require(boolean condition, String assertion) {
        if (!condition) throw new AssertionError(assertion);
        assertions.add(assertion);
        System.out.println("ISSUE10 PASS " + assertion);
    }
    static void group(String name) { System.out.println("ISSUE10 GROUP " + name + " " + Instant.now()); }

    @Configuration(proxyBeanMethods = false)
    public static class ProbeConfig {
        /** Probe-only deterministic map substitute: no real geocoding, weather, OSS, model or payment call. */
        @Bean @Primary WechatService isolatedLogin() {
            return code -> {
                if (!code.startsWith("mock_" + RUN)) throw new IllegalArgumentException("probe login only");
                return "mock_openid_" + code;
            };
        }
        @Bean @Primary BaiduMapUtil isolatedMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop, String address, String unusedKey) {
                    require("Isolation street".equals(shop) && address.contains(RUN), "checkout uses synthetic address through explicit map substitute");
                    mapCalls.incrementAndGet(); return 7;
                }
            };
        }
    }
    static HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
        if (token != null) b.header(path.startsWith("/admin/") ? "token" : "authentication", token);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode result(HttpResponse<String> response, int code, String assertion) throws Exception {
        var n = JSON.readTree(response.body());
        require(n.path("code").asInt(-999) == code, assertion);
        return n.path("data");
    }
    static JsonNode request(String method, String path, String token, Object body) throws Exception {
        return result(call(method,path,token,body),1,method+" "+path.split("\\?")[0]);
    }
    static void refused(String method, String path, String token, Object body, String message) throws Exception {
        var n = JSON.readTree(call(method,path,token,body).body());
        require(n.path("code").asInt(-1)==0 && n.path("message").asText().contains(message), "HTTP business refusal: "+message);
    }
    static List<Map<String,Object>> rows(String table, String key, long id) {
        // Identifiers are source constants; all values are bound parameters.
        return jdbc.queryForList("SELECT * FROM "+table+" WHERE "+key+"=? ORDER BY id",id);
    }
    static List<Map<String,Object>> allRows(String table) {
        String order = switch (table) {
            case "order_risk_result" -> "order_id, model_version";
            case "order_risk_feature_snapshot" -> "order_id, feature_version";
            default -> "id";
        };
        return jdbc.queryForList("SELECT * FROM "+table+" ORDER BY "+order);
    }
    static List<Long> ids(JsonNode data) {
        var result = new ArrayList<Long>(); data.forEach(n -> result.add(n.path("id").asLong())); return result;
    }
    static ShoppingCartChangeDTO dish(long id, String flavor) {
        var b = new ShoppingCartChangeDTO(); b.setDishId(id); b.setDishFlavor(flavor); return b;
    }
    static ShoppingCartChangeDTO meal(long id) {
        var b = new ShoppingCartChangeDTO(); b.setSetmealId(id); return b;
    }
    static Map<String,Object> addressBody(String suffix, Integer isDefault) {
        return Map.of("consignee",RUN+suffix,"phone"," 00000000000 ","detail"," "+RUN+suffix+" door ",
                "sex","1","provinceName","P","cityName","C","districtName","D","label","home","isDefault",isDefault);
    }
    static AddressBookDTO addressDTO(String suffix, int isDefault) {
        var b = new AddressBookDTO(); b.setConsignee(RUN+suffix); b.setPhone(" 00000000000 ");
        b.setDetail(" "+RUN+suffix+" door "); b.setIsDefault(isDefault); return b;
    }
    static long createAddress(String token, long owner, String suffix, int isDefault) throws Exception {
        request("POST","/user/addressBook",token,addressBody(suffix,isDefault));
        long id = jdbc.queryForObject("SELECT id FROM address_book WHERE user_id=? AND consignee=?",Long.class,owner,RUN+suffix);
        addresses.add(id); return id;
    }
    static void reject(Runnable action, String assertion) {
        boolean refused = false;
        try { action.run(); } catch (BusinessException expected) { refused = true; }
        require(refused,assertion);
    }
    static void failed(Runnable action, String marker, String assertion) {
        Throwable failure = null;
        try { action.run(); } catch (RuntimeException expected) { failure = expected; }
        boolean causalMarker = false;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage()!=null && t.getMessage().contains(marker)) causalMarker=true;
        }
        require(failure!=null && causalMarker,assertion);
    }
    static void trigger(String name, String event, String table, String condition, String marker) {
        // Task-owned identifiers and numeric IDs only; no request text is interpolated.
        triggers.add(name);
        jdbc.execute("CREATE TRIGGER "+name+" BEFORE "+event+" ON "+table+" FOR EACH ROW BEGIN IF "+condition
                +" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+marker+"'; END IF; END");
    }
    static void drop(String name) { jdbc.execute("DROP TRIGGER IF EXISTS "+name); }
    static void cleanup(Runnable action, String assertion) {
        try { action.run(); require(true,assertion); }
        catch (RuntimeException | AssertionError failure) { cleanupFailures.add(assertion+": "+failure.getClass().getSimpleName()); }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(); Path out=root.resolve(".scratch/issue10");
        Files.createDirectories(out);
        Path runtimeFiles=out.resolve(RUN); Files.createDirectories(runtimeFiles);
        System.setProperty("java.io.tmpdir",runtimeFiles.toString());
        String started=Instant.now().toString();
        var context=new SpringApplication(App.class, ProbeConfig.class).run(
                "--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk","--server.address=127.0.0.1","--server.port=18090","--server.tomcat.basedir="+runtimeFiles.resolve("tomcat"),"--logging.level.root=OFF",
                "--analysis.task.enabled=false","--order-risk.task.scoring-enabled=false",
                "--analysis.xgboost.model-dir="+out.resolve(RUN+"/analysis-models"),"--order-risk.model-dir="+out.resolve(RUN+"/order-risk-models"));
        jdbc=context.getBean(JdbcTemplate.class); var redis=context.getBean(StringRedisTemplate.class);
        var cart=context.getBean(ShoppingCartService.class); var book=context.getBean(AddressBookService.class);
        var cartMapper=context.getBean(ShoppingCartMapper.class); var bookMapper=context.getBean(AddressBookMapper.class);
        String codeA="mock_"+RUN+"a",codeB="mock_"+RUN+"b";
        String openidA="mock_openid_"+codeA,openidB="mock_openid_"+codeB;
        String adminName="sandbox_admin",adminPassword="sandbox-only-login";
        Long adminId=null,category=null,good=null,stopped=null,setmeal=null;
        boolean isolated=false,adminOwned=false,businessPassed=false,cleanupPassed=false;
        Map<String,List<Map<String,Object>>> originals=new LinkedHashMap<>();
        Map<String,byte[]> redisOriginals=new LinkedHashMap<>();
        String failureClass="";
        try {
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"isolated MySQL database");
            try (var connection=jdbc.getDataSource().getConnection()) {
                require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"),"isolated MySQL host/port before writes");
            }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host"))
                    && "6379".equals(context.getEnvironment().getProperty("spring.data.redis.port"))
                    && "0".equals(context.getEnvironment().getProperty("spring.data.redis.database")),"isolated Redis host/port/database before writes");
            isolated=true;
            for (String table:List.of("employee","user","address_book","shopping_cart","category","dish","dish_flavor",
                    "setmeal","setmeal_dish","orders","order_detail","order_risk_result","order_risk_feature_snapshot"))
                originals.put(table,allRows(table));
            var originalKeys=redis.keys("*");
            if(originalKeys!=null) for(String key:originalKeys) redisOriginals.put(key,redis.dump(key));
            String redisKey="afk:issue10:"+RUN; keys.add(redisKey);
            redis.opsForValue().set(redisKey,"isolated");
            require("isolated".equals(redis.opsForValue().get(redisKey)),"real Redis owned key roundtrip");
            var employees=context.getBean(EmployeeMapper.class);
            var existing=jdbc.queryForList("SELECT id FROM employee WHERE username=?",Long.class,adminName);
            if(existing.isEmpty()) {
                var e=new Employee(); e.setUsername(adminName); e.setPassword(adminPassword); e.setName(RUN);
                e.setPhone("00000000000"); e.setSex("1"); e.setIdNumber("synthetic"); e.setStatus(1);
                e.setCreateTime(new Date()); e.setUpdateTime(new Date()); e.setCreateUser(900001L); e.setUpdateUser(900001L);
                employees.insert(e); adminId=e.getId(); adminOwned=true; track("employee",adminId);
            } else {
                require(employees.findEnabledByCredentials(adminName,adminPassword)!=null,"preexisting synthetic admin unchanged and compatible");
                adminId=existing.get(0);
            }
            String adminToken=result(call("POST","/admin/employee/login",null,Map.of("username",adminName,"password",adminPassword)),1,"normal synthetic admin HTTP password login").path("token").asText();
            tokenAdmin=adminToken;
            var loginA=request("POST","/user/user/login",null,Map.of("code",codeA));
            tokenA=loginA.path("token").asText();
            userA=jdbc.queryForObject("SELECT id FROM user WHERE openid=?",Long.class,openidA); users.add(userA);
            var loginB=request("POST","/user/user/login",null,Map.of("code",codeB));
            tokenB=loginB.path("token").asText();
            userB=jdbc.queryForObject("SELECT id FROM user WHERE openid=?",Long.class,openidB); users.add(userB);
            require(userA!=userB,"two distinct dev mock synthetic users (not real WeChat)");
            require(AopUtils.isAopProxy(cart)&&AopUtils.isAopProxy(book),"cart/address real Spring transaction proxies");
            recordManifest(out);
            require(AopUtils.isAopProxy(context.getBean(OrdersApplicationService.class)), "order real transaction proxy");
            // Confirm actual MySQL definitions before seeding products or orders.
            for (String table:List.of("dish","setmeal","shopping_cart","orders","order_detail")) {
                String ddl=jdbc.queryForMap("SHOW CREATE TABLE "+table).values().stream().filter(v->v.toString().contains("CREATE TABLE")).findFirst().orElseThrow().toString();
                require(ddl.contains("decimal(10,2)") && ddl.contains("PRIMARY KEY (`id`)") && ddl.contains("ENGINE=InnoDB"), "actual "+table+" schema decimal10,2 primary id InnoDB");
                if(table.equals("shopping_cart")||table.equals("order_detail")) require(ddl.contains("`number` int NOT NULL")&&ddl.contains("`amount` decimal(10,2) NOT NULL"),"actual "+table+" quantity/amount NOT NULL");
            }
            var c=new Category(); c.setName(RUN+"cat"); c.setType(1); c.setSort(0); c.setStatus(1);
            c.setCreateTime(new Date()); c.setUpdateTime(new Date()); c.setCreateUser(adminId); c.setUpdateUser(adminId);
            context.getBean(CategoryMapper.class).insert(c); category=c.getId(); track("category",category);
            good=createDish(context.getBean(DishMapper.class),category,adminId,"dish",1);
            stopped=createDish(context.getBean(DishMapper.class),category,adminId,"stop",0);
            var s=new Setmeal(); s.setName(RUN+"meal"); s.setCategoryId(category); s.setStatus(1);
            s.setPrice(new BigDecimal("18.00")); s.setImage("meal-old.png"); s.setCreateTime(new Date()); s.setUpdateTime(new Date());
            s.setCreateUser(adminId); s.setUpdateUser(adminId); context.getBean(SetmealMapper.class).insert(s); setmeal=s.getId(); track("setmeal",setmeal);
            long dishId=good,stoppedId=stopped,mealId=setmeal;
            jdbc.update("UPDATE dish SET price=18.00 WHERE id=?",dishId);
            long addressA=createAddress(tokenA,userA,"a",1), addressB=createAddress(tokenB,userB,"b",1);
            var billing=context.getBean(OrdersApplicationService.class);
            var ordersMapper=context.getBean(OrdersMapper.class);
            // Seed an owned historical order with a deliberately different image/amount.
            var old=new Orders(); old.setUserId(userA); old.setAddressBookId(addressA); old.setNumber(RUN+"old");
            old.setStatus(5); old.setOrderTime(new Date()); old.setPayMethod(1); old.setPayStatus(1); old.setAmount(new BigDecimal("19.00"));
            old.setDeliveryStatus(1); old.setTablewareStatus(0); old.setPackAmount(1); old.setRemark(RUN);
            ordersMapper.insert(old); orders.add(old.getId());
            var oldDetail=new OrderDetail(); oldDetail.setOrderId(old.getId()); oldDetail.setDishId(dishId); oldDetail.setName(RUN+"old");
            oldDetail.setImage("historical-only.png"); oldDetail.setNumber(1); oldDetail.setAmount(new BigDecimal("18.00"));
            context.getBean(OrderDetailMapper.class).insert(oldDetail);
            var oldRows=rows("orders","id",old.getId()); var oldDetails=rows("order_detail","order_id",old.getId());
            cart.addItem(userB,dish(dishId,null)); var otherCart=rows("shopping_cart","user_id",userB);
            var otherAddress=rows("address_book","user_id",userB);
            for(String path:List.of("/user/shoppingCart/list","/user/order/orderDetail/"+old.getId())) {
                require(call("GET",path,null,null).statusCode()==401,"anonymous read refused");
                require(call("GET",path,adminToken,null).statusCode()==401,"admin scope user read refused");
            }
            require(call("POST","/user/order/submit",null,submission(addressA)).statusCode()==401,"anonymous submit refused");
            require(call("POST","/user/order/submit",adminToken,submission(addressA)).statusCode()==401,"wrong scope submit refused");
            require(call("PUT","/user/order/payment",null,Map.of("orderNumber",RUN)).statusCode()==401,"anonymous mock payment refused");
            require(call("PUT","/user/order/payment",adminToken,Map.of("orderNumber",RUN)).statusCode()==401,"wrong scope mock payment refused");
            require(call("GET","/user/order/historyOrders?page=1&pageSize=100",null,null).statusCode()==401,"anonymous history refused");
            require(call("GET","/user/order/historyOrders?page=1&pageSize=100",adminToken,null).statusCode()==401,"wrong scope history refused");
            require(call("GET","/admin/order/conditionSearch?page=1&pageSize=100",null,null).statusCode()==401,"anonymous admin list refused");
            require(call("GET","/admin/order/conditionSearch?page=1&pageSize=100",tokenA,null).statusCode()==401,"wrong scope admin list refused");
            reject(()->billing.submit(null,dto(addressA)),"direct missing identity refused");
            reject(()->billing.submit(-1L,dto(addressA)),"direct invalid identity refused");
            reject(()->billing.submit(userA,dto(addressA)),"direct empty cart refused");
            refused("POST","/user/order/submit",tokenA,submission(addressA),"购物车为空");
            // This is the actual bug seam: before the fix 36+client packing2 persists38, expected40.
            cart.addItem(userA,dish(dishId,null)); cart.addItem(userA,dish(dishId,null));
            var repro=submission(addressA); repro.put("amount",44); repro.put("packAmount",2); repro.put("userId",userB);
            var two=request("POST","/user/order/submit",tokenA,repro); assertOrder(context,two.path("id").asLong(),two.path("orderAmount").decimalValue(),"40.00",2);
            group("ACTUAL_TWO18_36_PLUS2_PLUS2_EQUALS40");
            cart.addItem(userA,dish(dishId,null));
            var one=billing.submit(userA,dto(addressA)); assertOrder(context,one.getId(),one.getOrderAmount(),"21.00",1);
            cart.addItem(userA,dish(dishId,null)); cart.addItem(userA,meal(mealId));
            var mixed=submission(addressA); mixed.put("amount",-999); mixed.put("packAmount",-999);
            var mix=request("POST","/user/order/submit",tokenA,mixed); assertOrder(context,mix.path("id").asLong(),mix.path("orderAmount").decimalValue(),"40.00",2);
            cart.addItem(userA,dish(dishId,null)); cart.addItem(userA,dish(dishId,null));
            cart.addItem(userA,meal(mealId)); cart.addItem(userA,meal(mealId)); cart.addItem(userA,meal(mealId));
            var five=billing.submit(userA,dto(addressA)); assertOrder(context,five.getId(),five.getOrderAmount(),"97.00",5);
            // Read must give current catalog price without writing cart cached fields.
            cart.addItem(userA,dish(dishId,null));
            jdbc.update("UPDATE shopping_cart SET amount=-12.00 WHERE user_id=?",userA);
            jdbc.update("UPDATE dish SET price=18.37 WHERE id=?",dishId);
            var cached=rows("shopping_cart","user_id",userA);
            require(request("GET","/user/shoppingCart/list",tokenA,null).get(0).path("amount").decimalValue().compareTo(new BigDecimal("18.37"))==0,"trusted HTTP read current price after cached tampering/catalog edit");
            require(rows("shopping_cart","user_id",userA).equals(cached),"trusted read does not rewrite cached cart");
            var cents=request("POST","/user/order/submit",tokenA,submission(addressA)); assertOrder(context,cents.path("id").asLong(),cents.path("orderAmount").decimalValue(),"21.37",1);
            // Catalog changes after displayed cart are authoritative on submit and detail snapshot.
            cart.addItem(userA,dish(dishId,null)); jdbc.update("UPDATE dish SET price=19.99 WHERE id=?",dishId);
            var stale=submission(addressA); stale.put("amount",21.37); stale.put("packAmount",0);
            var changed=request("POST","/user/order/submit",tokenA,stale); assertOrder(context,changed.path("id").asLong(),changed.path("orderAmount").decimalValue(),"22.99",1);
            require(((BigDecimal)rows("order_detail","order_id",changed.path("id").asLong()).get(0).get("amount")).compareTo(new BigDecimal("19.99"))==0,"detail stores trusted current unit price");
            group("TRUSTED_PRICE_CENTS_STALE_CLAIMS_PASS");
            cart.addItem(userA,dish(dishId,null));
            var baselineCart=rows("shopping_cart","user_id",userA);
            long beforeOrders=jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Long.class,userA);
            for(long bad:List.of(addressB,0L,-1L)) {
                reject(()->billing.submit(userA,dto(bad)),"direct foreign/missing address refused");
                refused("POST","/user/order/submit",tokenA,submission(bad),bad==addressB?"地址不存在":"参数错误");
            }
            refused("POST","/user/order/submit",tokenA,Map.of(),"参数错误");
            reject(()->billing.submit(userA,new com.codeying.dto.user.order.OrdersSubmitDTO()),"direct missing address refused");
            reject(()->billing.submit(Long.MAX_VALUE,dto(addressA)),"direct missing user refused");
            for(int quantity:List.of(0,-1,Integer.MAX_VALUE)) {
                jdbc.update("UPDATE shopping_cart SET number=? WHERE user_id=?",quantity,userA);
                reject(()->billing.submit(userA,dto(addressA)),"direct invalid quantity/overflow refused");
                refused("POST","/user/order/submit",tokenA,submission(addressA),quantity==Integer.MAX_VALUE?"金额":"数量");
            }
            jdbc.update("UPDATE shopping_cart SET number=1 WHERE user_id=?",userA);
            // Both rows fit the real int schema, but their total cannot fit pack_amount int.
            cart.addItem(userA,meal(mealId));
            jdbc.update("UPDATE dish SET price=0.00 WHERE id=?",dishId);
            jdbc.update("UPDATE setmeal SET price=0.00 WHERE id=?",mealId);
            jdbc.update("UPDATE shopping_cart SET number=? WHERE user_id=? AND dish_id=?",Integer.MAX_VALUE,userA,dishId);
            reject(()->billing.submit(userA,dto(addressA)),"direct total packing int overflow refused");
            refused("POST","/user/order/submit",tokenA,submission(addressA),"数量");
            jdbc.update("DELETE FROM shopping_cart WHERE user_id=? AND setmeal_id=?",userA,mealId);
            jdbc.update("UPDATE shopping_cart SET number=1 WHERE user_id=?",userA);
            jdbc.update("UPDATE setmeal SET price=18.00 WHERE id=?",mealId);
            for(String price:List.of("-0.01","99999999.99")) {
                jdbc.update("UPDATE dish SET price=? WHERE id=?",new BigDecimal(price),dishId);
                reject(()->billing.submit(userA,dto(addressA)),"direct negative price/amount overflow refused");
                refused("POST","/user/order/submit",tokenA,submission(addressA),"金额");
            }
            jdbc.update("UPDATE dish SET price=18.00,status=0 WHERE id=?",dishId);
            reject(()->billing.submit(userA,dto(addressA)),"direct unsaleable catalog refused");
            refused("POST","/user/order/submit",tokenA,submission(addressA),"商品不可售");
            jdbc.update("UPDATE dish SET status=1 WHERE id=?",dishId);
            for(String mutate:List.of("dish_id=NULL","dish_id=0","setmeal_id="+mealId)) {
                jdbc.update("UPDATE shopping_cart SET "+mutate+" WHERE user_id=?",userA);
                reject(()->billing.submit(userA,dto(addressA)),"direct malformed/missing product refused");
                refused("POST","/user/order/submit",tokenA,submission(addressA),"商品");
                jdbc.update("UPDATE shopping_cart SET dish_id=?,setmeal_id=NULL WHERE user_id=?",dishId,userA);
            }
            require(rows("shopping_cart","user_id",userA).equals(baselineCart),"refusals preserve cart once owned malformed fixtures restored");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Long.class,userA)==beforeOrders,"all invalid requests create no orders");
            // Exact decimal storage upper edge: 99999996.99 goods +1+2 fits.
            jdbc.update("UPDATE dish SET price=99999996.99 WHERE id=?",dishId);
            var maximum=billing.submit(userA,dto(addressA)); assertOrder(context,maximum.getId(),maximum.getOrderAmount(),"99999999.99",1);
            jdbc.update("UPDATE dish SET price=0.00 WHERE id=?",dishId);
            cart.addItem(userA,dish(dishId,null));
            var zero=billing.submit(userA,dto(addressA)); assertOrder(context,zero.getId(),zero.getOrderAmount(),"3.00",1);
            jdbc.update("UPDATE dish SET price=18.00 WHERE id=?",dishId);
            group("INVALID_CART_ADDRESS_RANGE_PASS");
            // Real trigger failures at both core writes prove proxy transaction rollback.
            cart.addItem(userA,dish(dishId,null));
            for(String table:List.of("orders","order_detail")) {
                var beforeCart=rows("shopping_cart","user_id",userA);
                var beforeOrder=allRows("orders"); var beforeDetail=allRows("order_detail");
                String name=RUN+"_"+table;
                String condition=table.equals("orders")?"NEW.user_id="+userA:"NEW.order_id IN (SELECT id FROM orders WHERE user_id="+userA+")";
                trigger(name,"INSERT",table,condition,"i10_core_fail");
                failed(()->billing.submit(userA,dto(addressA)),"i10_core_fail","direct real "+table+" trigger failure retains cause");
                refused("POST","/user/order/submit",tokenA,submission(addressA),"下单失败");
                require(allRows("orders").equals(beforeOrder)&&allRows("order_detail").equals(beforeDetail)&&rows("shopping_cart","user_id",userA).equals(beforeCart),"direct and HTTP "+table+" failure no partial writes/cart clear");
                drop(name);
            }
            var retry=request("POST","/user/order/submit",tokenA,submission(addressA)); assertOrder(context,retry.path("id").asLong(),retry.path("orderAmount").decimalValue(),"21.00",1);
            refused("PUT","/user/order/payment",tokenB,Map.of("orderNumber",retry.path("orderNumber").asText()),"订单不存在");
            var beforePay=ordersMapper.selectById(retry.path("id").asLong()).getAmount();
            request("PUT","/user/order/payment",tokenA,Map.of("orderNumber",retry.path("orderNumber").asText()));
            require(ordersMapper.selectById(retry.path("id").asLong()).getAmount().compareTo(beforePay)==0,"mock payment leaves authoritative stored new amount unchanged");
            var history=request("GET","/user/order/historyOrders?page=1&pageSize=100",tokenA,null);
            require(history.path("records").size()==orders.size(),"real HTTP history lists only owner stored orders");
            var adminPage=request("GET","/admin/order/conditionSearch?page=1&pageSize=100&number="+RUN+"old",tokenAdmin,null);
            require(adminPage.path("records").size()==1&&adminPage.path("records").get(0).path("amount").decimalValue().compareTo(new BigDecimal("19.00"))==0,"real HTTP admin filtered list stores old19");
            require(request("GET","/user/order/orderDetail/"+old.getId(),tokenA,null).path("amount").decimalValue().compareTo(new BigDecimal("19.00"))==0,"historical amount remains19 through user detail");
            require(call("GET","/admin/order/details/"+old.getId(),null,null).statusCode()==401,"anonymous admin details refused");
            require(call("GET","/admin/order/details/"+old.getId(),tokenA,null).statusCode()==401,"user scope admin details refused");
            require(request("GET","/admin/order/details/"+old.getId(),tokenAdmin,null).path("amount").decimalValue().compareTo(new BigDecimal("19.00"))==0,"real HTTP admin preserves historical19");
            require(billing.adminOrderDetail(old.getId()).getAmount().compareTo(new BigDecimal("19.00"))==0,"admin old amount stored19");
            require(billing.historyOrders(userA,1,100,null).getRecords().stream().anyMatch(o->o.getId().equals(old.getId())&&o.getAmount().compareTo(new BigDecimal("19.00"))==0),"history uses stored old amount");
            refused("GET","/user/order/orderDetail/"+old.getId(),tokenB,null,"订单不存在");
            reject(()->billing.orderDetail(userB,old.getId()),"direct two-user order ownership refusal");
            require(rows("orders","id",old.getId()).equals(oldRows)&&rows("order_detail","order_id",old.getId()).equals(oldDetails),"old order and historical detail image never recomputed or rewritten");
            require(rows("shopping_cart","user_id",userB).equals(otherCart)&&rows("address_book","user_id",userB).equals(otherAddress),"all billing scenarios preserve other user cart/address sentinel");
            group("REAL_TRANSACTION_FAILURE_RETRY_OLD_ORDERS_PASS");
            businessPassed=true;
        } catch (Exception | AssertionError failure) {
            failureClass=failure.getClass().getSimpleName(); throw failure;
        } finally {
            if(isolated) {
                for(String trigger:triggers) cleanup(()->{
                    drop(trigger);
                    require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",Integer.class,trigger)==0,"owned trigger absent");
                },"remove task-owned failure trigger");
                // Discover by the unique openid/name as well as IDs: even an interrupted successful INSERT is owned.
                var ownedUsers=jdbc.queryForList("SELECT id FROM user WHERE openid IN (?,?)",Long.class,openidA,openidB);
                for(long owner:ownedUsers) cleanup(()->{
                    var ownedOrders=jdbc.queryForList("SELECT id FROM orders WHERE user_id=? AND remark=?",Long.class,owner,RUN);
                    for(long id:ownedOrders) {
                        jdbc.update("DELETE FROM order_risk_result WHERE order_id=?",id);
                        jdbc.update("DELETE FROM order_risk_feature_snapshot WHERE order_id=?",id);
                        jdbc.update("DELETE FROM order_detail WHERE order_id=?",id);
                        jdbc.update("DELETE FROM orders WHERE id=? AND user_id=? AND remark=?",id,owner,RUN);
                        require(rows("order_detail","order_id",id).isEmpty()&&rows("orders","id",id).isEmpty(),"owned order and details absent");
                    }
                    jdbc.update("DELETE FROM shopping_cart WHERE user_id=?",owner);
                    jdbc.update("DELETE FROM address_book WHERE user_id=?",owner);
                    require(rows("shopping_cart","user_id",owner).isEmpty()&&rows("address_book","user_id",owner).isEmpty(),"owned cart/address cleanup without clearing original users");
                    String ownedContext=RedisKeys.aiRecommendContextKey(owner);
                    redis.delete(ownedContext); require(!Boolean.TRUE.equals(redis.hasKey(ownedContext)),"owned user context key absent");
                },"remove only synthetic user's owned dependent rows/keys");
                cleanup(()->{
                    jdbc.update("DELETE FROM user WHERE openid IN (?,?)",openidA,openidB);
                    require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE openid IN (?,?)",Integer.class,openidA,openidB)==0,"owned mock users absent");
                },"remove owned mock users");
                for(String table:List.of("setmeal","dish","category")) cleanup(()->{
                    for(String suffix:List.of("meal","dish","stop","cat")) jdbc.update("DELETE FROM "+table+" WHERE name=?",RUN+suffix);
                    // The saleable goods' edited names are also task-owned.
                    if(table.equals("dish")) jdbc.update("DELETE FROM dish WHERE name=?",RUN+"changed");
                    if(table.equals("setmeal")) jdbc.update("DELETE FROM setmeal WHERE name=?",RUN+"newmeal");
                },"remove owned "+table+" fixtures");
                cleanup(()->jdbc.update("DELETE FROM employee WHERE username=? AND name=?",adminName,RUN),"remove only uniquely named owned synthetic admin, preserve existing admin");
                for(String key:keys) cleanup(()->{
                    redis.delete(key); require(!Boolean.TRUE.equals(redis.hasKey(key)),"owned Redis key absent without flush");
                },"remove tracked owned Redis key");
                for(var entry:originals.entrySet()) cleanup(()->require(allRows(entry.getKey()).equals(entry.getValue()),"original "+entry.getKey()+" fixture rows unchanged"),"verify original "+entry.getKey()+" including sentinel rows");
                for(var entry:redisOriginals.entrySet()) cleanup(()->require(Arrays.equals(redis.dump(entry.getKey()),entry.getValue()),"original Redis fixture value unchanged"),"verify original Redis fixture");
                cleanup(()->require(Objects.equals(redis.keys("*"),redisOriginals.keySet()),"Redis key set restored, no untracked new keys"),"verify complete Redis key cleanup");
                context.close();
                cleanup(()-> {
                    try {
                        Path ownedFiles=out.resolve(RUN);
                        if(Files.exists(ownedFiles)) try(var paths=Files.walk(ownedFiles)) {
                            for(Path file:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                        }
                        require(!Files.exists(ownedFiles),"owned model files/directories absent");
                    } catch(java.io.IOException e) { throw new RuntimeException(e); }
                },"cleanup only unique run-owned model paths");
                cleanupPassed=cleanupFailures.isEmpty();
            }
            try {
                recordManifest(out);
                Files.writeString(out.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "run",RUN,"started",started,"finished",Instant.now().toString(),"assertions",assertions,
                        "businessPassed",businessPassed&&cleanupPassed,"cleanupPassed",cleanupPassed,"cleanupFailures",cleanupFailures,
                        "failureClass",failureClass,"mapSubstitute","probe-only deterministic 7 minutes, no external call")));
            } finally { context.close(); }
        }
        require(businessPassed&&cleanupPassed,"business and owned cleanup acceptance completed");
        group("REAL_SQL_SPRING_HTTP_REDIS_ROLLBACK_CLEANUP_PASS");
    }
    static Map<String,Object> submission(long address) {
        var b=new LinkedHashMap<String,Object>(); b.put("addressBookId",address); b.put("payMethod",1);
        b.put("deliveryStatus",1); b.put("tablewareStatus",0); b.put("remark",RUN); return b;
    }
    static com.codeying.dto.user.order.OrdersSubmitDTO dto(long address) {
        var b=new com.codeying.dto.user.order.OrdersSubmitDTO(); b.setAddressBookId(address); b.setPayMethod(1);
        b.setRemark(RUN); b.setDeliveryStatus(1); b.setTablewareStatus(0); return b;
    }
    static void assertOrder(org.springframework.context.ApplicationContext context,long id,BigDecimal response,String expected,int quantity) throws Exception {
        orders.add(id);
        var order=context.getBean(OrdersMapper.class).selectById(id);
        var details=rows("order_detail","order_id",id);
        BigDecimal goods=BigDecimal.ZERO; int count=0;
        for(var d:details) { int n=((Number)d.get("number")).intValue(); count+=n; goods=goods.add(((BigDecimal)d.get("amount")).multiply(BigDecimal.valueOf(n))); }
        require(order.getUserId()==userA&&response.compareTo(new BigDecimal(expected))==0&&order.getAmount().compareTo(response)==0&&order.getPackAmount()==quantity&&count==quantity&&goods.add(BigDecimal.valueOf(quantity+2L)).compareTo(response)==0,"authoritative response SQL details quantity/packing total="+expected);
        require(rows("shopping_cart","user_id",userA).isEmpty(),"success clears only owned cart");
        var user=request("GET","/user/order/orderDetail/"+id,tokenA,null);
        require(user.path("amount").decimalValue().compareTo(response)==0&&request("GET","/admin/order/details/"+id,tokenAdmin,null).path("amount").decimalValue().compareTo(response)==0,"new user/admin details agree with stored amount="+expected);
    }
    static long createDish(DishMapper mapper,long category,long admin,String suffix,int status) {
        var d=new Dish(); d.setName(RUN+suffix); d.setCategoryId(category); d.setStatus(status); d.setImage("dish-old.png");
        d.setPrice(new BigDecimal("12.50")); d.setCreateTime(new Date()); d.setUpdateTime(new Date()); d.setCreateUser(admin); d.setUpdateUser(admin);
        mapper.insert(d); track("dish",d.getId()); return d.getId();
    }
    static void recordManifest(Path out) throws Exception {
        Files.writeString(out.resolve("owned-resources.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "prefix",RUN,"userIds",users,"addressIds",addresses,"orderIds",orders,"triggers",triggers,"redisKeys",keys,"fixtureIds",fixtureIds,"cartIds",cartIds)));
    }
}
