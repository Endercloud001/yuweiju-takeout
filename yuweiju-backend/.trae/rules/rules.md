# Java 后端开发规范手册

> 本规范结合阿里巴巴 Java 开发手册、Google Java Style Guide 及 实际项目实践整理而成，适用于 Spring Boot 多模块 Maven 后端工程。**强制**条目必须遵守，**推荐**条目应尽量遵守，**参考**条目供开发者自行权衡。

------



------

## 一、项目结构规范

### 1.1 多模块划分原则

【强制】Spring Boot 多模块项目按**职责**划分模块，避免循环依赖。推荐以下三层模块结构：

```
yuweiju-take-out/
├── yuweiju-common/        # 公共层：常量、枚举、工具类、异常、通用结果封装
├── yuweiju-pojo/          # 数据对象层：Entity / DTO / VO，不包含任何业务逻辑
└── yuweiju-server/        # 业务核心层：Controller / Service / Mapper / 配置 / 切面 / 拦截器
```

**依赖关系**：`yuweiju-server` → `yuweiju-pojo` → `yuweiju-common`，禁止反向依赖。

### 1.2 包结构规范

【强制】`yuweiju-server` 内部包按功能分层，包名全部小写：

```
com.yuweiju/
├── annotation/        # 自定义注解（如 @AutoFill）
├── aspect/            # AOP 切面（如 AutoFillAspect）
├── config/            # Spring 配置类（如 WebMvcConfiguration、RedisConfiguration）
├── constant/          # 业务常量（按模块拆分，禁止大杂烩）
├── context/           # 线程上下文工具（如 BaseContext）
├── controller/
│   ├── admin/         # 管理端接口
│   ├── user/          # 用户端接口
│   └── notify/        # 第三方回调通知接口
├── enumeration/       # 枚举类
├── exception/         # 自定义异常
├── handler/           # 全局异常处理器
├── interceptor/       # 拦截器
├── mapper/            # MyBatis Mapper 接口
├── properties/        # 配置属性映射类（@ConfigurationProperties）
├── service/
│   └── impl/          # Service 接口与实现类
├── task/              # 定时任务
├── utils/             # 工具类
└── websocket/         # WebSocket 服务
```

【推荐】`yuweiju-pojo` 内部包按对象类型分层：

```
com.yuweiju/
├── dto/               # Data Transfer Object：前端请求参数封装
├── entity/            # 数据库实体，与表结构一一对应
└── vo/                # View Object：返回前端的视图对象
```

【推荐】`yuweiju-common` 内部包按职责分层：

```
com.yuweiju/
├── constant/          # 全局常量（如 MessageConstant、JwtClaimsConstant）
├── context/           # 线程上下文
├── enumeration/       # 枚举（如 OperationType）
├── exception/         # 自定义异常基类与子类
├── json/              # JSON 序列化定制（如 JacksonObjectMapper）
├── properties/        # 外部化配置映射
├── result/            # 统一响应结构（Result、PageResult）
└── utils/             # 通用工具类（JwtUtil、AliOssUtil 等）
```

------

## 二、命名风格

### 2.1 通用规则

| 元素                                  | 风格                                                    | 示例                                 |
| ------------------------------------- | ------------------------------------------------------- | ------------------------------------ |
| 类名                                  | UpperCamelCase（以下情形例外：DO / BO / DTO / VO / AO） | `OrderServiceImpl`、`UserLoginDTO`   |
| 方法名 / 参数名 / 成员变量 / 局部变量 | lowerCamelCase                                          | `ordersSubmitDTO`、`getCurrentId()`  |
| 常量                                  | 全大写 + 下划线分隔                                     | `MAX_STOCK_COUNT`、`PENDING_PAYMENT` |
| 包名                                  | 全小写，点分隔                                          | `com.yuweiju.service.impl`           |
| 数据库表名 / 字段名                   | 全小写 + 下划线分隔（snake_case）                       | `order_detail`、`pay_status`         |

【强制】抽象类命名以 `Abstract` 或 `Base` 开头；异常类命名以 `Exception` 结尾；测试类命名以被测类名开头、以 `Test` 结尾。

正例：`BaseException` / `OrderBusinessException` / `DishServiceTest`

【强制】Entity 类中布尔类型字段**不要加 `is` 前缀**，否则部分框架（如 MyBatis、Jackson）在序列化/反序列化时会找不到对应属性。

反例：`Boolean isDeleted` → 正例：`Boolean deleted`

