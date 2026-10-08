import com.codeying.App;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.OrderRiskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.nio.file.*;

public class TrainingProbe {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(App.class,EnvironmentProbe.LocalMap.class);
        try(var context=app.run("--spring.config.location=file:/environment/application-afk.yml","--spring.profiles.active=dev,afk","--logging.level.root=WARN")){
            var jdbc=context.getBean(JdbcTemplate.class);var json=context.getBean(ObjectMapper.class);
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"isolated schema");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)==0,"training must start in its own empty isolated database");
            LocalDate target=LocalDate.now(ZoneId.of("Asia/Shanghai"));
            // Artificial patterns and explicit labels exercise the machinery; they are not business ground truth.
            for(int u=0;u<9;u++)jdbc.update("INSERT INTO user(id,openid,name,phone,sex) VALUES(?,?,?,?,?)",900010L+u,"training-fixture-"+u,"Synthetic training "+u,"00000000000","1");
            for(int d=0;d<4;d++)jdbc.update("INSERT INTO dish(id,name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(?,?,900001,?, '',1,NOW(),NOW(),900001,900001)",900010L+d,"Synthetic dish "+d,10+d*10);
            long id=910000;
            for(int day=100;day>=1;day--){
                for(int u=0;u<9;u++){
                    if((day+u)%(1+u/3)!=0)continue;
                    long orderId=++id;long dishId=900010L+(u+day)%4;int quantity=1+u/3;double amount=(10+((u+day)%4)*10)*quantity;
                    var time=Timestamp.valueOf(target.minusDays(day).atTime(12+u%2*6,0));
                    jdbc.update("INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,checkout_time,pay_status,amount) VALUES(?,?,5,?,900001,?,?,1,?)",orderId,"synthetic-"+orderId,900010L+u,time,time,amount);
                    jdbc.update("INSERT INTO order_detail(name,order_id,dish_id,number,amount,image) VALUES('Synthetic detail',?,?,?,?,'')",orderId,dishId,quantity,amount/quantity);
                    int label=(u+day)%2;
                    Map<String,Object> features=new LinkedHashMap<>();features.put("order_amount",amount);features.put("item_count",quantity);features.put("unique_dish_count",1);
                    features.put("remark_length",label==1?80:0);features.put("order_count_1h",label==1?8:1);features.put("order_count_24h",label==1?20:2);
                    features.put("cancel_rate_30d",label==1?0.8:0.0);features.put("avg_amount_30d",amount);features.put("order_amount_to_avg_ratio_30d",label==1?3.0:1.0);
                    jdbc.update("INSERT INTO order_risk_feature_snapshot(order_id,feature_version,snapshot_json) VALUES(?,'v1',?)",orderId,json.writeValueAsString(features));
                    jdbc.update("INSERT INTO order_risk_feedback(order_id,decision,operator_id,reason) VALUES(?,?,900001,'Artificial fixture label, not a real adjudication')",orderId,label==1?"reject":"approve");
                }
            }
            var risk=context.getBean(OrderRiskService.class);Map<String,Object> readiness=risk.getTrainingReadiness();System.out.println("SYNTHETIC_RISK_READINESS "+json.writeValueAsString(readiness));
            risk.runScheduledTraining();
            var models=jdbc.queryForList("SELECT model_version,artifact_path,metrics_json,status FROM order_risk_model_meta");require(models.size()==1,"actual new risk model metadata");
            String riskPath=(String)models.get(0).get("artifact_path");require(Files.isRegularFile(Path.of(riskPath)),"actual new risk model file");
            System.out.println("SYNTHETIC_RISK_TRAINING "+json.writeValueAsString(models));
            risk.scoreOrder(910001L,"isolated-training-probe");
            // Scoring flag is deliberately off for automatic application entrypoints, enable only this explicit isolated call.
            var props=context.getBean(com.codeying.properties.OrderRiskProperties.class);props.getTask().setScoringEnabled(true);
            risk.scoreOrder(910001L,"isolated-training-probe");props.getTask().setScoringEnabled(false);
            require(jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_result WHERE order_id=910001",Integer.class)>0,"new risk score persisted");
            var analysis=context.getBean(AnalysisApplicationService.class);analysis.generateDailySnapshots(target);
            var trained=analysis.runWeeklyTraining(target);require(trained.executed()&&trained.heatModelTrained(),"actual new analysis model trained");
            analysis.runDailyPrediction(target);
            int users=jdbc.queryForObject("SELECT COUNT(*) FROM user_behavior_feature_snapshot WHERE snapshot_date=?",Integer.class,target);
            int clusters=jdbc.queryForObject("SELECT COUNT(*) FROM user_cluster_result WHERE snapshot_date=?",Integer.class,target);
            int predictions=jdbc.queryForObject("SELECT COUNT(*) FROM dish_heat_prediction_result WHERE window_start=?",Integer.class,target);
            require(users>0&&clusters>0&&predictions>0,"new snapshots clusters predictions");
            System.out.println("SYNTHETIC_ANALYSIS_TRAINING "+json.writeValueAsString(Map.of("target",target.toString(),"training",trained,"userSnapshots",users,"clusterRows",clusters,"predictionRows",predictions,"metrics",analysis.getOfflineValidationMetrics(target))));
            System.out.println("ENVIRONMENT SYNTHETIC_BUSINESS_TRAINING_PASS; NO_REAL_DATA_OR_EFFECTIVENESS_CLAIM");
        }
    }
}
