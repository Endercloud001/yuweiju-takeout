import com.codeying.App;
import com.codeying.entity.*;
import com.codeying.mapper.*;
import com.codeying.properties.*;
import com.codeying.risk.RiskModelFiles;
import com.codeying.service.*;
import com.codeying.service.impl.OrderRiskServiceImpl.ModelBundle;
import com.codeying.utils.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.aop.support.AopUtils;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Synthetic machinery verification only. Each check batch retains its own database, files and Redis DB. */
public class Issue18Probe {
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    static final List<String> passed=new ArrayList<>();
    static final Map<String,Object> evidence=new LinkedHashMap<>();
    static Path out;
    static JdbcTemplate jdbc;
    static String base,admin,user,wrongScope,schema;
    static long normal,abnormal,missingData;
    static boolean business,cleanup=true;
    static ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> riskLogs;
    static ch.qos.logback.classic.Logger riskLogger;
    static final List<Long> orders=new ArrayList<>();
    @Configuration(proxyBeanMethods=false)
    public static class LocalMap {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor disableBackgroundScheduling() {
            return factory -> {
                var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
                String processor="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if(registry.containsBeanDefinition(processor))registry.removeBeanDefinition(processor);
            };
        }
        @Bean @Primary BaiduMapUtil localMap() {
            return new BaiduMapUtil() {
                @Override public int checkAndGetDrivingMinutes(String shop,String address,String key) { return 7; }
            };
        }
    }
    static void require(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        passed.add(label);System.out.println("ISSUE18 PASS "+label);
    }
    static long insert(String sql,Object... args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    static JsonNode request(String method,String path,String token,Object body)throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofMinutes(3));
        if(token!=null)b.header(path.startsWith("/admin/")?"token":"authentication",token);
        if(body!=null)b.header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return JSON.readTree(HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString()).body());
    }
    static JsonNode ok(String method,String path,String token,Object body)throws Exception {
        var response=request(method,path,token,body);
        require(response.path("code").asInt()==1,"HTTP_"+method+"_"+path.split("\\?")[0]);return response.path("data");
    }
    static int redis() {
        for(int db=1;db<16;db++) {
            var config=new RedisStandaloneConfiguration("redis",6379);config.setDatabase(db);
            var factory=new LettuceConnectionFactory(config);factory.afterPropertiesSet();factory.start();
            try(var connection=factory.getConnection()) {
                if(connection.serverCommands().dbSize()==0) {
                    connection.stringCommands().set("issue18:isolated-run".getBytes(java.nio.charset.StandardCharsets.UTF_8),out.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return db;
                }
            } finally {factory.destroy();}
        }
        throw new IllegalStateException("No unused fixture Redis DB; never flush retained runs");
    }
    static void prepareSchema()throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        schema="sandcastle_i18_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        try(var c=DriverManager.getConnection("jdbc:mysql://mysql:3306/sandcastle_fixture?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true","root","");var st=c.createStatement()) {
            require("sandcastle_fixture".equals(c.getCatalog()),"fixture_catalog_before_writes");
            evidence.put("driver",c.getMetaData().getDriverName()+" "+c.getMetaData().getDriverVersion());
            var tables=new ArrayList<String>();
            try(var rows=st.executeQuery("SHOW TABLES")){while(rows.next())tables.add(rows.getString(1));}
            require(tables.containsAll(List.of("orders","order_detail","order_risk_result","order_risk_feedback","order_risk_model_meta")),"actual_fixture_tables_before_writes");
            var ddls=new LinkedHashMap<String,String>();
            for(String table:tables){if(!table.matches("[a-z_]+"))throw new IllegalStateException("Unexpected fixture identifier");
                try(var r=st.executeQuery("SHOW CREATE TABLE `"+table+"`")){r.next();ddls.put(table,r.getString(2));}}
            Files.writeString(out.resolve("fixture-ddl.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(ddls));
            require(ddls.get("order_risk_result").contains("PRIMARY KEY (`order_id`,`model_version`)"),"actual_score_composite_key");
            require(ddls.get("order_risk_feature_snapshot").contains("PRIMARY KEY (`order_id`,`feature_version`)"),"actual_snapshot_composite_key");
            require(ddls.get("order_risk_model_meta").contains("PRIMARY KEY (`model_version`)"),"actual_model_version_key");
            st.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            for(String table:tables)st.execute("CREATE TABLE `"+schema+"`.`"+table+"` LIKE sandcastle_fixture.`"+table+"`");
            evidence.put("isolatedSchema",schema);evidence.put("copiedStructureOnly",true);
        }
    }
    static long order(int days,int label,boolean snapshot,boolean feedback)throws Exception {
        long id=insert("INSERT INTO orders(number,status,user_id,address_book_id,order_time,checkout_time,pay_status,amount,remark) VALUES(?,5,900001,900001,?,?,1,?,?)",
            "i18-"+UUID.randomUUID(),Timestamp.from(Instant.now().minus(Duration.ofDays(days))),Timestamp.from(Instant.now().minus(Duration.ofDays(days))),label==1?180:18,label==1?"test":"");
        orders.add(id);
        jdbc.update("INSERT INTO order_detail(name,order_id,dish_id,number,amount,image) VALUES('Synthetic',?,900001,1,18,'')",id);
        if(snapshot) {
            Map<String,Object> f=new LinkedHashMap<>();
            f.put("order_amount",label==1?180:18);f.put("item_count",1+days%3);f.put("unique_dish_count",1);
            f.put("remark_length",label==1?80:0);f.put("order_count_1h",label==1?8:1);f.put("order_count_24h",label==1?20:2);
            f.put("cancel_rate_30d",label==1?0.8:0.0);f.put("avg_amount_30d",18);f.put("order_amount_to_avg_ratio_30d",label==1?10:1);
            jdbc.update("INSERT INTO order_risk_feature_snapshot(order_id,feature_version,snapshot_json) VALUES(?,'v1',?)",id,JSON.writeValueAsString(f));
        }
        if(feedback)jdbc.update("INSERT INTO order_risk_feedback(order_id,decision,operator_id,reason) VALUES(?,?,900001,'Synthetic machinery label, no real effectiveness claim')",id,label==1?"reject":"approve");
        return id;
    }
    static void seedBase()throws Exception {
        jdbc.update("INSERT INTO employee(id,name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user) VALUES(900001,'Synthetic','i18_admin','unused','00000000000','1','fixture',1,NOW(),NOW(),900001,900001)");
        jdbc.update("INSERT INTO user(id,openid,name,phone,sex) VALUES(900001,'i18-a','Synthetic A','00000000000','1'),(900002,'i18-b','Synthetic B','00000000001','1')");
        jdbc.update("INSERT INTO category(id,type,name,sort,status,create_time,update_time,create_user,update_user) VALUES(900001,1,'Synthetic',1,1,NOW(),NOW(),900001,900001)");
        jdbc.update("INSERT INTO dish(id,name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(900001,'Synthetic',900001,18,'',1,NOW(),NOW(),900001,900001)");
        jdbc.update("INSERT INTO address_book(id,user_id,consignee,phone,detail,is_default) VALUES(900001,900001,'Synthetic','00000000000','Isolation street',1)");
    }
    public static void main(String[] args)throws Exception {
        try {run(args);}catch(Throwable ex) {
            System.out.println("ISSUE18 FAIL "+(ex instanceof AssertionError?ex.getMessage():"dependency_or_startup")+" class="+ex.getClass().getName());
            for(Throwable cause=ex;cause!=null;cause=cause.getCause()) {
                System.out.println("ISSUE18 CAUSE class="+cause.getClass().getName()+(cause instanceof SQLException sql?" sqlState="+sql.getSQLState():""));
                for(var frame:cause.getStackTrace())if(frame.getClassName().equals("Issue18Probe")){System.out.println("ISSUE18 LOCATION "+frame.getMethodName()+":"+frame.getLineNumber());break;}
            }
            evidence.put("failureClass",ex.getClass().getName());business=false;
        }finally {
            evidence.put("businessPassed",business);evidence.put("cleanupPassed",cleanup);evidence.put("assertions",passed);
            evidence.put("syntheticMachineryOnly",true);evidence.put("retainedDataAndFiles",true);
            if(riskLogs!=null) {
                var diagnostics=new ArrayList<String>();
                for(var event:riskLogs.list) {
                    String diagnostic=event.getFormattedMessage();
                    for(var cause=event.getThrowableProxy();cause!=null;cause=cause.getCause())diagnostic+=" <- "+cause.getClassName();
                    diagnostics.add(diagnostic);
                }
                Files.writeString(out.resolve("risk-diagnostics.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(diagnostics));
                riskLogger.detachAppender(riskLogs);riskLogs.stop();
            }
            if(out!=null)Files.writeString(out.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
            System.out.println("ISSUE18 RESULT businessPassed="+business+" cleanupPassed="+cleanup);
        }
        if(!business||!cleanup)System.exit(1);
    }
    static void run(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();out=Path.of(args[2]).toAbsolutePath().normalize();
        require(out.startsWith(root.resolve(".scratch/issue18")),"output_inside_task_root");
        Files.createDirectories(out.resolve("models"));prepareSchema();int db=redis();evidence.put("redisDatabase",db);
        var app=new SpringApplication(App.class,LocalMap.class);
        try(var context=app.run("--spring.config.location=file:"+Path.of(args[1]).resolve("application-afk.yml").toAbsolutePath(),"--spring.profiles.active=dev,afk",
            "--server.port=0","--logging.level.root=OFF","--spring.datasource.druid.url=jdbc:mysql://mysql:3306/"+schema+"?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
            "--order-risk.model-dir="+out.resolve("models"),"--analysis.xgboost.model-dir="+out.resolve("unused-analysis-models"),"--spring.data.redis.database="+db,"--spring.task.scheduling.enabled=false")) {
            jdbc=context.getBean(JdbcTemplate.class);
            riskLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.codeying.service.impl.OrderRiskServiceImpl.class);
            riskLogger.setLevel(ch.qos.logback.classic.Level.WARN);
            riskLogs=new ch.qos.logback.core.read.ListAppender<>();riskLogs.start();riskLogger.addAppender(riskLogs);
            require(schema.equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"fresh_schema_actual_spring_connection");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)==0,"fresh_current_training_window");
            var props=context.getBean(OrderRiskProperties.class);var risk=context.getBean(OrderRiskService.class);
            require(!context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"),"probe_disables_all_scheduled_callbacks_including_reply_locks");
            require(!props.getTask().getScoringEnabled()&&!context.getBean(AnalysisProperties.class).getTask().getEnabled(),"all_background_business_tasks_disabled");
            require(AopUtils.isAopProxy(risk),"actual_risk_spring_transaction_proxy");
            require(java.lang.reflect.Proxy.isProxyClass(context.getBean(OrderRiskResultMapper.class).getClass()),"actual_named_mapper_proxy");
            base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var jwt=context.getBean(SkyProperties.class).getJwt();
            admin=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","admin");
            user=JwtUtil.createToken(jwt.getUserSecretKey(),600000,900001L,"fixture","user");
            wrongScope=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","user");
            for(String endpoint:List.of("training/run","training/readiness","models","replay?modelVersion=absent","feedback")) {
                String method=endpoint.equals("training/run")||endpoint.equals("feedback")?"POST":"GET";
                for(String token:Arrays.asList(null,user,wrongScope))require(request(method,"/admin/order-risk/"+endpoint,token,null).path("code").asInt()!=1,"auth_rejected_"+endpoint);
            }
            var empty=ok("POST","/admin/order-risk/training/run",admin,null);
            require("SKIPPED".equals(empty.path("status").asText())&&"no_orders_in_window".equals(empty.path("reason").asText())&&!empty.path("trained").asBoolean(),"no_data_skip_code1_never_trained");
            require("MODEL_MISSING".equals(ok("GET","/admin/order-risk/replay?modelVersion=absent",admin,null).path("replayStatus").asText()),"missing_model_not_empty_replay");
            seedBase();normal=order(50,0,true,false);
            var noHoldout=ok("POST","/admin/order-risk/training/run",admin,null);
            require("insufficient_dataset".equals(noHoldout.path("reason").asText())&&!noHoldout.path("trained").asBoolean(),"missing_holdout_not_trained");
            props.getTask().setScoringEnabled(true);
            try { risk.scoreOrder(normal,"i18-no-model"); } finally { props.getTask().setScoringEnabled(false); }
            var fallback=risk.findLatestByOrderIds(List.of(normal)).get(normal);
            require("model_missing".equals(JSON.readTree(fallback.getReasonJson()).path("fallbackReason").asText()),"missing_model_explicit_rule_scoring");
            abnormal=order(10,1,true,true);
            var insufficient=ok("POST","/admin/order-risk/training/run",admin,null);
            require("SKIPPED".equals(insufficient.path("status").asText())&&"insufficient_positive_labels".equals(insufficient.path("reason").asText()),"insufficient_labels_skip");
            require(ok("GET","/admin/order-risk/models",admin,null).isEmpty(),"skips_no_model_metadata");
            // Full real current window, explicit synthetic labels >=50 positives, no threshold/algorithm change.
            for(int day=119;day>=1;day--)for(int n=0;n<2;n++)order(day,(day+n)%2,true,true);
            missingData=order(3,0,false,false);
            jdbc.update("DELETE FROM order_detail WHERE order_id=?",missingData);
            var ready=ok("GET","/admin/order-risk/training/readiness",admin,null);evidence.put("readiness",JSON.convertValue(ready,Object.class));
            require(ready.path("ready").asBoolean()&&ready.path("trainPositives").asInt()>=50&&ready.path("holdoutSamples").asInt()>0,"real_90day_train_30day_holdout_labels");
            require(ready.path("policyDerivedOrders").asInt()>=2,"unlabelled_candidate_separate_from_explicit_feedback");
            require(context.getBean(OrderRiskFeatureSnapshotMapper.class).findForOrders(List.of(normal),"v1' OR 1=1 --").isEmpty(),"feature_version_sql_bound");
            var trained=ok("POST","/admin/order-risk/training/run",admin,null);evidence.put("training",JSON.convertValue(trained,Object.class));
            require("TRAINED".equals(trained.path("status").asText())&&trained.path("trained").asBoolean()&&trained.path("activated").asBoolean(),"new_real_lr_rf_training_activated_without_old_model");
            String version=trained.path("modelVersion").asText(),artifact=trained.path("artifactPath").asText();
            System.out.println("ISSUE18 TRAINING status="+trained.path("status").asText()+" modelVersion="+version+" trainSamples="+trained.path("trainSamples").asInt()+" holdoutSamples="+trained.path("holdoutSamples").asInt()+" trainPositives="+trained.path("trainPositives").asInt()+" fileSaved="+trained.path("fileSaved").asBoolean()+" artifact="+artifact);
            require(Files.isRegularFile(Path.of(artifact))&&Path.of(artifact).startsWith(out.resolve("models")),"new_model_file_inside_unique_run");
            require(version.equals(jdbc.queryForObject("SELECT model_version FROM order_risk_model_meta WHERE status='ACTIVE'",String.class)),"new_version_metadata_matches_file_response");
            var loaded=RiskModelFiles.load(props.getModelDir(),artifact,ModelBundle.class);
            require(version.equals(loaded.getModelVersion())&&loaded.getLrModel()!=null&&loaded.getRfModel()!=null&&loaded.getStandardizerMeta().size()==9,"real_serialized_model_reload");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_standardizer_meta",Integer.class)==9,"real_standardizer_rows");
            var replay=ok("GET","/admin/order-risk/replay?modelVersion="+version,admin,null);
            require("AVAILABLE".equals(replay.path("replayStatus").asText())&&replay.path("metrics").path("trainSamples").asInt()==trained.path("trainSamples").asInt(),"replay_actual_new_sample_metrics");
            String featureVersion=props.getFeatureVersion();
            props.setFeatureVersion("i18-no-snapshots");
            try {
                var skippedWithActive=ok("POST","/admin/order-risk/training/run",admin,null);
                require("SKIPPED".equals(skippedWithActive.path("status").asText())&&!skippedWithActive.path("trained").asBoolean()&&!skippedWithActive.has("modelVersion"),"old_active_not_new_training_when_samples_missing");
                require(version.equals(jdbc.queryForObject("SELECT model_version FROM order_risk_model_meta WHERE status='ACTIVE'",String.class)),"skipped_run_preserves_old_active");
            }finally{props.setFeatureVersion(featureVersion);}
            // Scoring with a second genuine Service bean proves file-based loading, not training's cached model.
            var fresh=context.getAutowireCapableBeanFactory().createBean(com.codeying.service.impl.OrderRiskServiceImpl.class);
            require(AopUtils.isAopProxy(fresh),"fresh_spring_managed_risk_proxy_for_file_reload");
            props.getTask().setScoringEnabled(true);
            try {
                fresh.scoreOrder(normal,"i18-reload");risk.scoreOrder(abnormal,"i18-abnormal");risk.scoreOrder(missingData,"i18-incomplete");risk.scoreOrder(Long.MAX_VALUE,"i18-missing");
            }finally{props.getTask().setScoringEnabled(false);}
            var results=risk.findLatestByOrderIds(List.of(normal,abnormal,missingData,Long.MAX_VALUE));
            require(results.size()==3&&!results.containsKey(Long.MAX_VALUE),"missing_order_no_score_or_snapshot");
            require(version.equals(results.get(normal).getModelVersion())&&results.get(normal).getPLr()!=null&&results.get(abnormal).getPRf()!=null,"ordinary_abnormal_actual_loaded_model_inference");
            var normalReason=JSON.readTree(results.get(normal).getReasonJson());var abnormalReason=JSON.readTree(results.get(abnormal).getReasonJson());
            require("UNLABELLED_CANDIDATE".equals(normalReason.path("labelStatus").asText())&&"FEEDBACK_LABELLED".equals(abnormalReason.path("labelStatus").asText()),"candidate_and_feedback_label_distinct");
            require(!normalReason.path("topFeatures").isEmpty()&&!abnormalReason.path("ruleHits").isEmpty(),"score_explanations_retained");
            require("INCOMPLETE".equals(JSON.readTree(results.get(missingData).getReasonJson()).path("dataStatus").asText())&&"rule-baseline-v1".equals(results.get(missingData).getModelVersion()),"missing_details_explicit_incomplete_rule_fallback");
            require(risk.getTrainingReadiness().get("incompleteOrMissingSnapshots").equals(1),"incomplete_snapshot_excluded_from_training");
            ok("POST","/admin/order-risk/feedback",admin,Map.of("orderId",normal,"decision","reject","reason","synthetic feedback"));
            require("REJECT".equals(jdbc.queryForObject("SELECT decision_status FROM order_risk_result WHERE order_id=? AND model_version=?",String.class,normal,version)),"feedback_decision_persisted");
            require("reject".equals(jdbc.queryForObject("SELECT decision FROM order_risk_feedback WHERE order_id=? ORDER BY created_at DESC,id DESC LIMIT 1",String.class,normal)),"feedback_label_persisted");
            require(request("POST","/admin/order-risk/feedback",admin,Map.of("orderId",Long.MAX_VALUE,"decision","reject")).path("code").asInt()!=1,"missing_order_feedback_rejected");
            require(request("POST","/admin/order-risk/feedback",admin,Map.of("orderId",normal,"decision","ban")).path("code").asInt()!=1,"unsupported_feedback_no_user_ban");
            // Failure after file creation: real MySQL trigger, transaction rollback and retained orphan file.
            long before=jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_model_meta",Long.class);
            jdbc.execute("CREATE TRIGGER i18_fail_meta BEFORE INSERT ON order_risk_model_meta FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic model metadata failure'");
            try {
                var failed=ok("POST","/admin/order-risk/training/run",admin,null);evidence.put("partialFailure",JSON.convertValue(failed,Object.class));
                require("PARTIAL".equals(failed.path("status").asText())&&failed.path("fileSaved").asBoolean()&&!failed.path("metadataSaved").asBoolean()&&!failed.path("activated").asBoolean(),"sql_publish_failure_truthful_partial_not_success");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_model_meta",Long.class)==before&&version.equals(jdbc.queryForObject("SELECT model_version FROM order_risk_model_meta WHERE status='ACTIVE'",String.class)),"failed_publish_rolls_back_old_active_archive");
            }finally{jdbc.execute("DROP TRIGGER i18_fail_meta");}
            Path obstacle=out.resolve("not-a-directory");Files.writeString(obstacle,"synthetic obstacle");String dir=props.getModelDir();props.setModelDir(obstacle.toString());
            try {
                var failed=ok("POST","/admin/order-risk/training/run",admin,null);evidence.put("fileFailure",JSON.convertValue(failed,Object.class));
                require("FAILED".equals(failed.path("status").asText())&&!failed.path("fileSaved").asBoolean()&&!failed.path("activated").asBoolean(),"filesystem_failure_no_false_activation");
            }finally{props.setModelDir(dir);}
            // Feedback multi-write rollback via actual Spring proxy and JDBC failure.
            long feedbackBefore=jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_feedback WHERE order_id=?",Long.class,normal);
            jdbc.execute("CREATE TRIGGER i18_fail_feedback BEFORE UPDATE ON order_risk_result FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic decision failure'");
            try {
                boolean threw=false;try{risk.submitFeedback(normal,"approve",900001L,"synthetic");}catch(RuntimeException ex){threw=ex.getCause()!=null;}
                require(threw,"feedback_failure_internal_cause_retained");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_feedback WHERE order_id=?",Long.class,normal)==feedbackBefore,"feedback_label_rollback_on_decision_failure");
            }finally{jdbc.execute("DROP TRIGGER i18_fail_feedback");}
            // JSON arrays are valid database JSON but invalid metric objects; never treat as empty metrics.
            jdbc.update("INSERT INTO order_risk_model_meta(model_version,artifact_path,metrics_json,status,train_window_start,train_window_end) VALUES('i18-invalid-metrics',?,'[]','REJECTED',CURRENT_DATE,CURRENT_DATE)",artifact);
            require("INVALID_METRICS".equals(ok("GET","/admin/order-risk/replay?modelVersion=i18-invalid-metrics",admin,null).path("replayStatus").asText()),"invalid_replay_not_empty_metrics");
            // Empty metrics and missing artifact remain different from missing metadata.
            jdbc.update("INSERT INTO order_risk_model_meta(model_version,artifact_path,metrics_json,status,train_window_start,train_window_end) VALUES('i18-empty',?,'{}','REJECTED',CURRENT_DATE,CURRENT_DATE)",artifact);
            require("NO_METRICS".equals(ok("GET","/admin/order-risk/replay?modelVersion=i18-empty",admin,null).path("replayStatus").asText()),"existing_model_empty_replay_distinct");
            jdbc.update("INSERT INTO order_risk_model_meta(model_version,artifact_path,metrics_json,status,train_window_start,train_window_end) VALUES('i18-missing-file',?,'{}','REJECTED',CURRENT_DATE,CURRENT_DATE)",out.resolve("absent.ser").toString());
            require("MODEL_FILE_MISSING".equals(ok("GET","/admin/order-risk/replay?modelVersion=i18-missing-file",admin,null).path("replayStatus").asText()),"missing_file_distinct");
            // Auth exclusions read from actual WebMvcConfiguration: catalog is public, ordering is protected.
            require(request("GET","/user/category/list?type=1",null,null).path("code").asInt()==1,"actual_public_catalog_exclusion");
            require(request("POST","/user/order/submit",null,Map.of("addressBookId",900001)).path("code").asInt()!=1,"anonymous_order_rejected");
            jdbc.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Synthetic',900001,900001,1,18,NOW())");
            var submission=ok("POST","/user/order/submit",user,Map.of("addressBookId",900001,"payMethod",1,"deliveryStatus",1,"tablewareNumber",1,"tablewareStatus",1));
            long submitted=submission.path("id").asLong();String number=submission.path("orderNumber").asText();
            require(submitted>0&&jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id=?",Integer.class,submitted)==1,"normal_order_real_sql_transaction");
            String otherUser=JwtUtil.createToken(jwt.getUserSecretKey(),600000,900002L,"fixture","user");
            require(request("GET","/user/order/orderDetail/"+submitted,otherUser,null).path("code").asInt()!=1,"foreign_order_read_rejected");
            require(request("PUT","/user/order/payment",otherUser,Map.of("orderNumber",number)).path("code").asInt()!=1,"foreign_order_payment_rejected");
            ok("PUT","/user/order/payment",user,Map.of("orderNumber",number));
            require(jdbc.queryForObject("SELECT pay_status FROM orders WHERE id=?",Integer.class,submitted)==1,"normal_simulated_payment_unchanged");
            ok("PUT","/admin/order/rejection",admin,Map.of("id",submitted,"rejectionReason","synthetic rejection"));
            require(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",Integer.class,submitted)==Orders.CANCELLED,"order_level_rejection_semantics");
            jdbc.update("INSERT INTO shopping_cart(name,user_id,dish_id,number,amount,create_time) VALUES('Synthetic',900001,900001,1,18,NOW())");
            jdbc.execute("CREATE TRIGGER i18_score_failure BEFORE INSERT ON order_risk_result FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic optional risk failure'");
            props.getTask().setScoringEnabled(true);
            try {
                var resilient=ok("POST","/user/order/submit",user,Map.of("addressBookId",900001,"payMethod",1));
                long resilientId=resilient.path("id").asLong();
                require(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?",Integer.class,resilientId)==1&&jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id=?",Integer.class,resilientId)==1,"optional_risk_failure_preserves_core_order_transaction");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM order_risk_feature_snapshot WHERE order_id=?",Integer.class,resilientId)==0,"failed_optional_score_savepoint_no_partial_snapshot");
            }finally{props.getTask().setScoringEnabled(false);jdbc.execute("DROP TRIGGER i18_score_failure");}
            require(props.getTraining().getMinAnomalyLabels()==50&&props.getThresholds().getLow()==12&&props.getThresholds().getMedium()==16&&props.getScoring().getLrWeight()==0.4,"original_policy_thresholds_weights_unchanged");
            String diagnostics=riskLogs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining());
            require(diagnostics.contains("SQLException")&&diagnostics.contains("FileAlreadyExistsException"),"real_sql_and_file_failure_cause_chains_retained");
            require(!diagnostics.contains("synthetic model metadata failure")&&!diagnostics.contains("synthetic optional risk failure"),"dependency_messages_sanitized_from_risk_logs");
            cleanup=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()",Integer.class)==0;
            require(cleanup,"owned_failure_triggers_removed_no_data_or_file_cleanup");
            evidence.put("syntheticOrderIds",orders);evidence.put("currentWindowEnd",Instant.now().toString());evidence.put("modelDirectory",root.relativize(out.resolve("models")).toString());business=true;
        }
    }
}
