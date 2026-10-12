import com.codeying.App;
import com.codeying.entity.*;
import com.codeying.mapper.*;
import com.codeying.service.*;
import com.codeying.properties.AliOssProperties;
import com.codeying.dto.admin.common.UploadFileDTO;
import com.codeying.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import javax.imageio.ImageIO;

/** Real SDK + protected HTTP + MyBatis/MySQL probe. Everything except task-owned SQL/Redis is loopback. */
public class Issue20Probe {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String BASE = "http://127.0.0.1:18090";
    static final String RUN = "i20_" + UUID.randomUUID().toString().replace("-", "").substring(0,8);
    static final List<String> results = new ArrayList<>();
    static final Map<String,List<Long>> owned = new LinkedHashMap<>();
    static final List<String> cleanupFailures = new ArrayList<>();
    static JdbcTemplate jdbc;
    static String admin, userA, userB;
    static long dish, meal, category, mealCategory, order;
    static boolean retainHistory;
    static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        results.add(label); System.out.println("ISSUE20 PASS " + label);
    }
    static void track(String table, long id) { owned.computeIfAbsent(table,k->new ArrayList<>()).add(id); }
    @Configuration(proxyBeanMethods=false)
    public static class ProbeConfig {
        @Bean @Primary WechatService localWechat() {
            return code -> switch (code) {
                case "issue20-a" -> "sandbox-user-a";
                case "issue20-b" -> "sandbox-user-b";
                default -> throw new IllegalArgumentException("local probe code only");
            };
        }
    }
    static class Storage implements AutoCloseable {
        final HttpServer server;
        final NavigableMap<String,byte[]> objects = Collections.synchronizedNavigableMap(new TreeMap<>());
        volatile boolean failUpload, failList;
        int puts, lists, markerPages;
        final byte[] png;
        Storage() throws Exception {
            var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
            image.setRGB(0,0,0x228844); var bytes=new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes); png=bytes.toByteArray();
            for(String scene:List.of("dish","setmeal","general")) {
                objects.put("issue20/"+scene+"/",new byte[0]); objects.put("issue20/"+scene+"/a.txt",new byte[]{1});
                objects.put("issue20/"+scene+"/b.bmp",png); objects.put("issue20/"+scene+"/c image.png",png);
                objects.put("issue20/"+scene+"/d.JPG",png); objects.put("issue20/"+scene+"/e.webp",png);
            }
            objects.put("issue20/historical.png",png);
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",18082),0);
            server.createContext("/",this::handle); server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0),"issue20-local-storage-stop"));
        }
        static String xml(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
        void handle(HttpExchange ex) throws IOException {
            try {
                String path=ex.getRequestURI().getPath();
                if (!path.startsWith("/sandbox-only")) { send(ex,404,"text/plain",new byte[0]); return; }
                String key=path.length()>14 ? path.substring(14) : "";
                if (key.startsWith("/")) key=key.substring(1);
                if ("PUT".equals(ex.getRequestMethod())) {
                    puts++;
                    if (failUpload) { error(ex); return; }
                    byte[] bytes=ex.getRequestBody().readAllBytes(); objects.put(key,bytes);
                    try { ex.getResponseHeaders().set("ETag","\""+HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes))+"\""); }
                    catch (Exception ignored) { throw new IOException("fixture digest failure"); }
                    send(ex,200,"application/xml",new byte[0]); return;
                }
                if (key.isEmpty()) {
                    lists++; if(failList) { error(ex); return; }
                    Map<String,String> params=new HashMap<>();
                    String query=ex.getRequestURI().getRawQuery();
                    if(query!=null) for(String entry:query.split("&")) {
                        String[] parts=entry.split("=",2); params.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"");
                    }
                    String prefix=params.getOrDefault("prefix",""); String marker=params.getOrDefault("marker","");
                    if(!marker.isEmpty()) markerPages++;
                    int max=Integer.parseInt(params.getOrDefault("max-keys","1000"));
                    List<String> keys;
                    synchronized(objects) { keys=objects.keySet().stream().filter(k->k.startsWith(prefix)&&k.compareTo(marker)>0).toList(); }
                    List<String> page=keys.subList(0,Math.min(max,keys.size())); boolean more=keys.size()>page.size();
                    var body=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult><Name>sandbox-only</Name><Prefix>"+xml(prefix)+"</Prefix><Marker>"+xml(marker)+"</Marker><MaxKeys>"+max+"</MaxKeys><IsTruncated>"+more+"</IsTruncated>");
                    if(more) body.append("<NextMarker>").append(xml(page.get(page.size()-1))).append("</NextMarker>");
                    for(String k:page) body.append("<Contents><Key>").append(xml(k)).append("</Key><LastModified>2026-10-12T00:00:00.000Z</LastModified><ETag>fixture</ETag><Size>").append(objects.get(k).length).append("</Size><StorageClass>Standard</StorageClass><Owner><ID>fixture-owner</ID><DisplayName>local-fixture</DisplayName></Owner></Contents>");
                    body.append("</ListBucketResult>"); send(ex,200,"application/xml",body.toString().getBytes(StandardCharsets.UTF_8)); return;
                }
                byte[] bytes=objects.get(key); send(ex,bytes==null?404:200,"image/png",bytes==null?new byte[0]:bytes);
            } finally { ex.close(); }
        }
        void error(HttpExchange ex) throws IOException {
            send(ex,403,"application/xml",("<?xml version=\"1.0\"?><Error><Code>AccessDenied</Code><Message>synthetic-third-party-body Signature=synthetic-only</Message><RequestId>local-fixture</RequestId><HostId>local</HostId></Error>").getBytes(StandardCharsets.UTF_8));
        }
        void send(HttpExchange ex,int status,String type,byte[] bytes) throws IOException {
            ex.getResponseHeaders().set("Content-Type",type); ex.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);
            if(bytes.length>0) ex.getResponseBody().write(bytes);
        }
        @Override public void close() { server.stop(0); }
    }
    static HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(15));
        if(token!=null) request.header(path.startsWith("/admin/")?"token":"authentication",token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    static JsonNode data(HttpResponse<String> response,String label) throws Exception {
        require(response.statusCode()==200,label+" transport"); var json=JSON.readTree(response.body());
        require(json.path("code").asInt(-9)==1,label+" business success"); return json.path("data");
    }
    static void denied(HttpResponse<String> response,String label) throws Exception {
        require(response.statusCode()==401,label+" HTTP401"); require(JSON.readTree(response.body()).path("code").asInt(-9)==0,label+" business refusal");
    }
    static void refused(HttpResponse<String> response,String message,String label) throws Exception {
        require(response.statusCode()==200,label+" transport"); var json=JSON.readTree(response.body());
        require(json.path("code").asInt(-9)==0,label+" business refusal");
        if(message!=null) require(json.path("msg").asText().equals(message),label+" friendly message");
        require(!response.body().contains("synthetic-third-party-body")&&!response.body().contains("Signature=")&&!response.body().contains("AccessDenied"),label+" sanitized");
    }
    static HttpResponse<String> upload(String token,String filename,byte[] bytes,Map<String,String> params) throws Exception {
        String boundary="issue20Boundary"; var body=new ByteArrayOutputStream();
        for(var item:params.entrySet()) body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+item.getKey()+"\"\r\n\r\n"+item.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        if(filename!=null) {
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8)); body.write(bytes); body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create(BASE+"/admin/common/upload")).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(token!=null) request.header("token",token);
        return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    static void readable(String url,byte[] expected,String label) throws Exception {
        require(url.startsWith("http://127.0.0.1:18082/sandbox-only/"),label+" loopback URL");
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        require(response.statusCode()==200 && Arrays.equals(expected,response.body()),label+" readable exact bytes");
        require(ImageIO.read(new ByteArrayInputStream(response.body()))!=null,label+" valid image");
    }
    static List<Map<String,Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"); }
    static void cleanup(Runnable action,String label) { try { action.run(); require(true,label); } catch(Throwable failure) { cleanupFailures.add(label+":"+failure.getClass().getSimpleName()); } }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(); Path out=root.resolve(".scratch/issue20"); Files.createDirectories(out);
        Path runtime=out.resolve(RUN); Files.createDirectories(runtime); System.setProperty("java.io.tmpdir",runtime.toString());
        Class.forName("com.mysql.cj.jdbc.Driver"); require(true,"actual packaged JDBC driver present");
        boolean business=false,closed=false,isolated=false; String failure="";
        ConfigurableApplicationContext context=null; Storage storage=null; StringRedisTemplate redis=null;
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>(); List<Map<String,Object>> oldDetails=null;
        var logs=new ListAppender<ILoggingEvent>(); logs.start(); List<Logger> observed=new ArrayList<>();
        try {
            storage=new Storage();
            context=new SpringApplication(App.class,ProbeConfig.class).run(
                "--spring.config.location=file:"+root.resolve(".sandcastle/environment/application-afk.yml"),
                "--spring.profiles.active=dev,afk","--server.address=127.0.0.1","--server.port=18090","--server.tomcat.basedir="+runtime.resolve("tomcat"),"--logging.level.root=OFF",
                "--spring.servlet.multipart.max-file-size=24MB","--spring.servlet.multipart.max-request-size=25MB",
                "--sky.alioss.dish-default-image-prefix= /issue20/dish ","--sky.alioss.setmeal-default-image-prefix=/issue20/setmeal","--sky.alioss.default-image-prefix=issue20/general","--sky.alioss.max-list-count=3",
                "--analysis.task.enabled=false","--order-risk.task.scoring-enabled=false","--analysis.xgboost.model-dir="+runtime.resolve("analysis-models"),"--order-risk.model-dir="+runtime.resolve("order-risk-models"));
            for(String name:List.of("com.codeying.service.impl.UploadServiceImpl","com.codeying.service.impl.DefaultImageLibraryServiceImpl","com.aliyun.oss")) {
                var logger=(Logger)LoggerFactory.getLogger(name); logger.setLevel(name.equals("com.aliyun.oss")?Level.WARN:Level.INFO); logger.setAdditive(false); logger.addAppender(logs); observed.add(logger);
            }
            jdbc=context.getBean(JdbcTemplate.class); redis=context.getBean(StringRedisTemplate.class);
            require("sandcastle_fixture".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"isolated database before writes");
            try(var connection=jdbc.getDataSource().getConnection()) { require(connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/sandcastle_fixture"),"isolated JDBC host before writes"); }
            require("redis".equals(context.getEnvironment().getProperty("spring.data.redis.host"))&&"6379".equals(context.getEnvironment().getProperty("spring.data.redis.port")),"isolated Redis before writes");
            for(String table:List.of("employee","user","category","dish","dish_flavor","setmeal","setmeal_dish","orders","order_detail","address_book")) {
                var ddl=jdbc.queryForMap("SHOW CREATE TABLE "+table).values().stream().map(Object::toString).filter(v->v.contains("CREATE TABLE")).findFirst().orElseThrow();
                require(ddl.contains("ENGINE=InnoDB")&&ddl.contains("PRIMARY KEY (`id`)"),"actual "+table+" schema engine and primary key");
                if(List.of("dish","setmeal").contains(table)) require(ddl.contains("`image` varchar(255)")&&ddl.contains("decimal(10,2)")&&ddl.contains("UNIQUE KEY"),"actual "+table+" image amount and uniqueness constraints");
                if(table.equals("order_detail")) require(ddl.contains("`order_id` bigint NOT NULL")&&ddl.contains("`image` varchar(255)")&&ddl.contains("`amount` decimal(10,2) NOT NULL"),"actual historical-detail constraints");
                before.put(table,rows(table));
            }
            isolated=true;
            var adminLogin=data(call("POST","/admin/employee/login",null,Map.of("username","sandbox_admin","password","sandbox-only-login")),"real fixture admin login"); admin=adminLogin.path("token").asText(); require(adminLogin.path("id").asLong()==900001,"actual admin fixture ID");
            var loginA=data(call("POST","/user/user/login",null,Map.of("code","issue20-a")),"real fixture user A login"); userA=loginA.path("token").asText();
            var loginB=data(call("POST","/user/user/login",null,Map.of("code","issue20-b")),"real fixture user B login"); userB=loginB.path("token").asText();
            require(loginA.path("id").asLong()==900001&&loginB.path("id").asLong()==900002&&!userA.equals(userB),"two actual distinct fixture users");
            redis.opsForValue().set("afk:issue20:"+RUN,"local"); require("local".equals(redis.opsForValue().get("afk:issue20:"+RUN)),"task-owned Redis roundtrip");
            denied(call("GET","/admin/common/default-images?scene=dish",null,null),"anonymous library"); denied(call("GET","/admin/common/default-images?scene=dish",userA,null),"wrong scope library");
            denied(upload(null,"x.png",storage.png,Map.of()),"anonymous upload"); denied(upload(userA,"x.png",storage.png,Map.of()),"wrong scope upload"); require(storage.puts==0&&storage.lists==0,"refusals do not reach storage");
            refused(upload(admin,null,new byte[0],Map.of("scene","dish")),"文件不能为空","missing multipart file");
            refused(upload(admin,"x.png",new byte[0],Map.of()),"文件为空","empty file");
            refused(upload(admin," ",new byte[]{1},Map.of()),"文件名为空","blank filename");
            for(String filename:List.of("x","x.","x.gif","x.webp")) refused(upload(admin,filename,storage.png,Map.of()),"文件格式错误","format refusal "+filename);
            refused(upload(admin,"x.png",new byte[5*1024*1024+1],Map.of()),"文件过大","default 5MiB exceeded");
            refused(upload(admin,"x.png",storage.png,Map.of("maxSize","1")),"文件过大","requested limit exceeded");
            refused(upload(admin,"x.png",new byte[20*1024*1024+1],Map.of("maxSize","999999999")),"文件过大","hard 20MiB exceeded");
            require(storage.puts==0,"validation refusals do not upload");
            String uploaded="";
            for(String ext:List.of("png","jpg","jpeg","bmp")) {
                String url=data(upload(admin,"photo."+ext,storage.png,Map.of("scene","dish","dir","/fixture/../month")),"real SDK upload "+ext).asText();
                require(url.contains("/dish/fixture/month/photo_"),"existing scene/dir/name rules "+ext); readable(url,storage.png,"uploaded "+ext); if(ext.equals("png")) uploaded=url;
            }
            String special=data(upload(admin,"photo #.png",storage.png,Map.of()),"real SDK special filename").asText(); readable(special,storage.png,"encoded filename");
            var props=context.getBean(AliOssProperties.class);
            for(String scene:List.of("dish","setmeal","general","unknown")) {
                var images=data(call("GET","/admin/common/default-images?scene="+scene+"&limit=99",admin,null),"library scene "+scene);
                require(images.size()==3,"filtered configured return limit "+scene);
                for(var image:images) { require(!image.path("objectKey").asText().endsWith(".bmp")&&!image.path("objectKey").asText().endsWith(".txt"),"configured extensions preserved "+scene); readable(image.path("url").asText(),storage.png,"library image "+scene); }
            }
            require(storage.markerPages>0,"real SDK marker pagination past non-images");
            require(data(call("GET","/admin/common/default-images?scene=dish&limit=1",admin,null),"requested library limit").size()==1,"requested limit one");
            var dishImages=data(call("GET","/admin/common/default-images?scene=dish",admin,null),"default library limit"); require(dishImages.size()==3,"default configured limit three");
            props.setSetmealDefaultImagePrefix("issue20/empty"); require(data(call("GET","/admin/common/default-images?scene=setmeal",admin,null),"empty library").isEmpty(),"empty library remains success"); props.setSetmealDefaultImagePrefix("issue20/setmeal");
            refused(call("GET","/admin/common/default-images?scene=dish&limit=0",admin,null),"limit 必须大于 0","invalid library limit");
            storage.failUpload=true; refused(upload(admin,"x.png",storage.png,Map.of()),"上传失败","real SDK storage failure");
            var uploadDto=new UploadFileDTO();
            // A real MultipartFile implementation, without test-classpath dependencies.
            byte[] png=storage.png;
            uploadDto.setFile(new org.springframework.web.multipart.MultipartFile() {
                public String getName(){return "file";} public String getOriginalFilename(){return "x.png";} public String getContentType(){return "image/png";}
                public boolean isEmpty(){return false;} public long getSize(){return png.length;} public byte[] getBytes(){return png;}
                public InputStream getInputStream(){return new ByteArrayInputStream(png);} public void transferTo(java.io.File file) throws IOException {Files.write(file.toPath(),png);}
            });
            try { context.getBean(UploadService.class).upload(uploadDto); throw new AssertionError("expected upload cause"); }
            catch(BusinessException ex) { require(ex.getCause()!=null&&ex.getCause().getCause() instanceof com.aliyun.oss.OSSException,"real SDK upload cause retained"); }
            storage.failUpload=false; storage.failList=true;
            refused(call("GET","/admin/common/default-images?scene=dish",admin,null),"获取默认图片失败","real SDK list failure");
            try { context.getBean(DefaultImageLibraryService.class).listDefaultImages("dish",1); throw new AssertionError("expected list cause"); }
            catch(BusinessException ex) { require(ex.getCause()!=null&&ex.getCause().getCause() instanceof com.aliyun.oss.OSSException,"real SDK list cause retained"); }
            storage.failList=false;
            require(logs.list.stream().allMatch(event->event.getThrowableProxy()==null&&!event.getFormattedMessage().contains("synthetic-third-party-body")&&!event.getFormattedMessage().contains("Signature=")&&!event.getFormattedMessage().contains("photo")),"application logs conceal upstream body signed query and filename");
            var c=new Category(); c.setName(RUN+"cat");c.setType(1);c.setSort(0);c.setStatus(1);c.setCreateUser(900001L);c.setUpdateUser(900001L);c.setCreateTime(new Date());c.setUpdateTime(new Date());context.getBean(CategoryMapper.class).insert(c);category=c.getId();track("category",category);
            var mc=new Category();mc.setName(RUN+"mealcat");mc.setType(2);mc.setSort(0);mc.setStatus(1);mc.setCreateUser(900001L);mc.setUpdateUser(900001L);mc.setCreateTime(new Date());mc.setUpdateTime(new Date());context.getBean(CategoryMapper.class).insert(mc);mealCategory=mc.getId();track("category",mealCategory);
            data(call("POST","/admin/dish",admin,Map.of("name",RUN+"dish","categoryId",category,"price",18,"image",uploaded,"status",1,"flavors",List.of(Map.of("name","taste","value","plain")))),"upload-to-dish create");
            dish=jdbc.queryForObject("SELECT id FROM dish WHERE name=?",Long.class,RUN+"dish");track("dish",dish);
            require(uploaded.equals(jdbc.queryForObject("SELECT image FROM dish WHERE id=?",String.class,dish)),"real SQL persisted uploaded dish URL");
            require(data(call("GET","/admin/dish/"+dish,admin,null),"dish load").path("image").asText().equals(uploaded),"dish load exact upload URL");
            String selected=dishImages.get(0).path("url").asText();
            data(call("POST","/admin/setmeal",admin,Map.of("name",RUN+"meal","categoryId",mealCategory,"price",19,"image",selected,"status",1,"setmealDishes",List.of(Map.of("dishId",dish,"copies",1)))),"select-to-setmeal create");
            meal=jdbc.queryForObject("SELECT id FROM setmeal WHERE name=?",Long.class,RUN+"meal");track("setmeal",meal);
            require(selected.equals(jdbc.queryForObject("SELECT image FROM setmeal WHERE id=?",String.class,meal)),"real SQL persisted selected meal URL");
            require(data(call("GET","/admin/setmeal/"+meal,admin,null),"meal load").path("image").asText().equals(selected),"meal load exact selected URL");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM dish_flavor WHERE dish_id=?",Integer.class,dish)==1&&jdbc.queryForObject("SELECT COUNT(*) FROM setmeal_dish WHERE setmeal_id=? AND dish_id=?",Integer.class,meal,dish)==1,"real child rows persisted via named Mapper save paths");
            var historical=new Orders();historical.setNumber(RUN+"history");historical.setUserId(900001L);historical.setAddressBookId(900001L);historical.setStatus(5);historical.setOrderTime(new Date());historical.setPayMethod(1);historical.setPayStatus(1);historical.setAmount(new BigDecimal("37.00"));historical.setDeliveryStatus(1);historical.setTablewareStatus(1);context.getBean(OrdersMapper.class).insert(historical);order=historical.getId();track("orders",order);
            String historicalUrl="http://127.0.0.1:18082/sandbox-only/issue20/historical.png";
            for(boolean isDish:List.of(true,false)) {var h=new OrderDetail();h.setOrderId(order);h.setName(isDish?"historical-dish":"historical-meal");h.setImage(historicalUrl);h.setNumber(1);h.setAmount(new BigDecimal(isDish?"18.00":"19.00"));if(isDish)h.setDishId(dish);else h.setSetmealId(meal);context.getBean(OrderDetailMapper.class).insert(h);track("order_detail",h.getId());}
            var oldOrder=jdbc.queryForList("SELECT * FROM orders WHERE id=?",order);
            oldDetails=jdbc.queryForList("SELECT * FROM order_detail WHERE order_id=? ORDER BY id",order);
            data(call("PUT","/admin/dish",admin,Map.of("id",dish,"image",selected,"flavors",List.of(Map.of("name","taste","value","new")))),"select-to-dish update");
            data(call("PUT","/admin/setmeal",admin,Map.of("id",meal,"image",uploaded,"setmealDishes",List.of(Map.of("dishId",dish,"copies",2)))),"upload-to-meal update");
            require(selected.equals(jdbc.queryForObject("SELECT image FROM dish WHERE id=?",String.class,dish))&&uploaded.equals(jdbc.queryForObject("SELECT image FROM setmeal WHERE id=?",String.class,meal)),"real SQL both update selection paths");
            for(String path:List.of("/admin/dish/"+dish,"/admin/setmeal/"+meal)) readable(data(call("GET",path,admin,null),"updated product load").path("image").asText(),storage.png,"saved product URL");
            var userDishes=data(call("GET","/user/dish/list?categoryId="+category,userA,null),"miniapp current dish API");require(userDishes.size()==1&&selected.equals(userDishes.get(0).path("image").asText()),"miniapp receives saved current image");
            var userMeals=data(call("GET","/user/setmeal/list?categoryId="+mealCategory,userA,null),"miniapp current meal API");require(userMeals.size()==1&&uploaded.equals(userMeals.get(0).path("image").asText()),"miniapp receives saved meal image");
            var own=data(call("GET","/user/order/orderDetail/"+order,userA,null),"own historical order");require(own.path("orderDetailList").size()==2,"historical two product details");for(var item:own.path("orderDetailList"))require(historicalUrl.equals(item.path("image").asText()),"historical URL retained on HTTP");
            refused(call("GET","/user/order/orderDetail/"+order,userB,null),null,"other owner historical refusal");
            require(oldDetails.equals(jdbc.queryForList("SELECT * FROM order_detail WHERE order_id=? ORDER BY id",order)),"historical SQL rows unchanged after current product saves and reads");readable(historicalUrl,storage.png,"retained historical fixture");
            for(String table:List.of("dish_flavor","setmeal_dish")) {
                String fk=table.equals("dish_flavor")?"dish_id":"setmeal_id";long parent=table.equals("dish_flavor")?dish:meal;
                for(long id:jdbc.queryForList("SELECT id FROM "+table+" WHERE "+fk+"=?",Long.class,parent))track(table,id);
            }
            require(oldOrder.equals(jdbc.queryForList("SELECT * FROM orders WHERE id=?",order)),"historical order SQL row unchanged");
            retainHistory=true; business=true;
        } catch(Throwable ex) { failure=ex.getClass().getSimpleName(); System.out.println("ISSUE20 FAIL "+failure+(ex instanceof AssertionError ? " "+ex.getMessage() : "")); }
        finally {
            if(isolated&&jdbc!=null) {
                // Keep successful synthetic products/history for review. On failure remove only tracked task rows.
                if(!retainHistory) {
                    cleanup(()->{if(order!=0)jdbc.update("DELETE FROM order_detail WHERE order_id=?",order);},"failed owned details cleanup");
                    cleanup(()->{if(order!=0)jdbc.update("DELETE FROM orders WHERE id=?",order);},"failed owned order cleanup");
                    cleanup(()->{if(meal!=0){jdbc.update("DELETE FROM setmeal_dish WHERE setmeal_id=?",meal);jdbc.update("DELETE FROM setmeal WHERE id=?",meal);}},"failed owned meal cleanup");
                    cleanup(()->{if(dish!=0){jdbc.update("DELETE FROM dish_flavor WHERE dish_id=?",dish);jdbc.update("DELETE FROM dish WHERE id=?",dish);}},"failed owned dish cleanup");
                    cleanup(()->{if(category!=0)jdbc.update("DELETE FROM category WHERE id=?",category);if(mealCategory!=0)jdbc.update("DELETE FROM category WHERE id=?",mealCategory);},"failed owned category cleanup");
                }
                for(var entry:before.entrySet()) cleanup(()->{
                    var current=rows(entry.getKey());var taskIds=owned.getOrDefault(entry.getKey(),List.of());
                    if(entry.getKey().equals("dish_flavor"))current.removeIf(row->dish!=0&&Objects.equals(((Number)row.get("dish_id")).longValue(),dish));
                    else if(entry.getKey().equals("setmeal_dish"))current.removeIf(row->meal!=0&&Objects.equals(((Number)row.get("setmeal_id")).longValue(),meal));
                    else current.removeIf(row->taskIds.contains(((Number)row.get("id")).longValue()));
                    if(!entry.getValue().equals(current))throw new AssertionError("shared rows changed");
                },"shared "+entry.getKey()+" rows unchanged");
            }
            if(redis!=null&&isolated) { final var ownRedis=redis;cleanup(()->{ownRedis.delete("afk:issue20:"+RUN);if(Boolean.TRUE.equals(ownRedis.hasKey("afk:issue20:"+RUN)))throw new AssertionError("owned key remains");},"owned Redis key stopped"); }
            for(var logger:observed) { logger.detachAppender(logs);logger.setLevel(null);logger.setAdditive(true); } logs.stop();
            if(context!=null) { try { context.close();require(!context.isActive(),"Spring context stopped"); }catch(Throwable ex){cleanupFailures.add("Spring close:"+ex.getClass().getSimpleName());} }
            if(storage!=null) {storage.close();require(true,"local OSS server stopped");}
            HTTP.close(); closed=cleanupFailures.isEmpty();
            JSON.writeValue(out.resolve("owned-resources.json").toFile(),Map.of("run",RUN,"syntheticRows",owned,"retainedHistorical",retainHistory,"serversStopped",true,"contextStopped",context==null||!context.isActive(),"redisKeyRemoved",results.contains("owned Redis key stopped")));
            JSON.writeValue(out.resolve("scenario-results.json").toFile(),Map.of("businessPassed",business,"cleanupPassed",closed,"failureClass",failure,"scenarios",results,"cleanupFailures",cleanupFailures));
        }
        if(!business||!closed)throw new AssertionError("Issue20 incomplete; sanitized scenario evidence retained");
        System.out.println("ISSUE20 ACCEPTANCE PASS");
    }
}