【强制】Service 与 DAO 类对外暴露**接口**，实现类使用 `Impl` 后缀。

正例：`OrderService` 接口 + `OrderServiceImpl` 实现类

### 2.2 各层命名规约

**Controller 层**

| 场景           | 示例                                                         |
| -------------- | ------------------------------------------------------------ |
| 同一资源区分端 | `admin/OrderController`（管理端）、`user/OrderController`（用户端） |
| 接口方法名     | 见下表                                                       |

**Service / DAO 层方法命名**

| 操作         | 前缀                | 示例                              |
| ------------ | ------------------- | --------------------------------- |
| 查询单个对象 | `get`               | `getById`、`getByNumberAndUserId` |
| 查询多个对象 | `list`              | `listByCategoryId`                |
| 分页查询     | `pageQuery`         | `pageQuery(DishPageQueryDTO dto)` |
| 统计数量     | `count`             | `countByStatus`                   |
| 统计汇总值   | `sum`               | `sumByMap`                        |
| 新增         | `save` / `insert`   | `saveWithFlavor`、`insertBatch`   |
| 删除         | `remove` / `delete` | `removeById`、`deleteBatch`       |
| 修改         | `update`            | `updateStatus`、`update`          |

**DTO / VO / Entity 命名**

| 类型   | 命名规则             | 示例                                   |
| ------ | -------------------- | -------------------------------------- |
| Entity | 与表名对应，单数形式 | `Orders`、`DishFlavor`、`ShoppingCart` |
| DTO    | 功能描述 + `DTO`     | `OrdersSubmitDTO`、`DishPageQueryDTO`  |
| VO     | 功能描述 + `VO`      | `OrderSubmitVO`、`DishVO`              |

> **说明**：`Orders` 表名复数是历史遗留，新建 Entity 建议使用单数，如 `Order`。

**枚举类**

【参考】枚举类名建议带 `Enum` 后缀，成员名称全大写 + 下划线分隔。

正例：`OperationType.INSERT` / `ProcessStatusEnum.SUCCESS`

**常量类**

【推荐】按功能模块拆分常量类，禁止把所有常量堆入一个大类。

正例（参考 yuweiju-take-out）：

```
MessageConstant    // 业务提示消息
JwtClaimsConstant  // JWT Payload 字段名
AutoFillConstant   // 自动填充字段方法名
StatusConstant     // 通用启用/禁用状态
PasswordConstant   // 密码相关默认值
```

------

## 三、注释规范

### 3.1 类注释

【强制】所有类（包括接口、枚举、注解）必须在类声明前添加 Javadoc，包含：**功能描述**、**创建者**。

```java
/**
 * 订单业务处理定时任务。
 * <p>
 * 负责处理支付超时订单（每分钟扫描一次）以及派送超时订单（每日凌晨 1 点扫描一次）。
 * </p>
 *
 * @author yourName
 */
@Component
@Slf4j
public class OrderTask {
    // ...
}
```

### 3.2 方法注释

【强制】所有接口方法（包括 Service 接口、Mapper 接口、Controller 公开方法）必须使用 Javadoc，至少包含：**功能说明**、`@param`（每个参数）、`@return`（有返回值时）、`@throws`（明确抛出的检查异常）。

```java
/**
 * 用户提交订单。
 * <p>
 * 流程：校验地址 → 计算骑手配送距离（百度地图 API，超 50 km 拒绝下单）
 * → 生成订单及明细 → 清空购物车。
 * </p>
 *
 * @param ordersSubmitDTO 下单请求参数，包含地址 ID、备注、餐具数量等
 * @return 下单结果 VO，包含订单号、金额、预计送达时间
 * @throws AddressBookBusinessException 收货地址不存在时抛出
 * @throws OrderBusinessException       购物车为空或超出配送范围时抛出
 */
@Override
@Transactional
public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
    // ...
}
```

【强制】抽象方法必须说明**实现要求**或**调用注意事项**。

【推荐】实现类覆写方法如果逻辑与接口文档一致，可不重复 Javadoc，但必须加 `@Override`；若逻辑有特殊说明，应补充 `{@inheritDoc}` 或额外注释。

### 3.3 方法内部注释

【强制】方法体内单行注释：在**被注释语句上方另起一行**，使用 `//`，`//` 与注释内容之间有且仅有一个空格。

```java
// 登录成功后，生成 JWT 令牌并返回给前端
String token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
```

