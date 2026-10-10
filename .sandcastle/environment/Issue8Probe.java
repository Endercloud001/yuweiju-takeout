import com.codeying.App;
import com.codeying.dto.admin.dish.DishDTO;
import com.codeying.dto.admin.dish.DishFlavorDTO;
import com.codeying.dto.admin.setmeal.SetmealDTO;
import com.codeying.dto.admin.setmeal.SetmealDishDTO;
import com.codeying.entity.Employee;
import com.codeying.mapper.EmployeeMapper;
import com.codeying.service.DishApplicationService;
import com.codeying.service.SetmealApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/** Real isolated catalog acceptance. No original-data scripts or external business API calls. */
public class Issue8Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18087";
    static final String RUN = "i8_" + UUID.randomUUID().toString().replace("-", "").substring(0,10);
    static final String USERNAME = "sandbox_admin", PASSWORD = "sandbox-only-login";
    static final List<String> categoryNames = new ArrayList<>(), dishNames = new ArrayList<>(), setmealNames = new ArrayList<>();
    static final List<String> assertions = new ArrayList<>();
    static JdbcTemplate jdbc;
    static String admin, user;
    static void require(boolean ok, String assertion) {
        if (!ok) throw new AssertionError(assertion);
        assertions.add(assertion);
        System.out.println("ISSUE8 PASS " + assertion);
    }
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static HttpResponse<String> call(String method, String path, String header, String token, Object body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
        if (header != null) b.header(header, token);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode result(HttpResponse<String> response, int code, String assertion) throws Exception {
        var n = JSON.readTree(response.body());
        require(n.path("code").asInt(-999) == code, assertion); return n.path("data");
    }
    static JsonNode a(String method, String path, Object body) throws Exception {
        return result(call(method, path, "token", admin, body), 1, "admin " + method + " " + path.split("\\?")[0]);
    }
    static JsonNode u(String path) throws Exception {
        return result(call("GET", path, "authentication", user, null), 1, "user GET " + path.split("\\?")[0]);
    }
    static List<Long> ids(JsonNode array) {
        var ids = new ArrayList<Long>(); array.forEach(n -> ids.add(n.path("id").asLong())); return ids;
    }
    static long category(String suffix, int type, int sort, int status) throws Exception {
        String name = RUN + suffix; categoryNames.add(name);
        a("POST", "/admin/category", Map.of("name", name, "type", type, "sort", sort, "status", status));
        return jdbc.queryForObject("SELECT id FROM category WHERE name=?", Long.class, name);
    }
    static long dish(String suffix, long category, int status) throws Exception {
        String name = RUN + suffix; dishNames.add(name);
        a("POST", "/admin/dish", Map.of("name", name, "categoryId", category, "price", 12.50, "status", status,
                "image", "", "description", "isolated catalog", "flavors", List.of(Map.of("name", "spice", "value", "[\"mild\",\"hot\"]"))));
        return jdbc.queryForObject("SELECT id FROM dish WHERE name=?", Long.class, name);
    }
    static long setmeal(String suffix, long category, int status, long dish) throws Exception {
        String name = RUN + suffix; setmealNames.add(name);
        a("POST", "/admin/setmeal", Map.of("name", name, "categoryId", category, "price", 22.50, "status", status,
                "setmealDishes", List.of(Map.of("dishId", dish, "copies", 2))));
        return jdbc.queryForObject("SELECT id FROM setmeal WHERE name=?", Long.class, name);
    }
    static List<Map<String,Object>> rows(String table, String key, long id) {
        // Identifiers come exclusively from constants at call sites, never request input.
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE " + key + "=? ORDER BY id", id);
    }
    static void failed(Runnable action, String marker, String assertion) {
        Throwable failure = null;
        try { action.run(); } catch (RuntimeException expected) { failure = expected; }
        boolean causalMarker = false;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(marker)) causalMarker = true;
        }
        require(failure != null && causalMarker, assertion);
    }
    static void refusal(String path, String text) throws Exception {
        var response = call("DELETE", path, "token", admin, null);
        var node = JSON.readTree(response.body());
        require(node.path("code").asInt(-1) == 0 && node.path("message").asText().contains(text), "business refusal: " + text);
    }
    static void cleanupParents(String table, List<String> names, String child, String key) {
        for (String name : names) {
            var owned = jdbc.queryForList("SELECT id FROM " + table + " WHERE name=?", Long.class, name);
            for (long id : owned) {
                if (child != null) jdbc.update("DELETE FROM " + child + " WHERE " + key + "=?", id);
                jdbc.update("DELETE FROM " + table + " WHERE id=? AND name=?", id, name);
            }
            require(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE name=?", Integer.class, name) == 0,
                    "cleanup removed owned " + table + " row " + name);
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(), out = root.resolve(".scratch/issue8");
        boolean serve = args.length > 1 && args[1].equals("--serve");
        var context = new SpringApplication(App.class).run(
                "--spring.config.location=file:" + root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk", "--server.port=18087", "--server.address=127.0.0.1",
                "--logging.level.root=OFF", "--analysis.task.enabled=false", "--order-risk.task.scoring-enabled=false",
                "--analysis.xgboost.model-dir=" + out.resolve("analysis-models"), "--order-risk.model-dir=" + out.resolve("order-risk-models"));
        jdbc = context.getBean(JdbcTemplate.class);
        var redis = context.getBean(StringRedisTemplate.class);
        boolean isolated = false, adminOwned = false;
        Long adminId = null, sentinel = null;
        String code = "mock_" + RUN, openid = "mock_openid_" + code;
        String flavorTrigger = RUN + "_flavor", detailTrigger = RUN + "_detail", redisKey = "afk:issue8:" + RUN;
        Map<String, List<Map<String,Object>>> preexisting = new LinkedHashMap<>();
        List<Map<String,Object>> sentinelBefore = null, sentinelFlavorsBefore = null;
        boolean passed = false, cleanupPassed = false;
        try {
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "isolated database name");
            try (var connection = jdbc.getDataSource().getConnection()) {
                require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"), "isolated MySQL hostname and URL");
            }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host"))
                    && "0".equals(context.getEnvironment().getProperty("spring.data.redis.database")), "isolated Redis hostname/database");
            isolated = true;
            for (String table : List.of("category", "dish", "dish_flavor", "setmeal", "setmeal_dish"))
                preexisting.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
            redis.opsForValue().set(redisKey, "isolated");
            require("isolated".equals(redis.opsForValue().get(redisKey)), "real Redis owned key roundtrip");
            var em = context.getBean(EmployeeMapper.class);
            var existing = jdbc.queryForList("SELECT id FROM employee WHERE username=?", Long.class, USERNAME);
            if (existing.isEmpty()) {
                var e = new Employee(); e.setUsername(USERNAME); e.setPassword(PASSWORD); e.setName(RUN);
                e.setPhone("00000000000"); e.setSex("1"); e.setIdNumber("synthetic"); e.setStatus(1);
                e.setCreateTime(new Date()); e.setUpdateTime(new Date()); e.setCreateUser(900001L); e.setUpdateUser(900001L);
                em.insert(e); adminId = e.getId(); adminOwned = true;
            } else {
                require(em.findEnabledByCredentials(USERNAME, PASSWORD) != null, "preexisting synthetic admin compatible without editing it");
                adminId = existing.get(0);
            }
            admin = result(call("POST", "/admin/employee/login", null, null, Map.of("username", USERNAME, "password", PASSWORD)), 1,
                    "synthetic admin normal password HTTP login").path("token").asText();
            user = result(call("POST", "/user/user/login", null, null, Map.of("code", code)), 1,
                    "local dev mock user HTTP login (not real WeChat)").path("token").asText();
            for (String path : List.of("/admin/category/page?page=1&pageSize=10", "/admin/dish/page?page=1&pageSize=10", "/admin/setmeal/page?page=1&pageSize=10")) {
                var noAuth = call("GET", path, null, null, null);
                require(noAuth.statusCode() == 401, "missing authentication denied " + path.split("\\?")[0]);
                require(call("GET", path, "token", user, null).statusCode() == 401, "wrong user token denied " + path.split("\\?")[0]);
            }
            // Current WebMvcConfiguration deliberately excludes catalog reads from JWT interception.
            // Exercise that compatibility and the adjacent protected user boundary; never add auth rules here.
            for (String path : List.of("/user/category/list", "/user/dish/list?categoryId=0", "/user/setmeal/list?categoryId=0", "/user/setmeal/dish/0")) {
                var anonymous = result(call("GET",path,null,null,null),1,"current public catalog anonymous access: "+path.split("\\?")[0]);
                var wrongHeader = result(call("GET",path,"authentication",admin,null),1,"public catalog ignores token scope: "+path.split("\\?")[0]);
                require(anonymous.equals(wrongHeader),"public catalog results unchanged by irrelevant token");
            }
            require(call("GET","/user/addressBook/list",null,null,null).statusCode()==401,"protected user boundary denies missing authentication");
            require(call("GET","/user/addressBook/list","authentication",admin,null).statusCode()==401,"protected user boundary denies wrong admin token");
            result(call("GET","/user/addressBook/list","authentication",user,null),1,"protected user accepts authenticated local mock identity");
            long c1 = category("c1", 1, 5, 1), c2 = category("c2", 1, 5, 1), c0 = category("c0", 1, 1, 0), cs = category("cs", 2, 0, 1);
            jdbc.update("UPDATE category SET update_time='2026-01-01 00:00:00' WHERE id IN (?,?,?)",c1,c2,c0);
            var cp = a("GET", "/admin/category/page?page=1&pageSize=1&type=1&name=" + enc(" " + RUN + " "), null);
            require(cp.path("total").asInt() == 3 && ids(cp.path("records")).equals(List.of(c0)), "category combined name/type, trim, sort and page total");
            require(ids(a("GET", "/admin/category/page?page=2&pageSize=1&type=1&name=" + enc(RUN), null).path("records")).equals(List.of(c2)), "category second page sort/update-time/id tie-break");
            var ownedCategories = ids(u("/user/category/list")).stream().filter(id -> List.of(c1,c2,c0,cs).contains(id)).toList();
            require(ownedCategories.equals(List.of(c2,c1,cs)), "user category enabled only, type/sort/id order");
            require(ids(u("/user/category/list?type=2")).contains(cs) && !ids(u("/user/category/list?type=2")).contains(c1), "user category type selection");
            require(ids(a("GET", "/admin/category/list?type=1", null)).contains(c0), "admin category includes disabled");
            a("POST", "/admin/category/status/0?id=" + c2, null);
            require(!ids(u("/user/category/list")).contains(c2), "admin category disable reflected in user list");
            a("POST", "/admin/category/status/1?id=" + c2, null);
            a("PUT", "/admin/category", Map.of("id",c1,"name",RUN+"c1","type",1,"sort",10));
            require(jdbc.queryForObject("SELECT sort FROM category WHERE id=?", Integer.class,c1) == 10, "category admin edit persists");
            System.out.println("ISSUE8 GROUP CATEGORY_PASS");
            long d1 = dish("d1", c1, 1), d2 = dish("d2", c1, 1), d0 = dish("d0", c1, 0);
            sentinel = dish("sentinel", c2, 0); sentinelBefore = rows("dish", "id", sentinel); sentinelFlavorsBefore = rows("dish_flavor", "dish_id", sentinel);
            // Equal timestamps make id the deliberate tie-breaker, without changing any preexisting rows.
            jdbc.update("UPDATE dish SET update_time='2026-01-01 00:00:00' WHERE id IN (?,?,?)", d1,d2,d0);
            var dp = a("GET", "/admin/dish/page?page=1&pageSize=1&categoryId="+c1+"&status=1&name="+enc(" "+RUN+" "), null);
            require(dp.path("total").asInt()==2 && ids(dp.path("records")).equals(List.of(d2)), "dish combined filters, trim, pagination and id tie-break");
            require(ids(a("GET", "/admin/dish/page?page=2&pageSize=1&categoryId="+c1+"&status=1&name="+enc(RUN), null).path("records")).equals(List.of(d1)), "dish second page");
            require(ids(a("GET", "/admin/dish/list?categoryId="+c1, null)).equals(List.of(d0,d2,d1)), "admin dish list includes stopped and maintains order");
            var ud = u("/user/dish/list?categoryId="+c1);
            require(ids(ud).equals(List.of(d2,d1)) && ud.get(0).path("flavors").get(0).path("value").asText().contains("hot"), "admin dish write -> sellable user list and flavor roundtrip");
            require(a("GET", "/admin/dish/"+d1, null).path("flavors").get(0).path("name").asText().equals("spice"), "admin flavor detail roundtrip");
            a("POST", "/admin/dish/status/0?id="+d2, null);
            require(ids(u("/user/dish/list?categoryId="+c1)).equals(List.of(d1)), "admin dish stop immediately removes user selection");
            a("POST", "/admin/dish/status/1?id="+d2, null);
            var changedDish = new LinkedHashMap<String,Object>(); changedDish.put("id",d1); changedDish.put("name",RUN+"d1"); changedDish.put("price",13.75);
            changedDish.put("flavors",List.of(Map.of("name","salt","value","[\"low\"]")));
            a("PUT", "/admin/dish", changedDish);
            require(a("GET", "/admin/dish/"+d1,null).path("flavors").get(0).path("name").asText().equals("salt"), "dish update replaces former flavors");
            require(u("/user/dish/list?categoryId="+c1).toString().contains("salt"), "updated flavor available to user");
            refusal("/admin/category?id="+c1, "存在菜品");
            System.out.println("ISSUE8 GROUP DISH_FLAVOR_PASS");
            long s1 = setmeal("s1", cs, 1, d1), s2 = setmeal("s2", cs, 1, d2), s0 = setmeal("s0", cs, 0, d1);
            jdbc.update("UPDATE setmeal SET update_time='2026-01-01 00:00:00' WHERE id IN (?,?,?)",s1,s2,s0);
            var sp = a("GET", "/admin/setmeal/page?page=1&pageSize=1&categoryId="+cs+"&status=1&name="+enc(" "+RUN+" "), null);
            require(sp.path("total").asInt()==2 && ids(sp.path("records")).equals(List.of(s2)), "setmeal combined filters, trim, pagination and order");
            require(ids(a("GET", "/admin/setmeal/page?page=2&pageSize=1&categoryId="+cs+"&status=1&name="+enc(RUN),null).path("records")).equals(List.of(s1)), "setmeal second page");
            require(ids(u("/user/setmeal/list?categoryId="+cs)).equals(List.of(s2,s1)), "admin setmeal write -> sellable user list");
            var sd = u("/user/setmeal/dish/"+s1);
            require(sd.size()==1 && sd.get(0).path("copies").asInt()==2 && sd.get(0).path("name").asText().equals(RUN+"d1"), "setmeal user detail dish/copies roundtrip");
            require(a("GET", "/admin/setmeal/"+s1,null).path("setmealDishes").get(0).path("dishId").asLong()==d1, "setmeal admin association roundtrip");
            a("PUT", "/admin/setmeal",Map.of("id",s1,"price",25,"setmealDishes",List.of(Map.of("dishId",d2,"copies",3))));
            require(u("/user/setmeal/dish/"+s1).get(0).path("name").asText().equals(RUN+"d2") && u("/user/setmeal/dish/"+s1).get(0).path("copies").asInt()==3, "setmeal update replaces former detail for user");
            a("POST", "/admin/setmeal/status/0?id="+s2,null);
            require(ids(u("/user/setmeal/list?categoryId="+cs)).equals(List.of(s1)), "stopped setmeal excluded from user selection");
            a("POST", "/admin/setmeal/status/1?id="+s2,null);
            refusal("/admin/category?id="+cs,"存在套餐");
            long emptyCategory = category("empty",1,100,1);
            a("DELETE","/admin/category?id="+emptyCategory,null);
            require(rows("category","id",emptyCategory).isEmpty(),"unassociated category deletion succeeds");
            var d0Before = rows("dish","id",d0); var s0Before = rows("setmeal","id",s0);
            var s0DetailsBefore = rows("setmeal_dish","setmeal_id",s0);
            refusal("/admin/dish?ids="+d0+","+d1,"关联套餐");
            require(rows("dish","id",d0).equals(d0Before) && !rows("dish","id",d1).isEmpty(), "mixed associated/free dish batch refused atomically");
            refusal("/admin/setmeal?ids="+s0+","+s1,"正在售卖");
            require(rows("setmeal","id",s0).equals(s0Before) && rows("setmeal_dish","setmeal_id",s0).equals(s0DetailsBefore) && !rows("setmeal","id",s1).isEmpty(), "mixed stopped/selling setmeal batch preserves both roots and associations");
            System.out.println("ISSUE8 GROUP SETMEAL_DETAILS_PASS");
            for (String group : List.of("category","dish","setmeal")) {
                String extra = group.equals("category") ? "&type=1" : "&categoryId="+(group.equals("dish")?c1:cs)+"&status=1";
                for (String input : List.of(RUN+"-absent", "' OR 1=1 --")) {
                    var empty = a("GET","/admin/"+group+"/page?page=1&pageSize=10"+extra+"&name="+enc(input),null);
                    require(empty.path("total").asInt()==0 && empty.path("records").isEmpty(), group+" empty/bound suspicious filter: "+input);
                }
                require(a("GET","/admin/"+group+"/page?page=99&pageSize=10&name="+enc(RUN),null).path("records").isEmpty(),group+" out-of-range empty page");
            }
            require(u("/user/dish/list?categoryId=0").isEmpty() && u("/user/setmeal/list?categoryId=0").isEmpty() && u("/user/setmeal/dish/0").isEmpty(), "empty user directory and missing setmeal detail");
            var da = context.getBean(DishApplicationService.class); var sa = context.getBean(SetmealApplicationService.class);
            require(AopUtils.isAopProxy(da) && AopUtils.isAopProxy(sa), "dish/setmeal use cases called through Spring transaction proxies");
            var dishBefore = rows("dish","id",d1); var flavorBefore = rows("dish_flavor","dish_id",d1);
            var mealBefore = rows("setmeal","id",s1); var detailBefore = rows("setmeal_dish","setmeal_id",s1);
            // Task-owned identifier and numeric ID only; conditional trigger affects one owned product.
            jdbc.execute("CREATE TRIGGER "+flavorTrigger+" BEFORE INSERT ON dish_flavor FOR EACH ROW BEGIN IF NEW.dish_id="+d1+" AND NEW.name='i8_fail_second' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue8_second_flavor_failure'; END IF; END");
            var change = new DishDTO(); change.setId(d1); change.setName(RUN+"rollback"); change.setPrice(new BigDecimal("99.99"));
            var f1 = new DishFlavorDTO(); f1.setName("first_insert"); f1.setValue("[]");
            var f2 = new DishFlavorDTO(); f2.setName("i8_fail_second"); f2.setValue("[]"); change.setFlavors(List.of(f1,f2));
            long actor = adminId;
            failed(() -> da.update(actor,change),"issue8_second_flavor_failure","second flavor insert fails with expected MySQL trigger cause");
            require(rows("dish","id",d1).equals(dishBefore) && rows("dish_flavor","dish_id",d1).equals(flavorBefore),"failed second flavor insert rolls back root edit, first insert and restores former flavor IDs/values");
            require(rows("dish","id",sentinel).equals(sentinelBefore),"independent product unchanged after dish rollback");
            jdbc.execute("DROP TRIGGER "+flavorTrigger);
            jdbc.execute("CREATE TRIGGER "+detailTrigger+" BEFORE INSERT ON setmeal_dish FOR EACH ROW BEGIN IF NEW.setmeal_id="+s1+" AND NEW.copies=999 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='issue8_detail_failure'; END IF; END");
            var meal = new SetmealDTO(); meal.setId(s1); meal.setName(RUN+"mealrollback"); meal.setPrice(new BigDecimal("99.99"));
            var item1 = new SetmealDishDTO(); item1.setDishId(d1); item1.setCopies(1);
            var item2 = new SetmealDishDTO(); item2.setDishId(d2); item2.setCopies(999); meal.setSetmealDishes(List.of(item1,item2));
            failed(() -> sa.update(actor,meal),"issue8_detail_failure","setmeal detail insert fails with expected MySQL trigger cause");
            require(rows("setmeal","id",s1).equals(mealBefore) && rows("setmeal_dish","setmeal_id",s1).equals(detailBefore),"failed setmeal detail rolls back root edit, partial detail and restores former association IDs/copies");
            require(rows("dish","id",sentinel).equals(sentinelBefore),"independent product unchanged after setmeal rollback");
            jdbc.execute("DROP TRIGGER "+detailTrigger);
            require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN (?,?)",Integer.class,flavorTrigger,detailTrigger)==0,"task failure triggers removed");
            System.out.println("ISSUE8 GROUP REAL_PROXY_ROLLBACK_PASS");
            // Successful cleanup paths are business checks too; never delete the sentinel until verified.
            a("DELETE","/admin/setmeal?ids="+s0+",0,invalid",null);
            require(rows("setmeal","id",s0).isEmpty() && rows("setmeal_dish","setmeal_id",s0).isEmpty(),"stopped setmeal deletion removes its details");
            a("DELETE","/admin/dish?ids="+d0+",0,invalid",null);
            require(rows("dish","id",d0).isEmpty() && rows("dish_flavor","dish_id",d0).isEmpty(),"unassociated dish deletion removes flavors");
            if (serve) {
                Files.writeString(out.resolve("browser-fixture.json"), JSON.writeValueAsString(Map.of("prefix",RUN,"category",RUN+"c1","dish",RUN+"d1","setmeal",RUN+"s1")));
                System.out.println("ISSUE8 READY_FOR_BROWSER");
                long until = System.currentTimeMillis()+180000;
                while (!Files.exists(out.resolve("browser-done")) && System.currentTimeMillis()<until) Thread.sleep(250);
                require(Files.exists(out.resolve("browser-done")),"browser completion received within bounded wait");
            }
            passed = true;
        } finally {
            try {
                if (isolated) {
                    jdbc.execute("DROP TRIGGER IF EXISTS "+flavorTrigger); jdbc.execute("DROP TRIGGER IF EXISTS "+detailTrigger);
                    require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN (?,?)",Integer.class,flavorTrigger,detailTrigger)==0,"finally confirms no owned triggers remain");
                    if (sentinel != null && sentinelBefore != null) require(rows("dish","id",sentinel).equals(sentinelBefore) && rows("dish_flavor","dish_id",sentinel).equals(sentinelFlavorsBefore),"sentinel root and flavors preserved before owned cleanup");
                    cleanupParents("setmeal",setmealNames,"setmeal_dish","setmeal_id");
                    cleanupParents("dish",dishNames,"dish_flavor","dish_id");
                    cleanupParents("category",categoryNames,null,null);
                    jdbc.update("DELETE FROM user WHERE openid=?",openid);
                    require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE openid=?",Integer.class,openid)==0,"owned local mock user removed");
                    if (adminOwned) {
                        jdbc.update("DELETE FROM employee WHERE id=? AND username=? AND name=?",adminId,USERNAME,RUN);
                        require(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE id=?",Integer.class,adminId)==0,"owned synthetic admin removed");
                    }
                    redis.delete(redisKey); require(!Boolean.TRUE.equals(redis.hasKey(redisKey)),"owned Redis key removed, no flush");
                    for (var entry : preexisting.entrySet()) require(jdbc.queryForList("SELECT * FROM "+entry.getKey()+" ORDER BY id").equals(entry.getValue()),"preexisting "+entry.getKey()+" rows unchanged after cleanup");
                    Files.deleteIfExists(out.resolve("browser-fixture.json"));
                    cleanupPassed = true;
                }
                Files.writeString(out.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("run",RUN,"assertions",assertions,"businessPassed",passed && cleanupPassed,"cleanupPassed",cleanupPassed)));
            } finally { context.close(); }
        }
        require(passed,"real isolated business acceptance completed");
        System.out.println("ISSUE8 REAL_SQL_SPRING_HTTP_REDIS_ROLLBACK_PASS");
    }
}
