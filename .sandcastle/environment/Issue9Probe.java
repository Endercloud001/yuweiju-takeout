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
public class Issue9Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18089";
    static final String RUN = "i9_" + UUID.randomUUID().toString().replace("-", "").substring(0,10);
    static final List<String> assertions = new ArrayList<>(), cleanupFailures = new ArrayList<>();
    static final List<String> triggers = new ArrayList<>(), keys = new ArrayList<>();
    static final List<Long> users = new ArrayList<>(), addresses = new ArrayList<>(), orders = new ArrayList<>();
    static final List<Long> cartIds = new ArrayList<>();
    static final Map<String,List<Long>> fixtureIds = new LinkedHashMap<>();
    static void track(String table, long id) { fixtureIds.computeIfAbsent(table, ignored -> new ArrayList<>()).add(id); }
    static final AtomicInteger mapCalls = new AtomicInteger();
    static JdbcTemplate jdbc;
    static String tokenA, tokenB;
    static long userA, userB;
    static void require(boolean condition, String assertion) {
        if (!condition) throw new AssertionError(assertion);
        assertions.add(assertion);
        System.out.println("ISSUE9 PASS " + assertion);
    }
    static void group(String name) { System.out.println("ISSUE9 GROUP " + name + " " + Instant.now()); }

    @Configuration(proxyBeanMethods = false)
    public static class ProbeConfig {
        /** Probe-only deterministic map substitute: no real geocoding, weather, OSS, model or payment call. */
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
        if (token != null) b.header("authentication", token);
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
        Path root = Path.of(args[0]).toAbsolutePath(); Path out=root.resolve(".scratch/issue9");
        Files.createDirectories(out);
        String started=Instant.now().toString();
        var context=new SpringApplication(App.class, ProbeConfig.class).run(
                "--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk","--server.address=127.0.0.1","--server.port=18089","--logging.level.root=OFF",
                "--analysis.task.enabled=false","--order-risk.task.scoring-enabled=false",
                "--analysis.xgboost.model-dir="+out.resolve("analysis-models"),"--order-risk.model-dir="+out.resolve("order-risk-models"));
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
            String redisKey="afk:issue9:"+RUN; keys.add(redisKey);
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
            var loginA=request("POST","/user/user/login",null,Map.of("code",codeA));
            tokenA=loginA.path("token").asText();
            userA=jdbc.queryForObject("SELECT id FROM user WHERE openid=?",Long.class,openidA); users.add(userA);
            var loginB=request("POST","/user/user/login",null,Map.of("code",codeB));
            tokenB=loginB.path("token").asText();
            userB=jdbc.queryForObject("SELECT id FROM user WHERE openid=?",Long.class,openidB); users.add(userB);
            require(userA!=userB,"two distinct dev mock synthetic users (not real WeChat)");
            require(AopUtils.isAopProxy(cart)&&AopUtils.isAopProxy(book),"cart/address real Spring transaction proxies");
            recordManifest(out);
            var boundary=List.of(new Object[]{"POST","/user/shoppingCart/add",Map.of("dishId",0)},
                    new Object[]{"POST","/user/shoppingCart/sub",Map.of("dishId",0)},
                    new Object[]{"GET","/user/shoppingCart/list",null},new Object[]{"DELETE","/user/shoppingCart/clean",null},
                    new Object[]{"POST","/user/addressBook",addressBody("unauthorized",0)},
                    new Object[]{"PUT","/user/addressBook",addressBody("unauthorized",0)},
                    new Object[]{"DELETE","/user/addressBook?id=0",null},new Object[]{"GET","/user/addressBook/list",null},
                    new Object[]{"GET","/user/addressBook/default",null},new Object[]{"GET","/user/addressBook/0",null},
                    new Object[]{"PUT","/user/addressBook/default",Map.of("id",0)});
            for(var entry:boundary) {
                String method=(String)entry[0],path=(String)entry[1];
                require(call(method,path,null,entry[2]).statusCode()==401,"anonymous refused "+method+" "+path);
                require(call(method,path,adminToken,entry[2]).statusCode()==401,"wrong admin scope refused "+method+" "+path);
            }
            require(request("GET","/user/shoppingCart/list",tokenA,null).isEmpty(),"empty cart list");
            request("DELETE","/user/shoppingCart/clean",tokenA,null);
            require(request("GET","/user/addressBook/list",tokenA,null).isEmpty(),"empty address list");
            require(request("GET","/user/addressBook/default",tokenA,null).isNull(),"empty default is null");
            group("AUTH_EMPTY_PASS");

            var c=new Category(); c.setName(RUN+"cat"); c.setType(1); c.setSort(0); c.setStatus(1);
            c.setCreateTime(new Date()); c.setUpdateTime(new Date()); c.setCreateUser(adminId); c.setUpdateUser(adminId);
            context.getBean(CategoryMapper.class).insert(c); category=c.getId(); track("category",category);
            good=createDish(context.getBean(DishMapper.class),category,adminId,"dish",1);
            stopped=createDish(context.getBean(DishMapper.class),category,adminId,"stop",0);
            var s=new Setmeal(); s.setName(RUN+"meal"); s.setCategoryId(category); s.setStatus(1);
            s.setPrice(new BigDecimal("22.50")); s.setImage("meal-old.png"); s.setCreateTime(new Date()); s.setUpdateTime(new Date());
            s.setCreateUser(adminId); s.setUpdateUser(adminId); context.getBean(SetmealMapper.class).insert(s); setmeal=s.getId(); track("setmeal",setmeal);
            long dishId=good,stoppedId=stopped,mealId=setmeal;
            var foreignCart=dish(dishId,null); cart.addItem(userB,foreignCart);
            var cartB=rows("shopping_cart","user_id",userB);
            cartIds.add(((Number)cartB.get(0).get("id")).longValue());
            reject(()->cart.addItem(null,dish(dishId,null)),"proxy add requires identity without HTTP");
            reject(()->cart.listForUser(null),"proxy list requires identity without HTTP");
            reject(()->cart.clearForUser(null),"proxy clean requires identity without HTTP");
            var both=dish(dishId,null); both.setSetmealId(mealId);
            reject(()->cart.addItem(userA,both),"proxy add refuses both kinds");
            reject(()->cart.subtractItem(userA,both),"proxy subtract refuses both kinds");
            reject(()->cart.addItem(userA,new ShoppingCartChangeDTO()),"proxy add refuses missing kind");
            reject(()->cart.addItem(userA,dish(stoppedId,null)),"proxy stopped dish add refusal");
            reject(()->cart.addItem(userA,dish(0,null)),"proxy missing goods add refusal");
            refused("POST","/user/shoppingCart/add",tokenA,Map.of(),"必须且只能");
            refused("POST","/user/shoppingCart/add",tokenA,Map.of("dishId",dishId,"setmealId",mealId),"必须且只能");
            refused("POST","/user/shoppingCart/sub",tokenA,Map.of("dishId",dishId),"购物车中无此商品");
            require(rows("shopping_cart","user_id",userB).equals(cartB),"other user subtraction never mutates owner cart");
            // Unknown quantity/userId fields have no adjustable-quantity or identity authority in the existing DTO.
            request("POST","/user/shoppingCart/add",tokenA,Map.of("dishId",dishId,"dishFlavor","  ","number",-9,"userId",userB));
            long plain=cart.listForUser(userA).get(0).getId(); cartIds.add(plain);
            var first=cartMapper.selectById(plain);
            require(first.getNumber()==1&&first.getDishFlavor()==null&&first.getUserId()==userA,"one-step add ignores unknown quantity/owner, blank flavor normalized null");
            var snapshot=rows("shopping_cart","id",plain);
            jdbc.update("UPDATE dish SET name=?,image=?,price=? WHERE id=?",RUN+"changed","dish-new.png",new BigDecimal("99.00"),dishId);
            request("POST","/user/shoppingCart/add",tokenA,Map.of("dishId",dishId));
            var quantity=cartMapper.selectById(plain);
            require(quantity.getNumber()==2&&quantity.getName().equals(first.getName())&&quantity.getImage().equals(first.getImage())
                    &&quantity.getAmount().equals(first.getAmount()),"existing add preserves original name/image/price snapshot");
            request("POST","/user/shoppingCart/sub",tokenA,Map.of("dishId",dishId,"dishFlavor",""));
            require(rows("shopping_cart","id",plain).equals(snapshot),"one-step subtract restores complete original cart row");
            jdbc.update("UPDATE shopping_cart SET dish_flavor='' WHERE id=? AND user_id=?",plain,userA);
            cart.addItem(userA,dish(dishId," "));
            require(cart.listForUser(userA).size()==1&&cartMapper.selectById(plain).getNumber()==2,"stored empty and request whitespace/null flavor share identity");
            cart.subtractItem(userA,dish(dishId,null));
            request("POST","/user/shoppingCart/add",tokenA,Map.of("dishId",dishId,"dishFlavor"," hot "));
            cart.addItem(userA,dish(dishId,"hot"));
            require(cart.listForUser(userA).size()==2&&cart.listForUser(userA).get(1).getNumber()==2
                    &&"hot".equals(cart.listForUser(userA).get(1).getDishFlavor()),"flavored item trimmed and distinct from plain item");
            cartIds.add(cart.listForUser(userA).get(1).getId());
            request("POST","/user/shoppingCart/add",tokenA,Map.of("setmealId",mealId));
            long mealCart=cart.listForUser(userA).get(2).getId(); cartIds.add(mealCart); var mealFirst=cartMapper.selectById(mealCart);
            jdbc.update("UPDATE setmeal SET name=?,image=?,price=? WHERE id=?",RUN+"newmeal","meal-new.png",new BigDecimal("88.00"),mealId);
            cart.addItem(userA,meal(mealId));
            require(cartMapper.selectById(mealCart).getNumber()==2&&cartMapper.selectById(mealCart).getAmount().equals(mealFirst.getAmount())
                    &&cartMapper.selectById(mealCart).getName().equals(mealFirst.getName())&&cartMapper.selectById(mealCart).getImage().equals(mealFirst.getImage()),"setmeal quantity preserves stored snapshot");
            jdbc.update("UPDATE dish SET status=0 WHERE id=?",dishId);
            var beforeStopped=rows("shopping_cart","user_id",userA);
            refused("POST","/user/shoppingCart/add",tokenA,Map.of("dishId",dishId),"商品不可售");
            require(rows("shopping_cart","user_id",userA).equals(beforeStopped),"existing stopped item add refused without quantity mutation");
            cart.subtractItem(userA,dish(dishId,"hot")); // Sub does not recheck saleability, preserving removal of stopped items.
            cart.subtractItem(userA,dish(dishId,"hot"));
            require(cart.listForUser(userA).stream().noneMatch(item->"hot".equals(item.getDishFlavor())),"subtract stopped flavored item at one deletes it");
            jdbc.update("UPDATE dish SET status=1 WHERE id=?",dishId);
            jdbc.update("UPDATE setmeal SET status=0 WHERE id=?",mealId);
            reject(()->cart.addItem(userA,meal(mealId)),"stopped existing setmeal refuses add");
            jdbc.update("UPDATE setmeal SET status=1 WHERE id=?",mealId);
            require(cartMapper.updateQuantityOwned(userA,((Number)cartB.get(0).get("id")).longValue(),7)==0
                    &&cartMapper.deleteOwned(userA,((Number)cartB.get(0).get("id")).longValue())==0,"bound conditional cart writes cannot mutate another user ID");
            request("DELETE","/user/shoppingCart/clean",tokenA,null);
            require(cart.listForUser(userA).isEmpty()&&rows("shopping_cart","user_id",userB).equals(cartB),"scoped cleaning leaves other user entire snapshot unchanged");
            // Preserve existing persisted null/negative quantity handling, do not invent a new input API.
            cart.addItem(userA,dish(dishId,null)); long legacy=cart.listForUser(userA).get(0).getId(); cartIds.add(legacy);
            jdbc.update("UPDATE shopping_cart SET number=-2 WHERE id=?",legacy);
            cart.addItem(userA,dish(dishId,null)); require(cartMapper.selectById(legacy).getNumber()==-1,"legacy negative number add preserves existing plus-one behavior");
            cart.subtractItem(userA,dish(dishId,null)); require(cart.listForUser(userA).isEmpty(),"legacy nonpositive number subtraction deletes owned row");
            // Current MySQL number column is NOT NULL; the retained defensive null fallback is unit-tested only.
            cart.clearForUser(userA);
            recordManifest(out); group("CART_INVARIANTS_PASS");

            long b1=createAddress(tokenB,userB,"b1",0); var bookB=rows("address_book","user_id",userB);
            long a1=createAddress(tokenA,userA,"a1",0);
            require(book.defaultForUser(userA).getId()==a1,"first address forced default despite request zero");
            require("00000000000".equals(book.findOwned(userA,a1).getPhone())&&book.findOwned(userA,a1).getDetail().equals(RUN+"a1 door"),"phone/detail trimming preserved");
            long a2=createAddress(tokenA,userA,"a2",0),a3=createAddress(tokenA,userA,"a3",1);
            require(ids(request("GET","/user/addressBook/list",tokenA,null)).equals(List.of(a3,a2,a1)),"address default-first then descending ID ordering");
            require(request("GET","/user/addressBook/default",tokenA,null).path("id").asLong()==a3,"explicit default creation clears previous default");
            var edit=new LinkedHashMap<String,Object>(addressBody("edit",0)); edit.put("id",a3);
            request("PUT","/user/addressBook",tokenA,edit);
            require(book.findOwned(userA,a3).getIsDefault()==1,"updating default address ignores requested zero flag");
            var nondefaultEdit=new LinkedHashMap<String,Object>(addressBody("edit2",1)); nondefaultEdit.put("id",a2);
            request("PUT","/user/addressBook",tokenA,nondefaultEdit);
            require(book.findOwned(userA,a2).getIsDefault()==0&&book.defaultForUser(userA).getId()==a3,"updating nondefault address ignores requested one flag");
            require(request("GET","/user/addressBook/"+a2,tokenA,null).path("detail").asText().equals(RUN+"edit2 door"),"updated owner detail HTTP roundtrip");
            for(long foreign:List.of(b1,0L,-1L)) {
                var badEdit=new LinkedHashMap<String,Object>(addressBody("foreign",1)); badEdit.put("id",foreign);
                refused("GET","/user/addressBook/"+foreign,tokenA,null,"地址不存在");
                refused("PUT","/user/addressBook",tokenA,badEdit,"地址不存在");
                refused("DELETE","/user/addressBook?id="+foreign,tokenA,null,"地址不存在");
                refused("PUT","/user/addressBook/default",tokenA,Map.of("id",foreign),"地址不存在");
                reject(()->book.findOwned(userA,foreign),"proxy detail refuses foreign/missing ID");
                reject(()->book.deleteForUser(userA,foreign),"proxy deletion refuses foreign/missing ID");
                reject(()->book.setDefaultForUser(userA,foreign),"proxy default refuses foreign/missing ID");
                var dto=addressDTO("foreign",1); dto.setId(foreign);
                reject(()->book.updateForUser(userA,dto),"proxy update refuses foreign/missing ID");
            }
            reject(()->book.createForUser(null,addressDTO("bad",1)),"proxy address create requires identity");
            reject(()->book.defaultForUser(null),"proxy address default requires identity");
            var blank=addressDTO("bad",1); blank.setPhone(" ");
            reject(()->book.createForUser(userA,blank),"proxy create validates phone independently of DTO HTTP");
            blank.setPhone("00000000000"); blank.setDetail(null);
            reject(()->book.createForUser(userA,blank),"proxy create validates detail independently of DTO HTTP");
            refused("PUT","/user/addressBook",tokenA,addressBody("noid",1),"参数错误");
            refused("POST","/user/addressBook",tokenA,Map.of("phone"," ","detail","door"),"手机号不能为空");
            require(rows("address_book","user_id",userB).equals(bookB),"all address ownership refusals preserve other user rows");
            request("PUT","/user/addressBook/default",tokenA,Map.of("id",a1));
            require(book.defaultForUser(userA).getId()==a1&&book.listForUser(userA).stream().filter(a->a.getIsDefault()==1).count()==1,"HTTP default switch leaves exactly selected owned default");
            recordManifest(out); group("ADDRESS_RULES_OWNERSHIP_PASS");

            var addressBefore=rows("address_book","user_id",userA);
            String switchTrigger=RUN+"_switch";
            trigger(switchTrigger,"UPDATE","address_book","NEW.user_id="+userA+" AND NEW.id="+a2+" AND NEW.is_default=1 AND (SELECT is_default FROM address_book WHERE id="+a1+")=0","i9_switch_fail");
            failed(()->book.setDefaultForUser(userA,a2),"i9_switch_fail","real proxy default target UPDATE fails after clearing former default");
            require(rows("address_book","user_id",userA).equals(addressBefore)&&rows("address_book","user_id",userB).equals(bookB),"switch failure rolls back old default and all rows, other user unchanged");
            drop(switchTrigger);
            String createTrigger=RUN+"_create";
            trigger(createTrigger,"UPDATE","address_book","NEW.user_id="+userA+" AND NEW.id="+a1+" AND NEW.is_default=0 AND (SELECT COUNT(*) FROM address_book WHERE user_id="+userA+" AND is_default=1)>1","i9_create_fail");
            failed(()->book.createForUser(userA,addressDTO("rollbackcreate",1)),"i9_create_fail","real proxy default creation clear UPDATE fails after address INSERT");
            require(rows("address_book","user_id",userA).equals(addressBefore)&&rows("address_book","user_id",userB).equals(bookB),"creation failure removes inserted row and preserves former default and other user");
            drop(createTrigger);
            String deleteTrigger=RUN+"_delete";
            trigger(deleteTrigger,"UPDATE","address_book","NEW.user_id="+userA+" AND NEW.id="+a3+" AND NEW.is_default=1 AND (SELECT COUNT(*) FROM address_book WHERE id="+a1+")=0","i9_delete_fail");
            failed(()->book.deleteForUser(userA,a1),"i9_delete_fail","real proxy replacement UPDATE fails after deleting former default");
            require(rows("address_book","user_id",userA).equals(addressBefore)&&rows("address_book","user_id",userB).equals(bookB),"replacement failure restores deleted address exact ID/default and other user");
            drop(deleteTrigger);
            cart.addItem(userA,dish(dishId,null)); long cartId=cart.listForUser(userA).get(0).getId(); cartIds.add(cartId);
            var cartBefore=rows("shopping_cart","user_id",userA);
            String updateTrigger=RUN+"_cartupdate";
            trigger(updateTrigger,"UPDATE","shopping_cart","NEW.user_id="+userA+" AND NEW.id="+cartId,"i9_cart_update_fail");
            failed(()->cart.addItem(userA,dish(dishId,null)),"i9_cart_update_fail","real proxy cart increment SQL failure propagates original cause");
            require(rows("shopping_cart","user_id",userA).equals(cartBefore)&&rows("shopping_cart","user_id",userB).equals(cartB),"failed cart increment preserves quantity/row/snapshot and other user");
            drop(updateTrigger);
            String insertTrigger=RUN+"_cartinsert";
            trigger(insertTrigger,"INSERT","shopping_cart","NEW.user_id="+userA,"i9_cart_insert_fail");
            failed(()->cart.addItem(userA,dish(dishId,"new")),"i9_cart_insert_fail","real proxy cart insert SQL failure propagates original cause");
            require(rows("shopping_cart","user_id",userA).equals(cartBefore)&&rows("shopping_cart","user_id",userB).equals(cartB),"failed cart insert leaves rows/snapshots unchanged and no partial new item");
            drop(insertTrigger);
            String contextKey=RedisKeys.aiRecommendContextKey(userA); keys.add(contextKey);
            require(!Boolean.TRUE.equals(redis.hasKey(contextKey)),"new synthetic user has no existing recommendation context");
            redis.opsForList().rightPush(contextKey,"wrong-type-owned-statistics-fixture");
            cart.addItem(userA,dish(dishId,null));
            require(cartMapper.selectById(cartId).getNumber()==2,"existing statistics Redis GET failure isolated while core cart succeeds");
            require(redis.opsForList().size(contextKey)==1,"SQL transaction does not roll back task-owned Redis wrong-type fixture");
            recordManifest(out); group("REAL_PROXY_MYSQL_FAILURE_ROLLBACK_PASS");

            request("DELETE","/user/addressBook?id="+a1,tokenA,null);
            require(book.defaultForUser(userA).getId()==a3,"default deletion selects latest remaining ID, not list order");
            request("DELETE","/user/addressBook?id="+a2,tokenA,null);
            require(book.defaultForUser(userA).getId()==a3,"nondefault deletion preserves selected default");
            var checkoutBefore=cart.listForUser(userA); var checkoutAddress=book.findOwned(userA,a3);
            refused("POST","/user/order/submit",tokenA,Map.of("addressBookId",b1,"packAmount",0),"地址不存在");
            require(cart.listForUser(userA).equals(checkoutBefore),"wrong-address checkout refusal preserves prepared cart");
            var submitted=request("POST","/user/order/submit",tokenA,Map.of("addressBookId",a3,"packAmount",0,"payMethod",1,"deliveryStatus",1,"remark",RUN));
            long orderId=submitted.path("id").asLong(); orders.add(orderId); recordManifest(out);
            var order=context.getBean(OrdersMapper.class).selectById(orderId);
            require(order.getUserId()==userA&&order.getAddressBookId()==a3&&order.getPhone().equals(checkoutAddress.getPhone())
                    &&order.getConsignee().equals(checkoutAddress.getConsignee())&&order.getAddress().endsWith(checkoutAddress.getDetail()),"existing checkout consumes owned address and stores address snapshot");
            var detail=jdbc.queryForList("SELECT * FROM order_detail WHERE order_id=?",orderId);
            require(detail.size()==1&&Objects.equals(detail.get(0).get("dish_flavor"),checkoutBefore.get(0).getDishFlavor())
                    &&Objects.equals(detail.get(0).get("dish_id"),checkoutBefore.get(0).getDishId())
                    &&Objects.equals(detail.get(0).get("setmeal_id"),checkoutBefore.get(0).getSetmealId())
                    &&detail.get(0).get("name").equals(checkoutBefore.get(0).getName())
                    &&detail.get(0).get("image").equals(checkoutBefore.get(0).getImage())
                    &&((Number)detail.get(0).get("number")).intValue()==2
                    &&((BigDecimal)detail.get(0).get("amount")).compareTo(checkoutBefore.get(0).getAmount())==0,"existing checkout preserves prepared cart goods/flavor/price/image/quantity snapshots in SQL");
            require(order.getAmount().compareTo(checkoutBefore.get(0).getAmount().multiply(BigDecimal.valueOf(2)))==0
                    &&order.getPackAmount()==0,"preserved current checkout amount with zero requested packing, no new billing");
            require(mapCalls.get()==1&&cart.listForUser(userA).isEmpty()&&rows("shopping_cart","user_id",userB).equals(cartB),"checkout clears only submitting user cart and uses no external map call");
            var checkoutEdit=new LinkedHashMap<String,Object>(addressBody("after-order",0)); checkoutEdit.put("id",a3);
            request("PUT","/user/addressBook",tokenA,checkoutEdit);
            require(context.getBean(OrdersMapper.class).selectById(orderId).equals(order),"address edit leaves persisted historical order address snapshot unchanged");
            request("DELETE","/user/addressBook?id="+a3,tokenA,null);
            require(book.listForUser(userA).isEmpty()&&book.defaultForUser(userA)==null,"last address deletion leaves empty list and no default");
            require(rows("address_book","user_id",userB).equals(bookB),"successful address operations and checkout preserve other user sentinel");
            refused("POST","/user/order/submit",tokenA,Map.of("addressBookId",a3),"购物车为空");
            group("DEFAULT_REPLACEMENT_CHECKOUT_PASS");
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