【强制】方法体内多行注释使用 `/* */`，与代码对齐；禁止在方法体内使用 Javadoc 风格（`/** */`）。

【推荐】对"为什么这样做"而非"做了什么"进行注释；代码本身能清晰表达的逻辑无需重复注释。

反例（废话注释）：

```java
// 将 status 设置为 CANCELLED
order.setStatus(Orders.CANCELLED);
```

正例（解释原因）：

```java
// 支付超时（下单后超过 15 分钟未付款），系统自动取消并释放库存
order.setStatus(Orders.CANCELLED);
```

### 3.4 Entity / DTO / VO 字段注释

【强制】Entity 类每个字段必须有注释，说明**业务含义**及**枚举值范围**（如有）。使用 Javadoc 字段注释（`/** */`）或行内 `//` 均可，但同一类中保持统一。

```java
/** 订单状态：1-待付款 2-待接单 3-已接单 4-派送中 5-已完成 6-已取消 */
private Integer status;

/** 支付方式：1-微信支付 2-支付宝 */
private Integer payMethod;

/** 实收金额（单位：元），精确到分 */
private BigDecimal amount;
```

### 3.5 常量注释

【强制】枚举类型的每个成员必须有 Javadoc 注释，说明含义。

```java
public enum OperationType {
    /** 数据库新增操作，自动填充 createTime / createUser / updateTime / updateUser */
    INSERT,
    /** 数据库更新操作，仅自动填充 updateTime / updateUser */
    UPDATE
}
```

### 3.6 TODO / FIXME

【参考】特殊标记必须注明**标记人**、**标记时间**、**预计处理时间**，并及时清理。

```java
// TODO(yourName, 2024-06-01): 当前用固定备餐时间 10 分钟，后续改为根据菜品数量动态计算
// FIXME(yourName, 2024-06-01): 微信支付沙箱环境偶发返回 null，需排查原因
```

------

## 四、变量与常量定义

【推荐】不要使用一个常量类维护所有常量，按功能归类分开维护（见第二章 2.2 节）。

【推荐】任何集合 / 数组的构造或初始化，都应**指定初始容量**，避免频繁扩容。

```java
// 正例：根据购物车数量预分配空间
List<OrderDetail> orderDetailList = new ArrayList<>(shoppingCartList.size());
Map<String, Object> params = new HashMap<>(4);
```

【推荐】有意义的布尔变量名应直接表达"是/否"语义，避免再加 `is` 前缀（见 Entity 布尔字段强制规定）。

------

## 五、代码格式

【强制】采用 **4 个空格**缩进，禁止使用 Tab 字符。Vue / 前端工程采用 2 个空格。

【强制】IDE 文件编码设置为 **UTF-8**；换行符使用 **Unix 格式（LF）**，禁止 Windows 格式（CRLF）。

【强制】大括号使用约定：

- 左大括号前不换行，左大括号后换行。
- 右大括号前换行，右大括号后若有 `else` 等则不换行。
- 空代码块简写为 `{}`，无需换行。

【强制】以下情况均需在左右两侧添加**一个空格**：

- 二目、三目运算符（`=`、`&&`、`+`、`? :` 等）。
- `if` / `for` / `while` / `switch` / `do` 等保留字与括号之间。
- 方法参数逗号后面。

```java
// 正例
if (status == Orders.PENDING_PAYMENT) {
    order.setStatus(Orders.CANCELLED);
}
method("a", "b", "c");
int result = a > b ? a : b;
```

【强制】左小括号与紧跟字符之间、右小括号与前面字符之间**不留空格**。

反例：`if ( a == b )` → 正例：`if (a == b)`

【强制】注释的双斜线与内容之间有且仅有**一个空格**：`// 注释内容`

【推荐】不同业务逻辑之间插入一个空行隔开，相同逻辑内部不插入多余空行。

【推荐】单个方法体不超过 **80 行**，超过时考虑拆分私有方法。

------

## 六、OOP 规约

【强制】所有覆写方法必须加 `@Override` 注解。

【强制】禁止使用已过时（`@Deprecated`）的类或方法。

【强制】`Object.equals()` 容易抛 NPE，应使用**常量或确定有值的对象**调用：

```java
// 正例
"admin".equals(employee.getUsername());
// 反例
employee.getUsername().equals("admin");
```

【强制】所有同类型包装类（`Integer`、`Long` 等）之间的值比较，统一使用 `equals()` 方法，不使用 `==`（`-128~127` 区间内 Integer 缓存复用，超出此范围 `==` 会误判）。

