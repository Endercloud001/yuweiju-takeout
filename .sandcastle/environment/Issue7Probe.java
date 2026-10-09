import com.codeying.App;
import com.codeying.dto.admin.employee.*;
import com.codeying.entity.Employee;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.EmployeeMapper;
import com.codeying.mapper.UserMapper;
import com.codeying.service.EmployeeApplicationService;
import com.codeying.service.UserService;
import com.codeying.properties.SkyProperties;
import com.codeying.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Issue #7 real Spring/MyBatis/MySQL/Redis/HTTP checks, isolated synthetic rows only. */
public class Issue7Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18087";
    static final String PASSWORD = "issue7-synthetic-only";
    static final String RUN = "i7_" + UUID.randomUUID().toString().substring(0,8);
    static final List<String> blacklistedJtis = new ArrayList<>();
    static void require(boolean condition, String assertion) {
        if (!condition) throw new AssertionError(assertion);
    }
    static HttpResponse<String> call(String method, String path, String header, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(10));
        if (header != null) builder.header(header, token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode result(HttpResponse<String> response, int code, String assertion) throws Exception {
        JsonNode node = JSON.readTree(response.body());
        require(node.path("code").asInt(-999) == code, assertion); return node;
    }
    static JsonNode login(String username, String password, int code) throws Exception {
        return result(call("POST","/admin/employee/login", null,null, Map.of("username",username,"password",password)), code, "admin login outcome");
    }
    static void reject(Runnable action, String assertion) {
        boolean rejected = false; try { action.run(); } catch (BusinessException expected) { rejected = true; }
        require(rejected, assertion);
    }
    static Employee employee(String suffix, String name, int status, Date updated) {
        Employee e = new Employee(); e.setUsername(RUN + suffix); e.setName(name); e.setPassword(PASSWORD);
        e.setPhone("00000000000"); e.setSex("1"); e.setIdNumber("synthetic"); e.setStatus(status);
        e.setCreateTime(updated); e.setUpdateTime(updated); e.setCreateUser(900001L); e.setUpdateUser(900001L); return e;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath(); boolean serve = args.length > 1 && args[1].equals("--serve");
        var app = new SpringApplication(App.class);
        var context = app.run("--spring.config.location=file:" + root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk", "--server.port=18087", "--server.address=127.0.0.1",
                "--logging.level.root=WARN", "--analysis.xgboost.model-dir=" + root.resolve(".scratch/issue7/analysis-models"),
                "--order-risk.model-dir=" + root.resolve(".scratch/issue7/order-risk-models"));
        var jdbc = context.getBean(JdbcTemplate.class); var redis = context.getBean(StringRedisTemplate.class);
        String codeA = "mock_" + RUN + "a", codeB = "mock_" + RUN + "b";
        String openidA = "mock_openid_" + codeA, openidB = "mock_openid_" + codeB;
        List<Long> addressIds = new ArrayList<>();
        boolean isolated = false;
        try {
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "isolated DB name");
            var properties = context.getBean(SkyProperties.class);
            try (var connection = jdbc.getDataSource().getConnection()) {
                require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"), "isolated database host");
            }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host")), "isolated Redis host");
            isolated = true;
            redis.opsForValue().set("afk:issue7:" + RUN,"isolated");
            require("isolated".equals(redis.opsForValue().get("afk:issue7:" + RUN)), "real Redis read/write");
            var mapper = context.getBean(EmployeeMapper.class); var employees = context.getBean(EmployeeApplicationService.class);
            Date old = new Date(1700000000000L), recent = new Date(1700100000000L);
            Employee a = employee("a",RUN + " Match",1,old), b = employee("b",RUN + " Match",1,recent), disabled = employee("c",RUN + " Match",0,recent);
            mapper.insert(a); mapper.insert(b); mapper.insert(disabled);
            require(mapper.findEnabledByCredentials(a.getUsername(),PASSWORD).getId().equals(a.getId()), "real SQL valid credentials");
            require(mapper.findEnabledByCredentials(a.getUsername(),"wrong") == null, "real SQL wrong credentials");
            require(mapper.findEnabledByCredentials(disabled.getUsername(),PASSWORD) == null, "real SQL disabled credentials");
            var q = new EmployeePageQuery(); q.setPage(1); q.setPageSize(10); q.setName(" " + RUN + " ");
            var page = employees.page(q); require(page.getTotal() == 3, "name trim filter");
            require(page.getRecords().stream().map(e -> e.getId()).toList().equals(List.of(disabled.getId(),b.getId(),a.getId())), "update time/id descending");
            q.setPage(5); require(employees.page(q).getRecords().isEmpty(), "empty out-of-range page");
            q.setPage(1); q.setName(RUN + "-absent"); require(employees.page(q).getTotal() == 0, "empty filter");
            q.setName("  "); require(employees.page(q).getTotal() >= 3, "blank no filter");
            require(mapper.countByUsernameExcludingId(a.getUsername(),null) == 1 && mapper.countByUsernameExcludingId(a.getUsername(),a.getId()) == 0, "username exclusion real SQL");
            require(mapper.countByUsernameExcludingId("' OR 1=1 --",null) == 0, "bound username query");
            reject(() -> employees.setStatus(null,0,a.getId()), "missing actor service refusal");
            reject(() -> employees.setStatus(a.getId(),2,b.getId()), "invalid status service refusal");
            var edit = new EditPasswordDTO(); edit.setEmpId(a.getId()); edit.setOldPassword(PASSWORD); edit.setNewPassword("issue7-new-synthetic");
            reject(() -> employees.editPassword(b.getId(),edit), "cross-employee password service refusal");
            edit.setOldPassword("wrong"); reject(() -> employees.editPassword(a.getId(),edit), "old password service refusal");
            login(a.getUsername(),"wrong",0); login(disabled.getUsername(),PASSWORD,0);
            var login = login(a.getUsername(),PASSWORD,1); String adminToken = login.path("data").path("token").asText();
            require(!adminToken.isBlank(), "real password token issuance");
            var missing = call("GET","/admin/employee/page?page=1&pageSize=1",null,null,null);
            require(missing.statusCode() == 401, "unauthenticated admin HTTP"); result(missing,0,"unauthenticated code");
            result(call("GET","/admin/employee/page?page=1&pageSize=1","token",adminToken,null),1,"authenticated employee HTTP");
            result(call("PUT","/admin/employee/editPassword","token",adminToken,Map.of("empId",b.getId(),"oldPassword",PASSWORD,"newPassword","synthetic")),0,"HTTP password ownership");
            employees.setStatus(a.getId(),0,b.getId()); login(b.getUsername(),PASSWORD,0);
            require(mapper.selectById(b.getId()).getUpdateUser().equals(a.getId()), "status audit actor real persistence");
            employees.setStatus(a.getId(),1,b.getId()); login(b.getUsername(),PASSWORD,1);
            employees.setStatus(a.getId(),0,a.getId()); login(a.getUsername(),PASSWORD,0);
            result(call("GET","/admin/employee/page?page=1&pageSize=1","token",adminToken,null),1,"existing token after disable preserved semantics");
            employees.setStatus(a.getId(),1,a.getId());
            JsonNode userA = result(call("POST","/user/user/login",null,null,Map.of("code",codeA)),1,"new user HTTP login").path("data");
            JsonNode again = result(call("POST","/user/user/login",null,null,Map.of("code",codeA)),1,"existing user HTTP login").path("data");
            JsonNode userB = result(call("POST","/user/user/login",null,null,Map.of("code",codeB)),1,"second user HTTP login").path("data");
            long uidA = userA.path("id").asLong(), uidB = userB.path("id").asLong();
            require(uidA == again.path("id").asLong() && uidA != uidB, "existing identity reuse and separate user IDs");
            var users = context.getBean(UserMapper.class);
            require(users.findByOpenid(openidA).getId() == uidA, "openid real SQL lookup");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE openid=?",Integer.class,openidA) == 1, "one sequential user creation");
            String userTokenA = userA.path("token").asText(), userTokenB = userB.path("token").asText();
            require(JwtUtil.parseClaims(userTokenA,properties.getJwt().getUserSecretKey()).get("uid",Long.class) == uidA, "issued user token identity");
            result(call("GET","/admin/employee/page?page=1&pageSize=1","token",userTokenA,null),0,"user token denied admin");
            result(call("GET","/user/addressBook/list","authentication",adminToken,null),0,"admin token denied user");
            result(call("GET","/user/addressBook/list",null,null,null),0,"unauthenticated user rejected");
            jdbc.update("INSERT INTO address_book (user_id,consignee,phone,sex,detail,is_default) VALUES (?,?,'00000000000','1','isolated',0)",uidA,RUN);
            addressIds.add(jdbc.queryForObject("SELECT id FROM address_book WHERE user_id=? AND consignee=?",Long.class,uidA,RUN));
            long addressId = addressIds.get(0);
            result(call("GET","/user/addressBook/" + addressId,"authentication",userTokenA,null),1,"owner reads own address");
            result(call("GET","/user/addressBook/" + addressId,"authentication",userTokenB,null),0,"other user cannot read address");
            var listB = result(call("GET","/user/addressBook/list","authentication",userTokenB,null),1,"user B own list");
            require(listB.path("data").isEmpty(), "other user list isolation");
            // Align the check with persisted MySQL DATETIME rather than assume wall-clock timezone conversion.
            Date created = users.findByOpenid(openidA).getCreateTime();
            Date begin = new Date(created.getTime() - 86400000), end = new Date(created.getTime() + 86400000);
            long intervalCount = users.countCreatedInRange(begin, end);
            require(intervalCount >= 2, "persisted user time interval real SQL");
            require(intervalCount == jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE create_time>=? AND create_time<=?",Long.class,begin,end), "named time query equals direct bound SQL");
            require(users.countCreatedThrough(end) == jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE create_time<=?",Long.class,end), "cumulative user real SQL equals direct bound SQL");
            var mappings = context.getBean("requestMappingHandlerMapping",RequestMappingHandlerMapping.class);
            Set<String> legacyPaths = new TreeSet<>(); mappings.getHandlerMethods().forEach((info, method) -> {
                if (method.getBeanType().getPackageName().equals("com.codeying.controller.admin.page")) legacyPaths.addAll(info.getPatternValues());
            });
            var servletContext = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context).getServletContext();
            var captchaRegistration = servletContext.getServletRegistrations().values().stream()
                    .filter(registration -> registration.getClassName().equals("com.codeying.servlet.CaptchaServlet")).findFirst();
            require(captchaRegistration.isPresent(), "captcha servlet registered at runtime");
            System.out.println("ISSUE7 CAPTCHA_SERVLET_MAPPINGS " + captchaRegistration.get().getMappings());
            require(legacyPaths.equals(Set.of("/","/hello","/login","/register","/logout","/admin/list","/admin/edit","/admin/detail","/admin/save","/admin/delete")), "exact legacy handlers enabled in Spring");
            for (String path : List.of("/admin/list","/admin/edit","/admin/detail","/admin/save","/admin/delete"))
                require(call("GET",path,null,null,null).statusCode() == 401,"legacy admin JWT covered");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tb_admin'",Integer.class) == 0, "fixture has no legacy tb_admin; SQL legacy unavailable");
            result(call("POST","/admin/employee/logout","token",adminToken,null),1,"admin logout");
            blacklistedJtis.add(JwtUtil.parseClaims(adminToken,properties.getJwt().getAdminSecretKey()).getId());
            result(call("GET","/admin/employee/page?page=1&pageSize=1","token",adminToken,null),0,"real Redis admin revocation");
            result(call("POST","/user/user/logout","authentication",userTokenA,null),1,"user logout");
            blacklistedJtis.add(JwtUtil.parseClaims(userTokenA,properties.getJwt().getUserSecretKey()).getId());
            result(call("GET","/user/addressBook/list","authentication",userTokenA,null),0,"real Redis user revocation");
            System.out.println("ISSUE7 REAL_SQL_SPRING_HTTP_REDIS_PASS");
            System.out.println("ISSUE7 LEGACY_HANDLER_AND_JWT_COVERAGE_PASS; TB_ADMIN_SQL_AND_TEMPLATE_RENDER_NOT_VERIFIED");
            if (serve) {
                Path credentials = root.resolve(".scratch/issue7/browser-fixture.json");
                java.nio.file.Files.writeString(credentials,JSON.writeValueAsString(Map.of("username",a.getUsername(),"password",PASSWORD)));
                System.out.println("ISSUE7 READY_FOR_BROWSER");
                while (!java.nio.file.Files.exists(root.resolve(".scratch/issue7/browser-done"))) Thread.sleep(250);
                java.nio.file.Files.deleteIfExists(credentials);
            }
        } finally {
            if (isolated) {
                for (Long id : addressIds) jdbc.update("DELETE FROM address_book WHERE id=? AND consignee=?",id,RUN);
                jdbc.update("DELETE FROM user WHERE openid IN (?,?)",openidA,openidB);
                jdbc.update("DELETE FROM employee WHERE username IN (?,?,?)",RUN+"a",RUN+"b",RUN+"c");
                redis.delete("afk:issue7:" + RUN);
                // blacklist key namespace is owned by TokenBlacklistService; remove only this probe's JTIs.
                for (String jti : blacklistedJtis) redis.delete(com.codeying.constant.RedisKeys.tokenBlacklistKey(jti));
            }
            context.close();
        }
    }
}
