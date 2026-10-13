import com.codeying.App;
import com.codeying.analysis.HeatModelStore;
import com.codeying.constant.RedisKeys;
import com.codeying.entity.DishTimeseriesFeatureSnapshot;
import com.codeying.mapper.*;
import com.codeying.properties.*;
import com.codeying.service.*;
import com.codeying.utils.JwtUtil;
import com.codeying.vo.admin.analysis.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.aop.support.AopUtils;
import ml.dmlc.xgboost4j.java.*;
import smile.clustering.KMeans;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Real isolated SQL/Spring/XGBoost/Smile/Redis/model IO. Synthetic effectiveness is never business acceptance. */
public class Issue17Probe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final List<String> passed = new ArrayList<>();
    static final List<Long> users = new ArrayList<>(), dishes = new ArrayList<>();
    static final Map<String,Object> evidence = new LinkedHashMap<>();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static Path out;
    static JdbcTemplate jdbc;
    static LocalDate target;
    static String base, admin, user, wrongScope;
    static boolean business, cleanup;
    static String failure = "";
    static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed.add(label); System.out.println("ISSUE17 PASS " + label);
    }
    static long insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(c -> { var statement = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); return statement; }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    static JsonNode request(String method, String path, String token, String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofMinutes(3));
        if(token!=null)builder.header("token",token);
        if(body!=null)builder.header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var response=HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body());
    }
    static JsonNode stage(JsonNode run,String name) {
        for(var stage:run.path("stages")) if(name.equals(stage.path("stage").asText()))return stage;
        throw new AssertionError("missing stage "+name);
    }
    static AnalysisStage stage(AnalysisRun run,String name) {
        return run.stages().stream().filter(s->name.equals(s.stage())).findFirst().orElseThrow();
    }
    static long count(String table,String column,LocalDate date) {
        // Identifiers are fixed probe constants, all dates are bound JDBC parameters.
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Long.class,date);
    }
    static int freshRedis() {
        for(int db=1;db<16;db++) {
            var config=new RedisStandaloneConfiguration("redis",6379);config.setDatabase(db);
            var factory=new LettuceConnectionFactory(config);factory.afterPropertiesSet();factory.start();
            try(var connection=factory.getConnection()) {
                if(connection.serverCommands().dbSize()==0) {
                    connection.stringCommands().set("issue17:isolated-run".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            out.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return db;
                }
            } finally { factory.destroy(); }
        }
        throw new IllegalStateException("No fresh isolated Redis DB; retained runs must not be reused or flushed");
    }
    static void seed() {
        LocalDate maximum=jdbc.queryForObject("SELECT MAX(day) FROM (SELECT DATE(MAX(order_time)) AS day FROM orders UNION ALL SELECT MAX(snapshot_date) FROM user_behavior_feature_snapshot UNION ALL SELECT MAX(snapshot_date) FROM dish_timeseries_feature_snapshot UNION ALL SELECT MAX(window_start) FROM dish_heat_prediction_result UNION ALL SELECT MAX(snapshot_date) FROM user_cluster_result) dates",LocalDate.class);
        target=(maximum==null||maximum.isBefore(LocalDate.of(2040,1,1))?LocalDate.of(2040,1,1):maximum).plusDays(180);
        require(target.getYear()<9990,"new_target_within_supported_range");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_time >= ? AND order_time < ?",Long.class,
                Timestamp.valueOf(target.minusDays(120).atStartOfDay()),Timestamp.valueOf(target.plusDays(1).atStartOfDay()))==0,"new_history_window_unoccupied");
        for(String table:List.of("user_behavior_feature_snapshot","dish_timeseries_feature_snapshot","user_cluster_result"))require(count(table,"snapshot_date",target)==0,"new_target_"+table);
        require(count("dish_heat_prediction_result","window_start",target)==0,"new_target_prediction_window");
        evidence.put("targetDate",target.toString());evidence.put("historyWindowStart",target.minusDays(120).toString());evidence.put("historyWindowEndExclusive",target.toString());
        for(int u=0;u<9;u++)users.add(insert("INSERT INTO user(openid,name,phone,sex) VALUES(?,?,?,?)","issue17-"+out.getFileName()+"-"+u,"Synthetic "+u,"00000000000","1"));
        for(int d=0;d<4;d++)dishes.add(insert("INSERT INTO dish(name,category_id,price,image,status,create_time,update_time,create_user,update_user) VALUES(?,900001,?,'',1,NOW(),NOW(),900001,900001)","Issue17 synthetic "+d,10+d*10));
        int orderCount=0;
        for(int day=120;day>=1;day--) for(int u=0;u<9;u++) {
            if((day+u)%(1+u/3)!=0)continue;
            int d=(u+day)%4,quantity=1+u/3;
            var time=Timestamp.valueOf(target.minusDays(day).atTime(12+(u%2)*6,0));
            long order=insert("INSERT INTO orders(number,status,user_id,address_book_id,order_time,checkout_time,pay_status,amount) VALUES(?,5,?,900001,?,?,1,?)",
                    "i17-"+out.getFileName()+"-"+day+"-"+u,users.get(u),time,time,(10+d*10)*quantity);
            jdbc.update("INSERT INTO order_detail(name,order_id,dish_id,number,amount,image) VALUES('Synthetic history',?,?,?,?,'')",order,dishes.get(d),quantity,10+d*10);
            orderCount++;
        }
        evidence.put("syntheticOrders",orderCount);evidence.put("syntheticUsers",users.size());evidence.put("syntheticDishes",dishes.size());
    }
    static void ddl() throws Exception {
        var dataSource=jdbc.getDataSource();
        try(var connection=Objects.requireNonNull(dataSource).getConnection()) {
            String url=connection.getMetaData().getURL();
            require(url.startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"),"internal_mysql_url_checked_before_sql_writes");
            require("sandcastle_fixture".equals(connection.getCatalog()),"internal_schema_checked_before_sql_writes");
            evidence.put("driver",connection.getMetaData().getDriverName()+" "+connection.getMetaData().getDriverVersion());
        }
        require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"isolated_database");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE id=900001 AND username='sandbox_admin'",Integer.class)==1,"named_employee_fixture");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id=900001 AND name='Fixture category'",Integer.class)==1,"named_category_fixture");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=900001 AND openid='sandbox-user-a'",Integer.class)==1,"named_user_fixture");
        var constraints=jdbc.queryForList("SELECT TABLE_NAME,INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_key FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND NON_UNIQUE=0 GROUP BY TABLE_NAME,INDEX_NAME");
        evidence.put("schemaKeys",constraints);
        for(String table:List.of("user_behavior_feature_snapshot","dish_timeseries_feature_snapshot","user_cluster_result","dish_heat_prediction_result"))
            require(constraints.stream().anyMatch(row->table.equals(row.get("TABLE_NAME"))&&String.valueOf(row.get("columns_in_key")).split(",").length>=3),"compound_unique_key_"+table);
        require(constraints.stream().anyMatch(row->"PRIMARY".equals(row.get("INDEX_NAME"))),"actual_primary_keys_loaded");
        require(constraints.stream().anyMatch(row->"order_risk_feature_snapshot".equals(row.get("TABLE_NAME"))
                && "PRIMARY".equals(row.get("INDEX_NAME")) && "order_id,feature_version".equals(row.get("columns_in_key"))),
                "named_fixture_composite_primary_key_read_only_no_risk_training");
    }
    public static void main(String[] args) throws Exception {
        try { run(args); }
        catch (Throwable ex) {
            System.out.println("ISSUE17 FAIL probe preparation class=" + ex.getClass().getName());
            if (out != null) Files.writeString(out.resolve("scenario-results.json"), JSON.writeValueAsString(
                    Map.of("businessPassed", false, "cleanupPassed", false, "failureClass", ex.getClass().getName())));
            System.exit(1);
        }
    }
    static void run(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();out=Path.of(args[2]).toAbsolutePath().normalize();
        if(!out.startsWith(root.resolve(".scratch/issue17")))throw new IllegalArgumentException("probe output must be inside task scratch");
        Files.createDirectories(out.resolve("models"));Files.createDirectories(out.resolve("cache"));
        require("Asia/Shanghai".equals(TimeZone.getDefault().getID()),"explicit_fixture_business_timezone");
        int redisDb=freshRedis();evidence.put("redisDatabase",redisDb);evidence.put("modelDirectory",root.relativize(out.resolve("models")).toString());
        var app=new SpringApplication(App.class);
        try(var context=app.run("--spring.config.location=file:"+Path.of(args[1]).resolve("application-afk.yml").toAbsolutePath(),
                "--spring.profiles.active=dev,afk","--server.port=0","--logging.level.root=OFF",
                "--analysis.xgboost.model-dir="+out.resolve("models"),"--order-risk.model-dir="+out.resolve("unused-risk-models"),
                "--spring.data.redis.database="+redisDb,"--spring.task.scheduling.enabled=false")) {
            jdbc=context.getBean(JdbcTemplate.class);ddl();
            require(!context.getBean(AnalysisProperties.class).getTask().getEnabled()&&!context.getBean(OrderRiskProperties.class).getTask().getScoringEnabled(),"background_business_tasks_disabled");
            require(context.getBean(SkyProperties.class).getQweather().getApiHost()==null,"existing_dev_weather_degrade_fixture_no_external_calls");
            require(AopUtils.isAopProxy(context.getBean(DishService.class)),"real_spring_service_proxy");
            require(java.lang.reflect.Proxy.isProxyClass(context.getBean(AnalysisFeatureMapper.class).getClass()),"real_mapper_proxy");
            var redis=context.getBean(StringRedisTemplate.class);
            require(out.getFileName().toString().equals(redis.opsForValue().get("issue17:isolated-run")),"fresh_redis_db_namespace_before_business_writes");
            base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var jwt=context.getBean(SkyProperties.class).getJwt();
            admin=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","admin");
            user=JwtUtil.createToken(jwt.getUserSecretKey(),600000,900001L,"fixture","user");
            wrongScope=JwtUtil.createToken(jwt.getAdminSecretKey(),600000,900001L,"fixture","user");
            for(String endpoint:List.of("snapshot/run","training/run","prediction/run","backfill")) {
                require(request("POST","/admin/analysis/"+endpoint,null,null).path("code").asInt()!=1,"anonymous_rejected_"+endpoint);
                require(request("POST","/admin/analysis/"+endpoint,user,null).path("code").asInt()!=1,"user_token_rejected_"+endpoint);
                require(request("POST","/admin/analysis/"+endpoint,wrongScope,null).path("code").asInt()!=1,"wrong_scope_rejected_"+endpoint);
            }
            for(String endpoint:List.of("heat","clusters","metrics/offline","metrics/online")) {
                require(request("GET","/admin/analysis/"+endpoint,null,null).path("code").asInt()!=1,"protected_read_"+endpoint);
                require(request("GET","/admin/analysis/"+endpoint,wrongScope,null).path("code").asInt()!=1,"wrong_scope_read_"+endpoint);
            }
            require(request("POST","/admin/analysis/prediction/run?date=not-a-date",admin,null).path("msg").asText().contains("日期"),"invalid_date_friendly");
            require(request("POST","/admin/analysis/backfill",admin,"{\"endDate\":\"2026-01-01\",\"days\":0}").path("code").asInt()!=1,"invalid_range_rejected");
            require(request("POST","/admin/analysis/backfill",admin,"{\"endDate\":\"2026-01-01\",\"days\":366}").path("code").asInt()!=1,"excessive_range_rejected");
            seed();
            var analysis=context.getBean(AnalysisApplicationService.class);
            var snapshots=context.getBean(DishTimeseriesFeatureSnapshotMapper.class);
            var feature=context.getBean(AnalysisFeatureMapper.class);
            require(context.getBean(UserBehaviorFeatureSnapshotMapper.class).findDaily(target,"v1' OR 1=1 --").isEmpty(),"feature_version_parameter_binding");
            var aggregate=context.getBean(OrderDetailMapper.class).aggregateAnalysisSales(
                    Timestamp.valueOf(target.minusDays(30).atStartOfDay()),Timestamp.valueOf(target.atStartOfDay()),users,null,null);
            require(!aggregate.isEmpty(),"real_bound_completed_sales_join");
            require(context.getBean(OrderDetailMapper.class).aggregateAnalysisSales(Timestamp.valueOf(target.minusDays(30).atStartOfDay()),
                    Timestamp.valueOf(target.atStartOfDay()),users,"' OR 1=1 --",10).isEmpty(),"district_parameter_binding");
            var empty=analysis.runPredictionUseCase(target.plusDays(10));
            require("SKIPPED".equals(empty.status())&&"NO_DISH_SNAPSHOTS".equals(stage(empty,"prediction").reason()),"missing_dish_snapshots_skipped");
            var skippedHttp=request("POST","/admin/analysis/prediction/run?date="+target.plusDays(10),admin,null);
            require(skippedHttp.path("code").asInt()==1&&"SKIPPED".equals(skippedHttp.path("data").path("status").asText()),"prediction_skip_legacy_code_one_truthful_status");
            var noUsers=analysis.runTrainingUseCase(target.plusDays(11));
            require(!noUsers.executed()&&"NO_USER_SNAPSHOTS".equals(noUsers.response().get("skipReason")),"missing_user_snapshots_not_executed");
            var legacySkipped=analysis.runWeeklyTraining(target.plusDays(11));
            require(!legacySkipped.executed()&&!legacySkipped.heatModelTrained()&&"NO_USER_SNAPSHOTS".equals(legacySkipped.skipReason()),"legacy_weekly_executed_skip_compatible");
            var initial=analysis.runSnapshotUseCase(target);
            require("COMPLETE".equals(initial.status()),"new_snapshots_committed");
            LocalDate missingDishDate=target.plusDays(12);
            for(var snapshot:context.getBean(UserBehaviorFeatureSnapshotMapper.class).findDaily(target,"v1")) {
                snapshot.setId(null);snapshot.setSnapshotDate(missingDishDate);
                context.getBean(UserBehaviorFeatureSnapshotMapper.class).insert(snapshot);
            }
            var missingDishTraining=analysis.runTrainingUseCase(missingDishDate);
            require(missingDishTraining.executed()&&"NO_DISH_SNAPSHOTS".equals(stage(missingDishTraining,"heatTraining").reason()),"missing_dish_training_partial_with_cluster_outputs");
            var fallback=analysis.runPredictionUseCase(target);
            require("PARTIAL".equals(fallback.status())&&"NO_MODEL_BASELINE".equals(stage(fallback,"prediction").reason()),"missing_model_baseline_not_model_success");
            require(!Files.exists(out.resolve("models/dish-heat-xgboost.meta.json")),"prediction_does_not_claim_or_train_new_model");
            var response=request("POST","/admin/analysis/training/run?date="+target,admin,null);
            var run=response.path("data");evidence.put("training",JSON.convertValue(run,Object.class));
            require(response.path("code").asInt()==1&&"COMPLETE".equals(run.path("status").asText()),"real_new_full_synchronous_pipeline");
            require(run.path("heatModelTrained").asBoolean()&&stage(run,"heatTraining").path("outputs").path("trainSampleCount").asInt()>50,"genuine_new_training_with_enough_synthetic_samples");
            long userCount=count("user_behavior_feature_snapshot","snapshot_date",target),dishCount=count("dish_timeseries_feature_snapshot","snapshot_date",target),clusterCount=count("user_cluster_result","snapshot_date",target),predictionCount=count("dish_heat_prediction_result","window_start",target);
            evidence.put("sqlCounts",Map.of("users",userCount,"dishes",dishCount,"clusters",clusterCount,"predictions",predictionCount));
            require(userCount==clusterCount&&dishCount==predictionCount&&userCount>=9&&dishCount>=4,"actual_sql_counts_match_pipeline");
            require(stage(run,"snapshot").path("outputs").path("userSnapshotCount").asLong()==userCount&&stage(run,"prediction").path("outputs").path("predictionCount").asLong()==predictionCount,"response_counts_match_sql");
            String version=stage(run,"heatTraining").path("outputs").path("modelVersion").asText();
            require(version.contains(target.toString())&&version.equals(jdbc.queryForObject("SELECT DISTINCT model_version FROM dish_heat_prediction_result WHERE window_start=?",String.class,target)),"saved_model_version_matches_prediction_sql");
            var store=context.getBean(HeatModelStore.class);var meta=store.metadata();evidence.put("modelMetadata",meta);
            require(Files.isRegularFile(out.resolve("models").resolve((String)meta.get("modelFile"))),"new_version_model_file_saved");
            try(var loaded=store.load()) {
                require(loaded!=null&&version.equals(loaded.version()),"new_model_loaded_with_real_version");
                DMatrix matrix=new DMatrix(new float[21],1,21,Float.NaN);
                try { require(Float.isFinite(loaded.booster().predict(matrix)[0][0]),"reloaded_xgboost_prediction_finite"); }
                finally { matrix.dispose(); }
            }
            require(analysis.listHeatPredictions(target,2).size()==2&&analysis.listUserClusters(target,2).size()==2,"admin_rank_and_cluster_compatibility");
            var recommendation=analysis.listRecommendedDishIdsForUser(users.get(0),5);
            require(!recommendation.isEmpty()&&recommendation.stream().allMatch(dishes::contains),"miniapp_cluster_recommendation_ids_compatible");
            redis.delete(RedisKeys.userClusterKey(users.get(0)));
            require(!analysis.listRecommendedDishIdsForUser(users.get(0),5).isEmpty(),"recommendation_sql_recovery_after_cache_miss");
            var offline=analysis.getOfflineValidationMetrics(target);evidence.put("syntheticEffectiveness",offline);
            require(version.equals(offline.get("modelVersion"))&&((Number)offline.get("sampleCount")).intValue()>0&&offline.containsKey("rmsePass")&&offline.containsKey("maePass"),"this_model_metrics_not_historical_success");
            require(Double.valueOf(0.30).equals(context.getBean(AnalysisProperties.class).getValidation().getRmseRatioThreshold())&&Double.valueOf(0.20).equals(context.getBean(AnalysisProperties.class).getValidation().getMaeRatioThreshold()),"original_effect_thresholds_retained");
            var sqlUser=jdbc.queryForMap("SELECT rfm_frequency_30d,rfm_monetary_30d FROM user_behavior_feature_snapshot WHERE snapshot_date=? AND user_id=? AND feature_version='v1'",target,users.get(0));
            var expected=jdbc.queryForMap("SELECT COUNT(*) AS frequency,SUM(amount) AS monetary FROM orders WHERE user_id=? AND status=5 AND order_time>=? AND order_time<?",users.get(0),Timestamp.valueOf(target.minusDays(30).atStartOfDay()),Timestamp.valueOf(target.atStartOfDay()));
            require(((Number)sqlUser.get("rfm_frequency_30d")).longValue()==((Number)expected.get("frequency")).longValue()&&((java.math.BigDecimal)sqlUser.get("rfm_monetary_30d")).compareTo((java.math.BigDecimal)expected.get("monetary"))==0,"sql_feature_counts_and_amount_match_source_history");
            // Preserve original derived rows while proving failed replacements roll back through the real transaction manager.
            String trigger="i17_prediction_"+out.getFileName().toString().replaceAll("[^a-zA-Z0-9]","");
            jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON dish_heat_prediction_result FOR EACH ROW BEGIN IF NEW.window_start='"+target+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated prediction failure'; END IF; END");
            try {
                var failed=analysis.runPredictionUseCase(target);
                require("FAILED".equals(failed.status())&&stage(failed,"prediction").failureCause().getCause()!=null,"real_sql_failure_retains_internal_cause");
                require(count("dish_heat_prediction_result","window_start",target)==predictionCount,"prediction_replacement_delete_rolled_back");
                require(!JSON.writeValueAsString(failed.response()).contains("SQLSTATE"),"failure_response_sanitized");
            } finally { jdbc.execute("DROP TRIGGER "+trigger); }
            trigger="i17_snapshot_"+out.getFileName().toString().replaceAll("[^a-zA-Z0-9]","");
            jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON dish_timeseries_feature_snapshot FOR EACH ROW BEGIN IF NEW.snapshot_date='"+target+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated snapshot failure'; END IF; END");
            try {
                var failed=analysis.runManualTraining(target);
                require(!failed.executed()&&"FAILED".equals(stage(failed,"snapshot").status()),"failed_snapshot_pipeline_stops_training");
                require(count("user_behavior_feature_snapshot","snapshot_date",target)==userCount&&count("dish_timeseries_feature_snapshot","snapshot_date",target)==dishCount,"both_snapshot_tables_rollback_on_second_table_failure");
            } finally { jdbc.execute("DROP TRIGGER "+trigger); }
            trigger="i17_cluster_"+out.getFileName().toString().replaceAll("[^a-zA-Z0-9]","");
            String previousCluster=jdbc.queryForObject("SELECT DISTINCT model_version FROM user_cluster_result WHERE snapshot_date=?",String.class,target);
            jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON user_cluster_result FOR EACH ROW BEGIN IF NEW.snapshot_date='"+target+"' AND NEW.user_id="+users.get(1)+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated cluster failure'; END IF; END");
            try {
                var failed=analysis.runTrainingUseCase(target);
                require(!failed.executed()&&"FAILED".equals(stage(failed,"clustering").status()),"failed_cluster_write_not_executed");
                require(count("user_cluster_result","snapshot_date",target)==clusterCount&&previousCluster.equals(jdbc.queryForObject("SELECT DISTINCT model_version FROM user_cluster_result WHERE snapshot_date=?",String.class,target)),"cluster_replacement_partial_insert_rolled_back");
            } finally { jdbc.execute("DROP TRIGGER "+trigger); }
            // A different new date has snapshots but no observed history; old model must not count as new training.
            LocalDate insufficient=target.plusDays(150);analysis.runSnapshotUseCase(insufficient);
            var notTrained=analysis.runTrainingUseCase(insufficient);
            require(!Boolean.TRUE.equals(notTrained.response().get("heatModelTrained"))&&"INSUFFICIENT_SAMPLES".equals(stage(notTrained,"heatTraining").reason()),"insufficient_observed_samples_old_model_not_success");
            evidence.put("insufficientDate",insufficient.toString());
            // Actual filesystem dependency failure, after clustering commits, produces partial success.
            var props=context.getBean(AnalysisProperties.class);String modelDir=props.getXgboost().getModelDir();
            Path blocked=out.resolve("not-a-directory");Files.writeString(blocked,"synthetic file obstacle");
            props.getXgboost().setModelDir(blocked.toString());
            try {
                var partial=analysis.runTrainingUseCase(target);
                require("PARTIAL".equals(partial.status())&&"FAILED".equals(stage(partial,"heatTraining").status())&&!Boolean.TRUE.equals(partial.response().get("heatModelTrained")),"model_save_dependency_partial_clustering_retained");
                require(stage(partial,"heatTraining").failureCause().getCause()!=null,"model_save_failure_cause_retained");
            } finally { props.getXgboost().setModelDir(modelDir); }
            require(version.equals(store.metadata().get("modelVersion")),"failed_model_save_preserves_previous_published_version");
            // Real Redis connection failure is isolated to this bean; host Redis remains running.
            var originalFactory=redis.getConnectionFactory();
            var failingFactory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",1),
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).clientOptions(
                            io.lettuce.core.ClientOptions.builder().socketOptions(io.lettuce.core.SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build()).build()).build());
            failingFactory.afterPropertiesSet();failingFactory.start();redis.setConnectionFactory(failingFactory);
            try {
                var partial=analysis.runPredictionUseCase(target);
                require("PARTIAL".equals(partial.status())&&"COMPLETE".equals(stage(partial,"prediction").status())&&"FAILED".equals(stage(partial,"predictionCache").status()),"redis_failure_does_not_hide_committed_predictions");
            } finally { redis.setConnectionFactory(originalFactory);failingFactory.destroy(); }
            double[][] points={{0,0},{0.1,0.2},{0.2,0.1},{10,10},{10.1,10.2},{10.2,10.1}};
            var smile=KMeans.fit(points,2);
            require(smile.predict(new double[]{0,0})!=smile.predict(new double[]{10,10}),"real_smile_runtime_available_without_replacing_analysis_algorithm");
            business=true;
            cleanup=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME LIKE 'i17_%'",Integer.class)==0;
            require(cleanup,"owned_failure_triggers_removed_configuration_restored");
            evidence.put("retainedSyntheticData",true);evidence.put("noOriginalDataOrExternalCalls",true);
        } catch(Throwable ex) {
            business=false; cleanup=false;
            failure=ex.getClass().getName();
            String label=ex instanceof AssertionError ? String.valueOf(ex.getMessage()) : "dependency/startup failure";
            System.out.println("ISSUE17 FAIL "+label+" class="+failure);
            for(var frame:ex.getStackTrace())if(frame.getClassName().equals("Issue17Probe")){System.out.println("ISSUE17 LOCATION "+frame.getMethodName()+":"+frame.getLineNumber());break;}
        } finally {
            evidence.put("businessPassed",business);evidence.put("cleanupPassed",cleanup);evidence.put("failureClass",failure);evidence.put("assertions",passed);
            Files.writeString(out.resolve("scenario-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
            System.out.println("ISSUE17 RESULT businessPassed="+business+" cleanupPassed="+cleanup+" target="+target+" syntheticEffectivenessOnly=true");
        }
        if(!business||!cleanup)System.exit(1);
    }
}