【强制】RPC 方法的返回值和参数必须使用**包装类型**（`Integer` 而非 `int`），避免因基本类型无法表示 `null` 而引发 NPE 或语义歧义。

【强制】构造方法内禁止加入任何业务逻辑，初始化逻辑请放入 `init()` 方法或 `@PostConstruct` 中。

【推荐】循环体内字符串拼接使用 `StringBuilder.append()`，禁止直接 `+` 拼接（每次循环都会创建新的 `StringBuilder` 对象，浪费内存）。

```java
// 正例
StringBuilder sb = new StringBuilder();
for (LocalDate date : dateList) {
    sb.append(date).append(",");
}
// 反例
String result = "";
for (LocalDate date : dateList) {
    result = result + date + ",";
}
```

【推荐】对于多个同名重载方法，应按**参数数量递增**顺序集中放置，便于阅读。

------

## 七、集合处理

【强制】集合转数组必须使用 `toArray(T[] array)`，传入与集合大小一致的类型数组：

```java
String[] array = list.toArray(new String[list.size()]);
```

禁止使用无参 `toArray()`，其返回值只能强转为 `Object[]`，转换其他类型会抛 `ClassCastException`。

【强制】`foreach` 循环中禁止对集合进行 `remove` / `add` 操作，应使用 `Iterator`：

```java
Iterator<Orders> iterator = orderList.iterator();
while (iterator.hasNext()) {
    Orders order = iterator.next();
    if (order.getStatus().equals(Orders.CANCELLED)) {
        iterator.remove();
    }
}
```

并发场景需对 `Iterator` 加锁，或使用 `CopyOnWriteArrayList`。

【推荐】遍历 `Map` 时优先使用 `entrySet()`，避免 `keySet()` + `get()` 造成的两次 Hash 查找：

```java
// 正例
for (Map.Entry<String, Object> entry : params.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

【推荐】返回空集合时使用 `Collections.emptyList()` 或 `new ArrayList<>(0)`，**禁止返回 `null`**（调用方需额外判空，易出 NPE）。

------

## 八、控制语句

【强制】`if` / `else` / `for` / `while` / `do` 语句块必须使用大括号，**即使只有一行**：

```java
// 正例
if (order == null) {
    throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
}
// 反例
if (order == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
```

【强制】`switch` 块中每个 `case` 必须通过 `break` / `return` 终止，或显式注释"fall-through"原因；必须包含 `default` 分支且放在最后。

【推荐】复杂条件判断应将结果赋值给**语义明确的布尔变量**，提升可读性：

```java
// 正例
boolean isDeliveryTimeout = order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)
        && order.getOrderTime().isBefore(LocalDateTime.now().minusHours(1));
