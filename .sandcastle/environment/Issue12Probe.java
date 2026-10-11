import com.codeying.App;
import com.codeying.properties.SkyProperties;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;

/** Repeatable task-only real HTTP/MySQL acceptance; all fixture writes are isolated. */
public class Issue12Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    static final long OWNER = 912101, OTHER = 912102;
    static final String A = "https://example.invalid/issue12/historical-A.png";
    static final String B = "https://example.invalid/issue12/current-B.png";
    static final String BROKEN = "https://example.invalid/issue12/broken.png";
    static final List<String> TABLES = List.of("orders", "order_detail", "dish", "setmeal", "user", "employee");
    static final List<String> TRIGGERS = List.of("issue12_detail_update_audit", "issue12_detail_insert_audit");
    static String base, admin, user, other;
    static void check(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
        System.out.println("ASSERT " + name + " PASS");
    }
    static JsonNode get(String path, String header, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(java.time.Duration.ofSeconds(15)).GET();
        if (token != null) request.header(header, token);
        var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        check("http_" + path.split("\\?")[0], response.statusCode() == 200 || response.statusCode() == 401);
        return JSON.readTree(response.body());
    }
    static JsonNode ok(String name, JsonNode response) {
        check(name + "_business_success", response.path("code").asInt() == 1);
        return response.path("data");
    }
    static void denied(String name, JsonNode response) { check(name, response.path("code").asInt() != 1); }
    static Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : TABLES) {
            String imageBytes = table.equals("order_detail") || table.equals("dish") || table.equals("setmeal") ? ", HEX(image) AS image_bytes" : "";
            result.put(table, jdbc.queryForList("SELECT *" + imageBytes + " FROM `" + table + "` ORDER BY id"));
        }
        return result;
    }
    static void stored(JdbcTemplate jdbc, String endpoint, JsonNode order) {
        long id = order.path("id").asLong();
        check(endpoint + "_amount19_" + id, order.path("amount").decimalValue().compareTo(new BigDecimal("19.00")) == 0);
        var expected = jdbc.queryForList("SELECT id,name,image,dish_id,setmeal_id,number,amount FROM order_detail WHERE order_id=? ORDER BY id", id);
        var details = order.path("orderDetailList");
        check(endpoint + "_detail_count_" + id, details.isArray() && details.size() == expected.size());
        for (int i = 0; i < expected.size(); i++) {
            var row = expected.get(i); var actual = details.get(i);
            Object image = row.get("image");
            check(endpoint + "_stored_image_" + row.get("id"),
                    actual.path("id").asLong() == ((Number)row.get("id")).longValue()
                    && (image == null ? actual.path("image").isNull() : image.equals(actual.path("image").asText()))
                    && !B.equals(actual.path("image").asText()));
            check(endpoint + "_stored_fields_" + row.get("id"),
                    Objects.equals(row.get("name"), actual.path("name").asText())
                    && actual.path("number").asInt() == ((Number)row.get("number")).intValue()
                    && actual.path("amount").decimalValue().compareTo((BigDecimal)row.get("amount")) == 0
                    && nullableId(actual.path("dishId"), row.get("dish_id"))
                    && nullableId(actual.path("setmealId"), row.get("setmeal_id")));
        }
    }
    static boolean nullableId(JsonNode actual, Object expected) {
        return expected == null ? actual.isNull() : actual.asLong() == ((Number)expected).longValue();
    }
    static void unchanged(JdbcTemplate jdbc, String name, Map<String, List<Map<String, Object>>> before) {
        check(name + "_all_rows_and_image_bytes_unchanged", before.equals(snapshot(jdbc)));
        check(name + "_zero_insert_update_attempts", jdbc.queryForObject("SELECT COUNT(*) FROM issue12_write_audit", Integer.class) == 0);
    }
    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(App.class);
        try (var context = app.run("--spring.config.location=file:" + Path.of(args[0]).toAbsolutePath(),
                "--spring.profiles.active=dev,afk", "--server.port=0", "--server.address=127.0.0.1", "--logging.level.root=WARN",
                "--spring.datasource.druid.url=jdbc:mysql://mysql:3306/sandcastle_fixture?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                "--spring.datasource.druid.username=root", "--spring.datasource.druid.password=",
                "--spring.data.redis.host=redis", "--spring.data.redis.port=6379", "--spring.data.redis.database=0")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            check("DATABASE_is_sandcastle_fixture", "sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)));
            // Inspect actual schema/keys before any fixture mutation; no schema text or business values logged.
            for (String table : TABLES) {
                var ddl = jdbc.queryForMap("SHOW CREATE TABLE `" + table + "`");
                String create = ddl.values().stream().map(Object::toString).filter(s -> s.startsWith("CREATE TABLE")).findFirst().orElseThrow();
                check("schema_" + table + "_primary_id", create.contains("PRIMARY KEY (`id`)") && create.contains("ENGINE=InnoDB"));
            }
            for (String table : List.of("orders", "order_detail", "dish", "setmeal", "user"))
                check("unused_ids_" + table, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "` WHERE id BETWEEN 912001 AND 912199", Integer.class) == 0);
            check("unused_detail_order_ids", jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id BETWEEN 912001 AND 912003", Integer.class) == 0);
            check("unused_task_trigger_names", jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME IN ('issue12_detail_update_audit','issue12_detail_insert_audit')", Integer.class) == 0);
            check("unused_task_audit_table", jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue12_write_audit'", Integer.class) == 0);
            var original = snapshot(jdbc);
            base = "http://127.0.0.1:" + ((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var jwt = context.getBean(SkyProperties.class).getJwt();
            admin = JwtUtil.createToken(jwt.getAdminSecretKey(), 600000, 900001L, "fixture", "admin");
            user = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, OWNER, "fixture", "user");
            other = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, OTHER, "fixture", "user");
            var createdTriggers = new ArrayList<String>(); boolean auditCreated = false;
            try {
                jdbc.update("INSERT INTO user(id,openid,name) VALUES(?,?,?),(?,?,?)", OWNER,"issue12-owner","fixture12",OTHER,"issue12-other","fixture12-other");
                jdbc.update("INSERT INTO dish(id,name,category_id,price,image) VALUES(912011,'issue12-dish',900001,99,?)", B);
                jdbc.update("INSERT INTO setmeal(id,name,category_id,price,image) VALUES(912012,'issue12-setmeal',900001,199,?)", B);
                for (int i = 1; i <= 3; i++)
                    jdbc.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,amount,phone) VALUES(?,?,?,?,?,?,?,?)",
                            912000+i, "ISSUE12-"+i, 5, i==3 ? OTHER : OWNER, 900001, "2020-10-0"+i+" 12:00:00", new BigDecimal("19.00"), "00000000012");
                String[] images = {A,A,null,""," \t ",BROKEN,A,null};
                for (int i = 0; i < images.length; i++)
                    jdbc.update("INSERT INTO order_detail(id,order_id,name,image,dish_id,setmeal_id,number,amount) VALUES(?,?,?,?,?,?,?,?)",
                            912021+i, 912001, "issue12-detail-"+i, images[i], i%2==0 ? (i==6 ? 912099L : 912011L) : null,
                            i%2==1 ? (i==7 ? 912098L : 912012L) : null, 1, new BigDecimal("2.00"));
                jdbc.update("INSERT INTO order_detail(id,order_id,name,image,dish_id,number,amount) VALUES(912029,912003,'issue12-other',?,912011,1,19)", A);
                check("two_distinct_fixture_owners", jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM orders WHERE id BETWEEN 912001 AND 912003", Integer.class) == 2);
                check("historicalA_catalogB_and_missing_products", jdbc.queryForObject("SELECT image FROM dish WHERE id=912011",String.class).equals(B)
                        && jdbc.queryForObject("SELECT image FROM setmeal WHERE id=912012",String.class).equals(B)
                        && jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE id=912099",Integer.class)==0
                        && jdbc.queryForObject("SELECT COUNT(*) FROM setmeal WHERE id=912098",Integer.class)==0);
                // MyISAM audit survives a swallowed exception or rollback. Only this task's detail rows are audited.
                jdbc.execute("CREATE TABLE issue12_write_audit(operation_name VARCHAR(10) NOT NULL) ENGINE=MyISAM"); auditCreated = true;
                for (String operation : List.of("UPDATE", "INSERT")) {
                    String name = "issue12_detail_" + operation.toLowerCase(Locale.ROOT) + "_audit";
                    String guard = operation.equals("UPDATE") ? "OLD.order_id BETWEEN 912001 AND 912003 OR NEW.order_id BETWEEN 912001 AND 912003" : "NEW.order_id BETWEEN 912001 AND 912003";
                    jdbc.execute("CREATE TRIGGER " + name + " AFTER " + operation + " ON order_detail FOR EACH ROW BEGIN IF " + guard + " THEN INSERT INTO issue12_write_audit VALUES ('" + operation + "'); END IF; END");
                    createdTriggers.add(name);
                }
                jdbc.update("UPDATE order_detail SET image=image WHERE id=912021");
                jdbc.update("INSERT INTO order_detail(id,order_id,name,image,number,amount) VALUES(912030,912002,'audit-self-test',NULL,1,1)");
                check("audit_detects_noop_update_and_insert", jdbc.queryForObject("SELECT COUNT(*) FROM issue12_write_audit",Integer.class)==2);
                jdbc.update("DELETE FROM order_detail WHERE id=912030"); jdbc.update("DELETE FROM issue12_write_audit");
                var before = snapshot(jdbc);
                String history = "/user/order/historyOrders?page=1&pageSize=10";
                var p = ok("user_history", get(history,"authentication",user));
                check("own_history_only_sort", p.path("total").asInt()==2 && p.path("records").size()==2
                        && p.path("records").get(0).path("id").asLong()==912002 && p.path("records").get(1).path("id").asLong()==912001);
                for (var o : p.path("records")) stored(jdbc,"user_history",o);
                unchanged(jdbc,"user_history",before);
                for (long id : List.of(912001L,912002L)) stored(jdbc,"user_detail",ok("user_detail",get("/user/order/orderDetail/"+id,"authentication",user)));
                unchanged(jdbc,"user_detail",before);
                p = ok("admin_page",get("/admin/order/conditionSearch?page=1&pageSize=10&number=ISSUE12","token",admin));
                check("admin_history_total_sort",p.path("total").asInt()==3 && p.path("records").size()==3 && p.path("records").get(0).path("id").asLong()==912003);
                for (var o : p.path("records")) stored(jdbc,"admin_page",o);
                unchanged(jdbc,"admin_page",before);
                for (long id : List.of(912001L,912002L,912003L)) stored(jdbc,"admin_detail",ok("admin_detail",get("/admin/order/details/"+id,"token",admin)));
                unchanged(jdbc,"admin_detail",before);
                for (String endpoint : List.of(history,"/user/order/orderDetail/912001")) denied("anonymous_user",get(endpoint,"authentication",null));
                for (String endpoint : List.of("/admin/order/conditionSearch?page=1&pageSize=1","/admin/order/details/912001")) denied("anonymous_admin",get(endpoint,"token",null));
                denied("other_user_detail_rejected",get("/user/order/orderDetail/912001","authentication",other));
                denied("own_user_other_detail_rejected",get("/user/order/orderDetail/912003","authentication",user));
                p = ok("other_history",get(history,"authentication",other));
                check("other_history_only",p.path("total").asInt()==1 && p.path("records").get(0).path("id").asLong()==912003);
                p = ok("user_paging",get("/user/order/historyOrders?page=2&pageSize=1","authentication",user));
                check("user_page_two",p.path("total").asInt()==2 && p.path("records").size()==1 && p.path("records").get(0).path("id").asLong()==912001);
                stored(jdbc,"user_paging",p.path("records").get(0));
                p = ok("user_empty",get("/user/order/historyOrders?page=9&pageSize=1","authentication",user));
                check("user_empty_page",p.path("total").asInt()==2 && p.path("records").isEmpty());
                p = ok("admin_paging",get("/admin/order/conditionSearch?page=2&pageSize=1&number=ISSUE12","token",admin));
                check("admin_page_two",p.path("total").asInt()==3 && p.path("records").size()==1 && p.path("records").get(0).path("id").asLong()==912002);
                stored(jdbc,"admin_paging",p.path("records").get(0));
                p = ok("admin_empty",get("/admin/order/conditionSearch?page=9&pageSize=1&number=ISSUE12","token",admin));
                check("admin_empty_page",p.path("total").asInt()==3 && p.path("records").isEmpty());
                unchanged(jdbc,"all_auth_paging_queries",before);
            } finally {
                for (String name : createdTriggers) jdbc.execute("DROP TRIGGER " + name);
                if (auditCreated) jdbc.execute("DROP TABLE issue12_write_audit");
                jdbc.update("DELETE FROM order_detail WHERE id BETWEEN 912021 AND 912030 AND order_id BETWEEN 912001 AND 912003");
                jdbc.update("DELETE FROM orders WHERE id BETWEEN 912001 AND 912003");
                jdbc.update("DELETE FROM dish WHERE id=912011"); jdbc.update("DELETE FROM setmeal WHERE id=912012");
                jdbc.update("DELETE FROM user WHERE id IN (?,?)",OWNER,OTHER);
                check("cleanup_shared_rows_exactly_unchanged",original.equals(snapshot(jdbc)));
                check("cleanup_task_triggers",jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME IN ('issue12_detail_update_audit','issue12_detail_insert_audit')",Integer.class)==0);
                check("cleanup_task_audit_table",jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='issue12_write_audit'",Integer.class)==0);
            }
            System.out.println("ISSUE12 ACCEPTANCE PASS");
        }
    }
}
