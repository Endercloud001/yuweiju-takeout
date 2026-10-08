import com.codeying.App;
import com.codeying.entity.AddressBook;
import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.service.AddressBookService;
import com.codeying.service.OrdersApplicationService;
import com.codeying.service.OrderRiskService;
import com.codeying.utils.BaiduMapUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import ml.dmlc.xgboost4j.java.*;
import smile.clustering.KMeans;
import smile.classification.LogisticRegression;

public class EnvironmentProbe {
    @Configuration
    public static class LocalMap {
        @Bean @Primary
        public BaiduMapUtil isolationMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop, String user, String key) { return 5; }
            };
        }
    }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(App.class, LocalMap.class);
        var context = application.run("--spring.config.location=file:/environment/application-afk.yml",
                "--spring.profiles.active=dev,afk", "--logging.level.root=WARN");
        boolean serve = args.length > 0 && args[0].equals("--serve");
        try {
            var jdbc = context.getBean(JdbcTemplate.class);
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)), "isolated database");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)==24,"24 schema tables");
            var redis=context.getBean(StringRedisTemplate.class);
            redis.opsForValue().set("afk:environment-probe", "isolated");
            require("isolated".equals(redis.opsForValue().get("afk:environment-probe")),"redis write/read");
            redis.delete("afk:environment-probe");
            var service=context.getBean(AddressBookService.class);
            require(AopUtils.isAopProxy(service), "address service uses real Spring proxy");
            long initial=service.count();
            var first=new AddressBook();first.setUserId(900002L);first.setPhone("00000000002");first.setIsDefault(0);
            var invalid=new AddressBook();invalid.setUserId(900002L);invalid.setIsDefault(0);
            boolean failed=false;
            try { service.saveBatch(List.of(first,invalid),1); } catch(RuntimeException expected) {failed=true;}
            require(failed && service.count()==initial,"real MySQL NOT NULL failure rolls back earlier batch insert");
            var orders=context.getBean(OrdersApplicationService.class);
            require(AopUtils.isAopProxy(orders), "actual order use case proxy");
            int before=jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class);
            int carts=jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=900001",Integer.class);
            jdbc.execute("CREATE TRIGGER afk_reject_detail BEFORE INSERT ON order_detail FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='afk-core-write-failure'");
            try {
                var dto=new OrdersSubmitDTO();dto.setAddressBookId(900001L);dto.setPayMethod(1);dto.setPackAmount(0);
                failed=false;
                try {orders.submit(900001L,dto);} catch(RuntimeException expected) {failed=true;}
                require(failed,"core detail insert injection reached");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)==before,"order insert rolled back");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM order_detail",Integer.class)==0,"detail insert rolled back");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id=900001",Integer.class)==carts,"cart retained");
            } finally {jdbc.execute("DROP TRIGGER afk_reject_detail");}
            System.out.println("ENVIRONMENT SQL_REDIS_AND_REAL_SPRING_TRANSACTIONS_PASS");
            System.out.println("RISK_READINESS " + context.getBean(OrderRiskService.class).getTrainingReadiness());
            float[] features=new float[64*3];float[] labels=new float[64];double[][] vectors=new double[64][3];int[] classes=new int[64];
            for(int i=0;i<64;i++){labels[i]=i%2;classes[i]=i%2;for(int j=0;j<3;j++){features[i*3+j]=(i%2)*5+(i/2)*0.01f+j*0.2f;vectors[i][j]=features[i*3+j];}}
            var matrix=new DMatrix(features,64,3,Float.NaN);matrix.setLabel(labels);
            var booster=XGBoost.train(matrix,Map.of("objective","reg:squarederror","nthread",1,"max_depth",2),8,Map.of("train",matrix),null,null);
            Path model=Path.of("/runtime/algorithms/probe.ubj");Files.createDirectories(model.getParent());booster.saveModel(model.toString());
            var loaded=XGBoost.loadModel(model.toString());require(loaded.predict(matrix).length==64,"Linux XGBoost JNI train/save/load/predict");
            loaded.dispose();booster.dispose();matrix.dispose();
            var km=KMeans.fit(vectors,2);require(km.predict(vectors[0])>=0,"Smile clustering");
            var lr=LogisticRegression.fit(vectors,classes);require(lr.predict(vectors[0])>=0,"Smile logistic regression");
            System.out.println("ENVIRONMENT XGBOOST_JNI_AND_SMILE_SYNTHETIC_ALGORITHMS_PASS");
            if(serve){System.out.println("ENVIRONMENT READY_FOR_BROWSER");while(!Thread.currentThread().isInterrupted())Thread.sleep(1000);}
        } finally {context.close();}
    }
}