if (isDeliveryTimeout) {
    // 处理派送超时
}
// 反例
if (order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)
        && order.getOrderTime().isBefore(LocalDateTime.now().minusHours(1))) {
    // ...
}
```

【推荐】循环体内避免以下操作（移至循环外）：对象创建、数据库连接获取、不必要的 `try-catch`。

【参考】以下情形需进行参数校验（防御性编程）：

- 对外暴露的 RPC / HTTP / WebSocket 接口入口。
- 执行时间开销大的方法，在方法开始处提前校验。
- 需要极高稳定性和可用性的方法。
- 涉及权限、金额、敏感数据的入口。

------

## 九、异常处理

【强制】捕获异常是为了处理它，**禁止空 catch 块**（既不处理也不抛出）。如果确实不需要处理，应注释说明原因，并至少打印日志。

【强制】自定义业务异常继承公共基类（`BaseException`），通过异常类型区分业务场景，禁止直接 `throw new RuntimeException("message")`。

正例：

```
BaseException（继承 RuntimeException）
├── OrderBusinessException      // 订单业务异常
├── AddressBookBusinessException // 地址簿业务异常
├── DeletionNotAllowedException // 禁止删除异常
└── LoginFailedException        // 登录失败异常
```

【强制】AOP 切面或反射调用中捕获 `Exception` 后，**禁止直接 `throw new RuntimeException(e)` 吞掉原始异常信息**，应保留原始异常或转换为自定义异常并传递 `cause`：

```java
// 正例
} catch (Exception e) {
    log.error("自动填充字段失败，实体类：{}，原因：{}", entity.getClass().getName(), e.getMessage(), e);
    throw new RuntimeException("公共字段自动填充失败", e);
}
```

【强制】全局异常处理器（`@RestControllerAdvice`）必须覆盖：

- 自定义业务异常（`BaseException` 子类）
- 参数校验异常（`MethodArgumentNotValidException`）
- SQL 约束违反异常（`SQLIntegrityConstraintViolationException`）
- 兜底异常（`Exception`），返回友好提示而非堆栈信息

【推荐】对于第三方 API 调用（如微信支付、百度地图），捕获后应记录**完整入参和响应**，便于排查：

```java
try {
    String result = baiduMapUtil.getDistance(origin, destination);
} catch (Exception e) {
    log.error("百度地图 API 调用失败，起点：{}，终点：{}，原因：{}", origin, destination, e.getMessage(), e);
    throw new OrderBusinessException(MessageConstant.MAP_API_FAILED);
}
```

------

## 十、日志规范

【强制】使用 `@Slf4j` 注解引入日志对象，禁止直接使用 `System.out.println()`。

【强制】日志输出使用**占位符 `{}`** 方式，禁止字符串拼接（避免不必要的字符串对象创建）：

```java
// 正例
log.info("员工登录，用户名：{}，IP：{}", employeeLoginDTO.getUsername(), request.getRemoteAddr());
// 反例
log.info("员工登录，用户名：" + employeeLoginDTO.getUsername());
```

【强制】日志级别使用规范：

| 级别    | 使用场景                                     |
| ------- | -------------------------------------------- |
| `DEBUG` | 开发/调试阶段详细信息，生产环境关闭          |
| `INFO`  | 关键业务流程节点（下单、支付、取消等）       |
| `WARN`  | 预期外但不影响主流程的情况（如超时重试）     |
| `ERROR` | 异常捕获、第三方接口失败，必须输出完整异常栈 |

【推荐】Controller 方法入口统一打印 INFO 日志，记录请求参数（注意脱敏密码等敏感字段）：

```java
@PostMapping("/login")
public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO dto) {
    // 脱敏：不打印密码字段
    log.info("员工登录，用户名：{}", dto.getUsername());
    // ...
}
```

【推荐】定时任务每次执行时打印 INFO 日志，记录执行时间及处理数量：

```java
@Scheduled(cron = "0 * * * * ?")
public void processTimeoutOrder() {
    log.info("【定时任务】扫描支付超时订单，执行时间：{}", LocalDateTime.now());
    // ...
    log.info("【定时任务】共处理 {} 笔超时订单", count);
}
```

------

## 十一、MyBatis / Mapper 规范

### 11.1 注解 vs XML

【推荐】**简单 SQL**（单表、无动态条件）使用 `@Select` / `@Insert` / `@Update` / `@Delete` 注解；**复杂 SQL**（多表 Join、动态条件、批量操作）放入 XML Mapper 文件，禁止在注解中拼接复杂 SQL 字符串。

```java
// 注解：简单查询
@Select("select * from orders where id = #{id}")
Orders getById(Long id);

