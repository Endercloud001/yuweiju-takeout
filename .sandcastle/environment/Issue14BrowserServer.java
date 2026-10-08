import com.codeying.App;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Issue #14 browser fixture; connects only to the owned internal test services. */
public class Issue14BrowserServer {
    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(App.class);
        app.setRegisterShutdownHook(false);
        var context = app.run("--spring.config.location=file:/environment/application-afk.yml",
                "--spring.profiles.active=dev,afk", "--server.port=8080", "--logging.level.root=WARN");
        var jdbc = context.getBean(JdbcTemplate.class);
        if (!"sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) {
            context.close();
            throw new IllegalStateException("isolated database required");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id IN (914101,914102)", Integer.class) != 0) {
            context.close();
            throw new IllegalStateException("browser fixture IDs occupied");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                jdbc.update("DELETE FROM order_risk_result WHERE order_id IN (914101,914102)");
                jdbc.update("DELETE FROM orders WHERE id IN (914101,914102)");
                System.out.println("ISSUE14 BROWSER FIXTURE CLEANUP PASS");
            } finally {
                context.close();
            }
        }));
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,amount,phone,consignee,address) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    914100 + i, "ISSUE14-BROWSER-" + i, 5, 900001, 900001,
                    "2026-10-08 12:00:00", 66.60, "00000000001", "Fixture", "Isolation street");
        }
        jdbc.update("INSERT INTO order_risk_result(order_id,model_version,feature_version,risk_score,risk_level,reason_json,evaluated_at) VALUES(914102,'browser-fixture','v1',10,'LOW','{}','2026-10-08 12:00:00')");
        System.out.println("ISSUE14 READY_FOR_BROWSER");
        Thread.currentThread().join();
    }
}
