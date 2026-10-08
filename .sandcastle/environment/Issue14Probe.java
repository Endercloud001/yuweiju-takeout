import com.codeying.App;
import com.codeying.properties.SkyProperties;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;

/** Task-only acceptance probe. Never load personal configuration or train a model. */
public class Issue14Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    static String base, admin, user, other;
    static void check(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
        System.out.println("ASSERT " + name + " PASS");
    }
    static JsonNode get(String path, String header, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(java.time.Duration.ofSeconds(15)).GET();
        if (token != null) b.header(header, token);
        var response = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body());
    }
    static JsonNode admin(String path) throws Exception { return get(path, "token", admin); }
    static JsonNode page(String extra) throws Exception {
        var response = admin("/admin/order/conditionSearch?page=1&pageSize=20&number=ISSUE14" + extra);
        check("page_success_" + extra, response.path("code").asInt() == 1);
        return response.path("data");
    }
    static long id(JsonNode page, int i) { return page.path("records").get(i).path("id").asLong(); }
    static void unavailable(String name, JsonNode vo) {
        check(name, "UNAVAILABLE".equals(vo.path("riskLevel").asText())
                && vo.path("riskScore").isNull() && vo.path("modelVersion").isNull()
                && !vo.path("riskReasons").asText().isBlank());
    }
    static void failed(String name, JsonNode response) { check(name, response.path("code").asInt() != 1); }
    public static void main(String[] args) throws Exception {
        String config = Path.of(args[0]).toAbsolutePath().toString();
        var app = new SpringApplication(App.class);
        try (var context = app.run("--spring.config.location=file:" + config,
                "--spring.profiles.active=dev,afk", "--server.port=0", "--logging.level.root=WARN",
                "--spring.datasource.druid.url=jdbc:mysql://mysql:3306/sandcastle_fixture?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                "--spring.datasource.druid.username=root", "--spring.datasource.druid.password=",
                "--spring.data.redis.host=redis", "--spring.data.redis.port=6379", "--spring.data.redis.database=0")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            check("internal_database", "sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)));
            base = "http://127.0.0.1:" + ((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var jwt = context.getBean(SkyProperties.class).getJwt();
            admin = JwtUtil.createToken(jwt.getAdminSecretKey(), 600000, 900001L, "fixture", "admin");
            user = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, 900001L, "fixture", "user");
            other = JwtUtil.createToken(jwt.getUserSecretKey(), 600000, 900002L, "fixture", "user");
            check("fixture_ids_unoccupied", jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id BETWEEN 914001 AND 914005", Integer.class) == 0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_result WHERE order_id BETWEEN 914001 AND 914005", Integer.class) == 0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE id=914001 OR order_id BETWEEN 914001 AND 914005", Integer.class) == 0);
            boolean riskRenamed = false, coreRenamed = false, detailRenamed = false;
            try {
                for (int i=1; i<=5; i++) {
                    jdbc.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,amount,phone) VALUES(?,?,?,?,?,?,?,?)",
                            914000+i, "ISSUE14-"+i, i==3 ? 6 : 5, 900001, 900001,
                            i<=2 ? "2026-10-01 12:00:00" : "2026-09-30 12:00:00", 66.60, "00000000001");
                }
                jdbc.update("INSERT INTO order_detail(id,order_id,name,image,number,amount) VALUES(914001,914001,'fixture','historical.png',2,33.30)");
                // Order 1 changed from HIGH to LOW; order 2 has latest-time ties.
                risk(jdbc,914001,"old",95,"HIGH","2026-09-29 12:00:00");
                risk(jdbc,914001,"new",10,"LOW","2026-10-01 12:00:00");
                risk(jdbc,914002,"a",10,"HIGH","2026-10-01 12:00:00");
                risk(jdbc,914002,"z",90,"LOW","2026-10-01 12:00:00");
                risk(jdbc,914003,"one",80,"HIGH","2026-10-01 12:00:00");
                risk(jdbc,914004,"one",40,"MEDIUM","2026-10-01 12:00:00");
                var p = page("");
                check("total_sort_tied_order_time", p.path("total").asLong()==5 && id(p,0)==914002 && id(p,1)==914001 && id(p,2)==914005);
                unavailable("missing_risk_page",p.path("records").get(2));
                p = page("&status=6"); check("state",p.path("total").asLong()==1 && id(p,0)==914003);
                p = page("&riskLevel=HIGH"); check("latest_correlation",p.path("total").asLong()==2 && id(p,0)==914002 && id(p,1)==914003);
                p = page("&minRiskScore=70"); check("score",p.path("total").asLong()==2);
                p = page("&riskLevel=HIGH&minRiskScore=70&status=5");
                check("combined_predicates_latest_ties",p.path("total").asLong()==1 && id(p,0)==914002);
                p = page("&beginTime=2026-10-01%2012:00:00&endTime=2026-10-01%2012:00:00");
                check("inclusive_time",p.path("total").asLong()==2);
                p = admin("/admin/order/conditionSearch?page=2&pageSize=1&number=ISSUE14").path("data");
                check("total_page",p.path("total").asLong()==5 && id(p,0)==914001);
                p = admin("/admin/order/conditionSearch?page=9&pageSize=1&number=ISSUE14").path("data");
                check("empty_page_total",p.path("total").asLong()==5 && p.path("records").isEmpty());
                p = page("&status=99"); check("no_records",p.path("total").asLong()==0 && p.path("records").isEmpty());
                p = page("&riskLevel=HIGH%27%20OR%201=1%20--"); check("bound_risk_input",p.path("total").asLong()==0);
                p = admin("/admin/order/conditionSearch?page=1&pageSize=20&number=%20ISSUE14%20&phone=%2000001%20").path("data");
                check("trimmed_substrings",p.path("total").asLong()==5);
                var detail = admin("/admin/order/details/914002");
                check("display_latest_model_tie",detail.path("code").asInt()==1 && "z".equals(detail.path("data").path("modelVersion").asText()) && "LOW".equals(detail.path("data").path("riskLevel").asText()));
                unavailable("missing_risk_detail",admin("/admin/order/details/914005").path("data"));
                failed("unauthenticated_page",get("/admin/order/conditionSearch?page=1&pageSize=1","token",null));
                failed("unauthenticated_detail",get("/admin/order/details/914001","token",null));
                failed("ownership",get("/user/order/orderDetail/914001","authentication",other));
                failed("missing_detail",admin("/admin/order/details/914099"));
                jdbc.execute("RENAME TABLE order_risk_result TO issue14_risk_hold"); riskRenamed=true;
                p = page(""); check("optional_page_core_retained",p.path("total").asLong()==5);
                for (var vo : p.path("records")) unavailable("optional_page_risk_"+vo.path("id"),vo);
                detail = admin("/admin/order/details/914001");
                check("optional_detail_core_retained",detail.path("code").asInt()==1 && detail.path("data").path("amount").decimalValue().compareTo(new java.math.BigDecimal("66.60"))==0);
                unavailable("optional_detail_risk",detail.path("data"));
                failed("risk_filter_failure_no_unfiltered_retry",admin("/admin/order/conditionSearch?page=1&pageSize=20&number=ISSUE14&riskLevel=HIGH"));
                failed("score_filter_failure",admin("/admin/order/conditionSearch?page=1&pageSize=20&number=ISSUE14&minRiskScore=70"));
                var u=get("/user/order/orderDetail/914001","authentication",user);
                check("user_detail_regression",u.path("code").asInt()==1 && u.path("data").path("amount").decimalValue().compareTo(new java.math.BigDecimal("66.60"))==0
                        && "historical.png".equals(u.path("data").path("orderDetailList").get(0).path("image").asText()));
                jdbc.execute("RENAME TABLE issue14_risk_hold TO order_risk_result"); riskRenamed=false;
                jdbc.execute("RENAME TABLE order_detail TO issue14_detail_hold"); detailRenamed=true;
                failed("core_detail_assembly_page",admin("/admin/order/conditionSearch?page=1&pageSize=20&number=ISSUE14"));
                failed("core_detail_assembly_detail",admin("/admin/order/details/914001"));
                jdbc.execute("RENAME TABLE issue14_detail_hold TO order_detail"); detailRenamed=false;
                jdbc.execute("RENAME TABLE orders TO issue14_orders_hold"); coreRenamed=true;
                failed("core_sql_page",admin("/admin/order/conditionSearch?page=1&pageSize=20&number=ISSUE14"));
                failed("core_sql_detail",admin("/admin/order/details/914001"));
                failed("user_core_sql",get("/user/order/orderDetail/914001","authentication",user));
            } finally {
                if (detailRenamed) jdbc.execute("RENAME TABLE issue14_detail_hold TO order_detail");
                if (coreRenamed) jdbc.execute("RENAME TABLE issue14_orders_hold TO orders");
                if (riskRenamed) jdbc.execute("RENAME TABLE issue14_risk_hold TO order_risk_result");
                jdbc.update("DELETE FROM order_risk_result WHERE order_id BETWEEN 914001 AND 914005");
                jdbc.update("DELETE FROM order_detail WHERE id=914001");
                jdbc.update("DELETE FROM orders WHERE id BETWEEN 914001 AND 914005");
                System.out.println("ASSERT fixture_cleanup PASS");
            }
            System.out.println("ISSUE14 ACCEPTANCE PASS");
        }
    }
    static void risk(JdbcTemplate jdbc,long order,String version,int score,String level,String time) {
        jdbc.update("INSERT INTO order_risk_result(order_id,model_version,feature_version,risk_score,risk_level,reason_json,evaluated_at) VALUES(?,?,'v1',?,?,?,?)",
                order,version,score,level,"{\"mode\":\"fixture\"}",time);
    }
}