// XML：动态条件查询（放入 OrderMapper.xml）
// <select id="pageQuery" resultType="...">
//     select * from orders
//     <where>
//         <if test="status != null"> and status = #{status} </if>
//         ...
//     </where>
// </select>
```

### 11.2 XML 规范

【强制】XML Mapper 文件中 SQL 关键字（`SELECT`、`FROM`、`WHERE`、`AND`、`ORDER BY` 等）推荐**小写**（与主流团队风格一致，便于阅读）。

【强制】动态 SQL 使用 `<where>`、`<set>`、`<trim>` 标签，禁止手动拼接 `where 1=1` 或 `, ` 前缀。

【强制】批量插入使用 `<foreach>` + `useGeneratedKeys="true"` + `keyProperty="id"`，确保回填主键 ID。

```xml
<insert id="insertBatch" useGeneratedKeys="true" keyProperty="id">
    insert into dish_flavor (dish_id, name, value) values
    <foreach collection="flavors" item="df" separator=",">
        (#{df.dishId}, #{df.name}, #{df.value})
    </foreach>
</insert>
```

【推荐】Mapper 接口方法参数超过 1 个时，使用 `@Param` 注解明确参数名，避免 MyBatis 默认 `param1`、`param2` 导致可读性差。

```java
// 正例
@Select("select * from orders where number = #{orderNumber} and user_id = #{userId}")
Orders getByNumberAndUserId(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);
```

### 11.3 自动填充（AOP）

【推荐】对 `createTime`、`createUser`、`updateTime`、`updateUser` 等公共审计字段，通过自定义 `@AutoFill` 注解 + AOP 切面统一赋值，禁止在 Service 层散落重复赋值代码。

约定：Mapper 方法第一个参数为待填充的实体对象，切面通过反射调用相应 setter 方法。

------

## 十二、Spring MVC / Controller 规范

### 12.1 接口设计

【强制】统一返回 `Result<T>` 包装类，字段含义：

| 字段   | 说明                       |
| ------ | -------------------------- |
| `code` | `1` 表示成功，`0` 表示失败 |
| `msg`  | 错误信息（成功时为 null）  |
| `data` | 业务数据（失败时为 null）  |

【强制】遵循 RESTful 风格：

- `GET` → 查询（幂等）
- `POST` → 新增 / 提交
- `PUT` → 全量修改
- `PATCH` → 局部修改（可选）
- `DELETE` → 删除

【推荐】URL 路径使用**小写 + 连字符**，不使用驼峰；资源名称使用**名词复数**：

```
GET  /admin/employees          // 员工列表
GET  /admin/employees/{id}     // 查询单个员工
POST /admin/employees          // 新增员工
PUT  /admin/employees          // 修改员工
POST /admin/employees/status/{status}  // 启用/禁用（状态变更可用 POST）
```

### 12.2 参数校验

【强制】Controller 方法的请求体 DTO 参数必须加 `@Valid` 注解触发校验；DTO 类中字段使用 `javax.validation` 注解：

```java
public class EmployeeDTO {
    @NotNull(message = "员工 ID 不能为空")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Length(max = 32, message = "用户名长度不能超过 32 位")
    private String username;
}
```

### 12.3 Swagger 文档

【推荐】所有 Controller 类加 `@Api(tags = "...")` 注解，每个接口方法加 `@ApiOperation(value = "...")`，参数 DTO/VO 加 `@ApiModel` 和 `@ApiModelProperty`，保持接口文档与代码同步。

### 12.4 分页查询

【推荐】分页查询统一使用 `PageHelper.startPage()` + `PageResult` 封装，`PageResult` 包含 `total`（总记录数）和 `records`（当前页数据）。

```java
public PageResult pageQuery(DishPageQueryDTO dto) {
    PageHelper.startPage(dto.getPage(), dto.getPageSize());
    Page<DishVO> page = dishMapper.pageQuery(dto);
    return new PageResult(page.getTotal(), page.getResult());
}
```

------

## 十三、其他

【强制】正则表达式利用**预编译**（`static final Pattern`），禁止在方法体内每次调用都编译：

```java
// 正例
private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

// 反例（每次方法调用都重新编译，性能差）
public boolean isValidPhone(String phone) {
    return phone.matches("^1[3-9]\\d{9}$");
}
```

【强制】获取当前毫秒数使用 `System.currentTimeMillis()`，禁止使用 `new Date().getTime()`。

【强制】`Math.random()` 返回 `[0, 1)` 的 double，若需随机整数，直接使用 `ThreadLocalRandom.current().nextInt(bound)` 或 `new Random().nextInt(bound)`。

【推荐】任何需要线程安全的场景（如定时任务对共享状态的操作），明确使用线程安全容器或同步机制，不依赖 `HashMap` / `ArrayList` 等非线程安全类。

【推荐】`ThreadLocal`（如 `BaseContext`）使用完毕后**必须调用 `remove()`**，避免线程池复用时数据污染：

```java
// 在拦截器的 afterCompletion 中清理
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                             Object handler, Exception ex) {
    BaseContext.removeCurrentId();
}
```

【推荐】定时任务类（`@Component` + `@Scheduled`）应处于独立的 `task` 包下，方法名以动词开头，清晰表达任务职责（如 `processTimeoutOrder`、`processDeliveryOrder`）。

【推荐】`@ConfigurationProperties` 配置映射类（`Properties` 结尾）应添加 `@Validated` 注解并在字段上声明约束，防止配置缺失导致启动后运行时才报错：

```java
@Data
@Component
@ConfigurationProperties(prefix = "yuweiju.jwt")
@Validated
public class JwtProperties {
    @NotBlank(message = "JWT 管理端 SecretKey 不能为空")
    private String adminSecretKey;

    @NotNull(message = "JWT 管理端 TTL 不能为空")
    private Long adminTtl;

    @NotBlank
    private String adminTokenName;
}
```

------

*本规范持续迭代，如有疑问或建议请提 Issue 或联系架构组。*