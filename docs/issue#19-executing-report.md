# Issue #19 AFK 执行与独立验证报告

**最新结论（2026-10-08）：issue #19 的开发与功能验收条件已满足。** 候选业务改动已同步，后端与管理端独立验收通过；维护者已完成小程序正常登录及“营业→打烊→恢复营业”的人工验收并提供截图。既往失败和自动化超时保留为历史，最终依据见报告末尾。本次已获授权创建、推送并合并 PR、关闭 issue；远端最终状态以对应 GitHub PR 和 issue 页面为准。

初次 AFK 记录（2026-10-08，Asia/Shanghai）：两轮开发产出已交付，当时独立验收失败，尚未完成验收。未推送、合并、关闭 issue、修改原库或发布。Windows 当前分支与已有用户改动保留。

## 授权、配置与结果

- Issue：https://github.com/Endercloud001/yuweiju-takeout/issues/19；正文/评论/状态已读取，无评论；原生 blocked_by #2 已关闭。
- 用户限制：最多2轮，总时限30分钟。AFK实际2轮，00:39:01–00:56:19，17分18秒；配置 maxIterations=2 / totalMs=1800000 / idle600秒 / completion60秒。准备、验证与报告在本次30分钟会话范围内完成。
- 模型沿既有 gpt-6.1-sol / medium；Sandcastle0.12.0；镜像 sandcastle:yuweiju-dev；保持专用认证目录，仅开发挂载，独立验证不挂认证、不调用模型。
- WSL环境：/home/endercloud/projects/yuweiju-sandcastle-env。准备提交025a9a44baa467eef4ebc3dcd13332ba7338f5d2保存原有3个运行时参数文件变更；check:types退出0。没有改旧#4分支。
- 任务分支codex/afk-issue-19-20261008；任务提交e5cd53e1d21ba4c9b1f51cfce731089c03a3cf5e与5e9bc6d38d09b04b149f98acb4ef72b19fecda15；最终候选为5e9bc6d。Supervisor退出0、completionSignal存在、2个任务提交存在，三者分开核对；不等于验收通过。
- 配置/提示/输入：/home/endercloud/projects/yuweiju-afk-issue19-input。证据：/home/endercloud/projects/yuweiju-afk-issue19-evidence。

## 实施结果和三端影响

报表与工作台改用具名Mapper；共享真实订单聚合SQL，取消订单amount列表Java求和。简单日期/状态计数仍参数绑定；SUM/Join/聚合放XML。保留日期双闭区间、systemDefault时区、COMPLETED状态、TOP10原排序、全历史订单概览。报表客单价BigDecimal两位HALF_UP，工作台double除法，各自保留。

导出默认昨天结束的30日周期及数据编排移入Service；Controller保留Excel模板填充、文件名/Content-Type和输出流。失败返回HTTP500友好文本，保留异常cause，避免对外暴露数据库细节。生产店铺Redis故障不再吞掉并假报成功；缺键默认营业、0关闭/其他值营业归一保持；开发profile固定营业保持。店铺只有单次Redis操作，没有SQL更新或多表事务，未虚设Mapper/事务。

代表调用链：AdminReportController→ReportApplicationServiceImpl→OrdersMapper/UserMapper/OrderDetailMapper；AdminWorkspaceController→WorkspaceApplicationServiceImpl→OrdersMapper/UserMapper/DishMapper/SetmealMapper；AdminShopController/UserShopController→ShopStatusService→Redis（prod）/常量（dev）。

管理端/dashboard、/statistics的token、VITE_API_BASE、code=1、CSV图表字段、下载blob未改；小程序/user/shop/info与/user/shop/status、authentication和首页状态字段未改。两前端源码、公开API/schema和认证未变，无须前端迁移。新增普通测试和真实MySQL/Redis/HTTP/Excel/浏览器探针；没有新增hash、冻结contract、baseline或gate。

## 验证记录与限制

工作目录WSL环境，执行python3 .sandcastle/verify-task.py --config /home/endercloud/projects/yuweiju-afk-issue19-input/config.json --commit 5e9bc6d38d09b04b149f98acb4ef72b19fecda15。新detached worktree：/home/endercloud/projects/yuweiju-afk-issue19-evidence/task-review-1791392195936593386/snapshot。

| 检查 | 结果 | 业务意义 |
| --- | --- | --- |
| 开发端Maven package | 第二轮修正测试charset预期后代理报告退出0，31测试0失败 | 仅开发日志证据；不是独立通过 |
| 开发端admin npm ci/lint/typecheck/test/build | 代理日志报告0，Vitest2通过 | 前端源码无改动；未证明真实页面请求 |
| 独立mvn -B -f yuweiju-backend/pom.xml package | 退出1；31测试，1失败，0错误/跳过 | Issue19ReportExportTest期待200、实际500；未生成可验收jar |
| 独立admin及classpath命令 | 未执行 | verify-task首条失败即停止，不能引用代理结果冒充独立结果 |
| git diff --check 025a9a4..5e9bc6d | 0 | 仅差异格式检查 |
| 隔离compose up -d --wait | 0，MySQL/Redis healthy | 只证明夹具服务就绪，不证明issue业务验收 |
| 真实MySQL/Redis/Spring/HTTP/图表/Excel业务探针 | 未执行 | 构建失败后停止，不自动重跑或续跑 |
| 原生小程序交互 | 未执行 | 历史JSON证据已清理，未对原系统重跑补证 |

独立失败证据：task-review-1791392195936593386/check-0.log及snapshot/yuweiju-backend/target/surefire-reports；exit-status.tsv为0\t1。Controller第56行报模板不存在。源快照中的中文模板为tracked文件；target/classes没有对应xlsx。Java21 file.encoding=UTF-8，但容器POSIX locale使native.encoding/sun.jnu.encoding=ANSI_X3.4-1968。

只读定位命令使用同镜像及同快照运行Issue19PathProbe.java：默认locale退出1，InvalidPathException（中文路径不可映射）；仅临时容器LANG=C.UTF-8时退出0、native.encoding=UTF-8、Chinese template visible=true。这证明验证环境无法处理中文文件名；未重跑Maven，尚不能断言这一项修复足以通过全部验收。两个只读容器--rm，不挂认证。

其他发现：第一轮Maven依赖TLS下载失败；第二轮曾有新增测试漏charset断言，修正后开发验证通过。开发worker和独立verify-task环境有差异，独立结果优先。完整日志保留本地；开发/tmp日志在沙箱清理后不可直接读取，其结果限于AFK日志记录。

## 资源停止与恢复

AFK resources.json stopped=true；独立review resources.json exitCode=1 / stopped=true / authMounted=false / modelCalled=false。核对本轮两次开发容器、review容器和compose服务均不再存在；compose down记录在compose-stop.log，具名MySQL/Redis及网络已停止移除，保留本轮数据卷。没有prune、全局停止或删除旧成果。任务worktree按runtime清理，任务分支和提交保留；独立snapshot保留。

恢复：从任务分支或5e9bc6d建立新副本；独立快照含候选源码与失败日志。Windows可评审交付：.scratch/issue19-delivery/issue19-candidate.patch（025a9a4至5e9bc6d完整差异）及afk-result.json、afk-resources.json、independent-resources.json。未把候选应用到用户当前工作区。

回退只撤销候选代码；不会恢复任何外部状态。本轮无原库/原Redis写入；compose卷保留，不清卷。合成fixture业务探针未运行。后续验证必须仍指向隔离MySQL/Redis，不能改成原库补验。

## 人工评审与阻塞处理

1. 先阅读docs/issue19-plan.md及候选diff，核对聚合XML、日期双闭边界、两种精度、销量排序和店铺失败表达。
2. 在新的验证环境统一UTF-8 locale后，对确切候选重跑Maven及管理端四项检查；这是后续恢复工作，本轮未实施。
3. runtime验证必须执行候选快照中的.sandcastle/environment/verify.py并挂载候选environment（原准备分支environment不含Issue19Probe）；使用yuweiju-issue19-check_isolated等具名隔离网络。检查正常/空/边界数据、HTTP拒绝/非法日期、Excel数值与30行、/dashboard和/statistics实际响应，不以exit0代替业务断言。
4. 另验证原生小程序首页店铺状态；未验证前不把全部验收项勾选完成。当前不建议合并。

技能停止依据：.agents/skills/yuweiju-afk/SKILL.md第4步“Stop on unresolved decisions or failure. Do not retry or resume automatically.” 本轮已耗尽2轮且独立构建失败，未自动续跑。

## /retro（本宿主会话执行一次）

按C:/Users/Endercloud/.agents/skills/retro/SKILL.md和writing-for-agents参考完成复盘：先核对资源停止，再读AFK结果/日志、两提交diff、独立结果、项目package.json/现有验证脚本。提案按影响排列；均未实施，避免将建议写成完成项。

| 提案 | 证据与影响 | 建议行动 | 状态 |
| --- | --- | --- | --- |
| 开发/验证容器统一UTF-8 locale | fresh review的中文模板不可访问；只读同快照对照证明LANG影响 | 镜像或现有verify-task容器启动设置LANG=C.UTF-8，保留现有测试并重验候选；无需新gate | 待实施 |
| 明确运行候选environment | verify.py从脚本ROOT挂环境；候选新增业务探针而准备分支没有 | runtime说明指向候选快照脚本，避免误跑旧环境只得一般环境成功 | 待实施 |
| 对照开发与独立的命令环境 | worker显式Java PATH及代理环境，verify-task无locale；开发报告成功但独立失败 | 在既有结果记录注明Java/locale及实际命令，缩短定位；不复制认证环境 | 待实施 |
| 依赖下载失败记录与缓存复用 | 第一轮Maven TLS失败，第二轮下载后继续完成 | 复用隔离依赖缓存或预装依赖，保留失败退出码；不自动无限重试 | 待评估 |
| 保留分轮资源证据 | 当前资源文件按同run覆盖，最终记录只含第二轮容器 | 现有resource记录按iteration保留，便于失败轮复盘；无需hash/gate | 待评估 |

证据限制：原生小程序、真实HTTP/browser与MySQL边界探针未运行；开发完整/tmp日志不可直接复核；首轮资源记录被第二轮覆盖，但从日志记录的具名容器已核对停止。不能据此宣称业务验收完成。

## Changed files
- `.sandcastle/environment/EnvironmentProbe.java`
- `.sandcastle/environment/Issue19Probe.java`
- `.sandcastle/environment/browser-check.cjs`
- `.sandcastle/environment/build-check.sh`
- `.sandcastle/environment/runtime-check.sh`
- `.sandcastle/environment/training-check.sh`
- `.sandcastle/task-progress.md`
- `docs/issue19-plan.md`
- `yuweiju-backend/src/main/java/com/codeying/controller/admin/AdminReportController.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/DishMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/OrderDetailMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/OrdersMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/SetmealMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/UserMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/service/ReportApplicationService.java`
- `yuweiju-backend/src/main/java/com/codeying/service/ShopStatusService.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/ReportApplicationServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/ShopStatusServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/WorkspaceApplicationServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/vo/admin/report/OrderBusinessAggregate.java`
- `yuweiju-backend/src/main/java/com/codeying/vo/admin/report/ReportExportData.java`
- `yuweiju-backend/src/main/resources/mapper/OrderDetailMapper.xml`
- `yuweiju-backend/src/main/resources/mapper/OrdersMapper.xml`
- `yuweiju-backend/src/test/java/com/codeying/controller/admin/Issue19ReportExportTest.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/Issue19ShopStatusTest.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/Issue19StatisticsTest.java`

## 2026-10-08 关闭条件独立复核（后续会话）

本节补充新证据，保留上次失败历史。按verification-before-completion重新读取issue正文/评论/状态、实际工程和候选，未启动AFK、未续跑开发代理、未修改业务源码/提交/issue状态。当前issue仍OPEN，无新评论。

结论更新：候选5e9bc6d的后端、管理端、真实MySQL/Redis/HTTP/Excel/浏览器已独立验收通过。上次中文模板失败在UTF-8验证命令中消除。当前Windows工程仍ecd2029，尚未应用候选；原生小程序首页登录/营业状态展示交互未验证，故本次仍不满足完整关闭条件。不能将候选已通过误写为当前工作区已实施。

### 新运行与证据

工作目录/home/endercloud/projects/yuweiju-sandcastle-env；新配置/home/endercloud/projects/yuweiju-afk-issue19-input/verification-config.json，仅检查命令export LANG=C.UTF-8，不更改既有runtime/镜像/专用认证。python3 .sandcastle/verify-task.py --config 上述配置 --commit 5e9bc6d退出0。

新鲜候选snapshot：/home/endercloud/projects/yuweiju-issue19-close-review/task-review-1791392844020233170/snapshot（detached5e9bc6d）。

| 执行目录与命令 | 退出/结果 |
| --- | --- |
| snapshot根：export LANG=C.UTF-8; mvn -B -f yuweiju-backend/pom.xml clean package | 0；31 tests / 0 failures / 0 errors / 0 skipped；BUILD SUCCESS |
| snapshot/yuweiju-web-vue/yuweiju-admin：npm ci及lint/typecheck/test/build | 整链0；check-1.log保留完整输出 |
| snapshot/yuweiju-backend：python3 ../.sandcastle/environment/extract-classpath.py | 0，来自本次实际jar |
| 新隔离compose项目yuweiju-issue19-closecheck up -d --wait | 0，独立数据卷/内部网络 |
| 无认证非root容器LANG=C.UTF-8挂候选environment，bash /environment/runtime-check.sh | 0；compile/local-http-fixtures/Spring/admin-runtime-build/admin-browser均0 |
| compose down及本轮runtime容器结束 | 0；stopped=true；核对本轮容器均不存在，数据卷保留 |

runtime资源与日志：/home/endercloud/projects/yuweiju-issue19-close-review/runtime-review-1791393007590786499。宿主独立脚本/runtime-verify.py及结果/runtime-result.log保留在该evidence根；不挂认证、不调用模型、不访问原库，不prune/清卷。

真实业务输出：ISSUE19 REAL_MYSQL_BOUNDARIES_STATUS_SUM_TOP10_PRECISION_HTTP_EXCEL_REDIS_PASS；ISSUE19 BROWSER_EMPTY_WORKBOOK_NUMERIC_DAILY_CELLS_PASS。浏览器正常fixture密码登录，/dashboard与/statistics实际请求code=1，非法日期/无token拒绝，Excel成功下载并核对数字单元格，pageErrors=0。人工查看dashboard/statistics截图，空统计页面展示正常；没有历史同环境截图作像素比较，两端源码无diff。

真实MySQL探针涵盖闰日日首/末及区间外边界、完成与非完成金额、用户累计、新增、TOP10降序/限制、空结果、工作台double10/3与报表3.33、默认30日非空Excel，finally仅清理专属数据。真实Redis调用prod服务验证0/1并恢复键；缓存故障和核心查询失败用本次31项中的普通Mockito测试验证传播/cause，未宣称真实Redis断网或原生prod profile全栈失败注入。dev HTTP固定营业规则仍保留。

### 逐项判断

| Issue #19验收项 | 候选判断 | 当前工程/证据限制 |
| --- | --- | --- |
| 三组调用链与具名业务查询 | 满足；Mapper/XML与Service职责已检查 | Windows旧Service仍含QueryWrapper及Java金额扫描，候选只在独立分支 |
| 日期/状态/金额/排序、正常/空/边界 | 本次实际MySQL与HTTP/Excel通过 | 本票无分页接口，不虚设分页修改/验收 |
| 非法日期、权限拒绝、核心查询/缓存故障 | HTTP拒绝真实验证；故障普通测试通过 | 单Redis操作无SQL事务，不强行新建事务/Mapper |
| 管理端图表/导出、首页店铺状态与规则 | 管理端及shop接口通过、更新规则在Service | 原生小程序正常登录、首页展示/营业行为仍未验证 |

关闭前还需：在确定交付分支应用/集成本票候选；完成隔离或另行授权的原生小程序首页实际交互并记录业务结果。若维护者明确接受以接口验证替代原生验收，应记录该验收调整，本次没有此决定。没有发现新的已复现候选业务代码缺陷；UTF-8 locale永久统一属于环境改进，已用临时明确环境完成候选复核，不继续把旧Maven失败当现存业务缺陷。

源码现状证据（Windows）：ReportApplicationServiceImpl.java:49起仍是Service Wrapper与amount列表；WorkspaceApplicationServiceImpl.java:49起同样；ShopStatusServiceImpl.java:30/39仍吞异常。git HEAD ecd2029与task分支5e9bc6d分别核对。复核仅追加本报告，未应用补丁或修改GitHub。
## 候选同步及微信工具重开后的实际验收尝试

用户授权应用候选，已将18个后端业务/Mapper/XML/VO/普通测试文件及docs/issue19-plan.md应用到Windows工作区。未切换分支、提交、重置或覆盖用户已有删除/未跟踪材料。verify-sync.py逐文件比较候选5e9bc6d（仅规范化CRLF）共19文件，differentFiles=[]；反向git apply --check及git diff --check退出0。Sandcastle环境脚本仍在其原WSL运行目录，不把不完整宿主runtime导入Windows工程。

用户打开微信开发者工具后重查：CLI islogin退出0、login=true；实际IDE端口53280。CLI auto打开本任务隔离副本E:/Learning Files/yuweiju-takeout/.scratch/issue19-miniapp-review，自动化9420监听，官方SDK读取pages/index/index并截图成功。只在该副本将baseUrl改为127.0.0.1:18080，业务小程序源码未修改。

已验证候选jar以prod,afk启动，独立compose项目yuweiju-issue19-native；MySQL/Redis无原库连接，后端使用独立测试JWT键、模型目录和fixtures。本机现有微信AppID配置匹配项目、mock-login=false；专用真实微信配置只写仓库外600权限临时配置，结束后删除，未输出秘密。后端挂独立网络及出网网络，仅正常微信code交换需要外部服务，不使用dev模拟用户回退。

部分实际结果：官方模拟器首页读取shopStatus=1、tokenPresent=true；真实GET /user/shop/info、category/list、shoppingCart/list、dish/list均HTTP200/code1且authentication存在。隔离数据库新增1个微信登录用户，mock用户0、非mock/非sandbox用户1（只输出统计，不输出openid/token）。这支持真实微信登录及正常首页接口链路已发生；没有记录其最初授权动作与完整登录请求，不能把它替代完整交互验收。

失败：重新触发正常登录getData后，SDK页面状态读取/截图多次达到30–45秒deadline；native.confirmModal及authorizeAllow操作返回，但后续页面状态/截图仍超时。未得到打烊/恢复营业的完整首页UI断言。不能因CLI auto退出0、连接成功、或有token就宣称原生交互全部通过。正常登录授权过程与营业→打烊→恢复的UI验收仍需人工补齐。

证据位于.scratch/issue19-delivery/native-initial.png、native-inspect.png、native-inspect.json、native-action.cjs及专属WSL native-acceptance目录；超时不产生成功JSON/截图。native-inspect.json为首次成功读取，不覆盖为失败后新结果。自动化脚本只输出路径/状态/业务code/authentication是否存在，未输出token、openid或wx.login的code。

本轮资源处理：恢复独立店铺营业1；compose down退出0，具名backend/gateway/MySQL/Redis及网络停止移除，保留隔离卷；删除临时微信配置。关闭本轮隔离项目，不退出用户IDE。Windows源码同步核对再次退出0。

### 人工验收操作步骤（已由维护者完成，见后续确认）

所有PowerShell命令在E:/Learning Files/yuweiju-takeout根目录运行。

1. 启动已准备好的隔离后端：`& '.scratch/issue19-delivery/start-native.ps1'`。脚本从现有本机配置读取匹配的微信设置，不输出凭据，使用已验收jar及独立MySQL/Redis。等到http://127.0.0.1:18080/user/shop/status返回code1/data1后继续。不要连接原库或原店铺Redis。
2. 在已登录的微信开发者工具打开`.scratch/issue19-miniapp-review`，编译运行首页`pages/index/index`，手动确认“授权微信登录”并允许用户资料授权。不要输入或注入token；确认Network中POST `/user/user/login`成功，随后的GET `/user/shop/info`为code1/status1，首页显示“营业中”、地址及正常菜品。保存脱敏请求结果与截图，不保存登录code/token/openid。
3. 在PowerShell执行`python -X utf8 '.scratch/issue19-delivery/native-admin.py' 0`；只修改独立Redis。重新进入或编译首页，确认shop/info返回status0，首页显示“休息中”和现有关闭遮罩，点餐入口行为符合关闭展示。不要为验收实际提交订单；记录结果截图。
4. 执行`python -X utf8 '.scratch/issue19-delivery/native-admin.py' 1`；重新进入首页，确认status1、营业中、关闭遮罩消失及点餐入口恢复，记录截图。prod-profile使状态切换有效；dev固定营业不能替代此项。
5. 查看Console/Network，确认无新的页面异常，shop字段、authentication、code1协议未变。完成后运行`& '.scratch/issue19-delivery/stop-native.ps1'`，关闭本轮隔离项目。该脚本只停止本任务资源并删除仓库外临时微信配置，保留隔离数据卷。
6. 将上述真实登录、两次状态变化的脱敏业务结果及截图补入本报告，然后再判断issue关闭条件；本次未关闭GitHub issue。
## 2026-10-08 维护者人工验收确认与最终判断

维护者在本会话明确确认：“已按你所言进行人工操作，确认每一步均符合正常行为。首次登录进会显示‘营业中’，可以使用native-admin.py正确改变营业状态。”本节将先前待补齐的小程序实际交互更新为人工验收通过；不改写为自动化成功。

| 人工操作 | 结果与证据 | 判断 |
| --- | --- | --- |
| 正常登录并进入首页 | 维护者确认首次登录显示营业中；截图1显示官方工具中的pages/index/index、营业中、Isolation street及Fixture dish | 通过（人工确认及截图） |
| 执行native-admin.py 0，刷新首页 | 截图2显示isolatedShopStatus=0，首页为休息中；维护者确认整步行为正常 | 通过 |
| 执行native-admin.py 1，刷新首页 | 截图3显示isolatedShopStatus=1，首页恢复营业中；维护者确认整步行为正常 | 通过 |
| 页面行为、请求及收尾操作 | 维护者确认此前给出的各人工步骤均符合正常行为；截图未完整覆盖Network、Console及资源停止 | 通过，依据维护者确认；未新增自动断言或资源停止检查 |

截图2、3已原样保存，分别为[打烊状态](verification-evidence/2026-10-08/issue19/manual-shop-closed.png)和[恢复营业状态](verification-evidence/2026-10-08/issue19/manual-shop-reopened.png)。截图1包含Storage中的token值，仅用于本会话视觉核对，不复制到仓库、不转录token或用户资料。截图中未完整显示关闭遮罩/点餐入口、首次授权全过程及Network，因此这些行为依据维护者对全部人工步骤的确认，不声称截图单独证明它们。

本次文档更新前重新执行verify-sync.py，退出0，19个业务/测试/方案文件与候选5e9bc6d一致（differentFiles=[]）。源码未再调整，无须重复此前已通过的整套业务测试；本次未启动、停止服务或写原库/Redis。

### Issue验收条件最终对照

- [x] 报表、工作台、店铺检查结论及代表调用链明确；业务筛选/聚合走具名Mapper，店铺真实Redis职责在Service。
- [x] 原日期、状态、金额、排序口径保留；正常/空/边界数据已有真实MySQL、HTTP和Excel独立验证。
- [x] 非法日期与权限拒绝已有实际HTTP验证；核心查询/缓存故障的失败传播已有通过的普通测试。
- [x] 管理端图表和导出独立验收通过；小程序正常登录、营业/休息/恢复营业的首页交互由维护者完成并确认；更新规则在Service。

综合已记录的独立验证、代码同步和本次人工确认，issue #19 功能开发与验收已满足关闭条件。既往“尚未应用候选”和“原生首页未验收”两个缺口已消除。自动化SDK超时作为工具限制保留，不再作为功能验收阻塞。本轮授权仅更新执行报告，未操作GitHub关闭、提交、合并或发布。

## 2026-10-08 交付复验与复盘改进

维护者授权使用子代理分别落实复盘、创建并推送 PR、合并并关闭 issue。业务交付在独立工作区 `E:/Learning Files/yuweiju-issue19-pr`、分支 `codex/issue-19-business-queries` 准备，基于最新 `origin/main` 的 `c8db7629c94a3395930d7e033eb7b1ea4307babd`。仅包含 18 个后端文件、实施计划、本报告和两张脱敏人工验收截图；原工作区分支和无关用户改动保留。

该交付工作区重新执行 Maven clean package，Java 21.0.9、UTF-8、独立无认证容器，退出 0；31 项测试，0 失败、0 错误、0 跳过，BUILD SUCCESS。证据 `/home/endercloud/projects/yuweiju-issue19-pr-evidence/maven.log` 与 `result.json` 已由主代理读取核对；差异格式检查退出 0。既有管理端、隔离业务探针及维护者小程序人工证据继续适用。运行时工具改进单独保存，不混入业务 PR。

以下为原 /retro 建议的最新落实结果，原表中的待实施/待评估是初次复盘时的历史状态。

### /retro 建议行动实施结果

2026-10-08（Asia/Shanghai）。实施目录 `/home/endercloud/projects/yuweiju-sandcastle-env`，分支 `codex/sandcastle-environment-20261007`，本地提交 `bcbaa03`（未推送其环境历史）。根工程业务代码、原库、原 Redis、旧 #4 分支/成果、既有认证边界均未修改；原有四个未跟踪 docs 材料保留。

| 建议行动 | 已实施结果 |
| --- | --- |
| 开发/验证统一 UTF-8 locale | Docker provider、verify-task、environment verifier 显式设置 `LANG=C.UTF-8` / `LC_ALL=C.UTF-8`；现有镜像实测 native.encoding=UTF-8。Dockerfile 同步 ENV，尚未重建镜像，该默认值下次 build 生效；本次实际运行由启动参数保证。 |
| 明确候选 environment 入口 | environment verifier 挂载被验收 snapshot 的 `.sandcastle/environment`，记录实际目录、宿主 verifier 版本及候选来源；runtime-only 原证据目录限制保留，environmentCommit 留空且 declaredCandidateCommit 仅为调用者声明，不将旧快照假称已证明提交来源。无网络合成夹具用候选独有 marker 验证挂载。 |
| 对照命令环境 | resource 保留 Java 路径/版本、native.encoding、locale；verify-task 写 environment.log，environment steps 记录实际容器命令；既有 checkCommands 继续保留。不复制认证环境或输出 token。 |
| 依赖缓存复用/预装评估 | Maven/npm 缓存只绑定本次 run 的 evidence/dependency-cache，跨同一 run 多轮复用，不挂个人缓存、不跨业务任务共享，独立验收仍用干净容器。两轮夹具实际读回 cache marker；失败退出仍保留，无自动无限重试。未预装全部业务依赖：候选依赖会变化，已有最小缓存复用满足需要。 |
| 分轮资源证据 | resource 顶层字段兼容保留，追加 iterations 历史，每轮保存 containerId/mounts/环境并在 finally 独立核对 stopped；fresh 夹具证明两轮记录对应两个真实容器且均已移除。 |

改动 10 个文件：`.sandcastle/{README.md,common.mts,config-fixture.mts,resource-check.mts,run-config-fixture.py,verify-task.py}` 与 `.sandcastle/environment/{Dockerfile,README.md,build-check.sh,verify.py}`。三端业务、接口、schema 无变化，无需消费者调整；回退本地运行时提交可恢复启动行为，不删已保留 evidence/cache。

### 实际验证

工作目录均为上述 WSL 工程；`npm run check:types`、`python3 -m py_compile .sandcastle/verify-task.py .sandcastle/environment/verify.py .sandcastle/run-config-fixture.py`、`git diff --check` 均退出 0。

- `SANDCASTLE_EVIDENCE=/home/endercloud/projects/yuweiju-issue19-retro-verification-final python3 .sandcastle/run-config-fixture.py`：退出 0。全程无模型、不挂认证；两轮本地夹具验证 UTF-8、缓存复用、两次真实容器资源；独立五条检查全部退出 0；合成 snapshot 的 environment mount fixture 在 `--network none` 下退出 0。
- 最终来源字段调整后，`SANDCASTLE_EVIDENCE=/home/endercloud/projects/yuweiju-issue19-retro-verification-final python3 .sandcastle/environment/verify.py --commit ecd2029 --runtime-only /home/endercloud/projects/yuweiju-issue19-retro-verification-final/environment-mount-fixture-1791395892949913407 --network none`：退出 0，environmentCommit=null，声明提交与实际挂载目录分别记录。此合成夹具只证明 runner 行为，不冒充 issue #19 业务复验。
- `SANDCASTLE_EVIDENCE=/home/endercloud/projects/yuweiju-issue19-retro-lifecycle npm run check:lifecycle`：退出 0；9 个已有生命周期场景全部通过，包括 exit7、无完成标记、正常完成、取消、总时限、SIGINT/SIGTERM、idle timeout、缺提交。日志 `/home/endercloud/projects/yuweiju-issue19-retro-lifecycle.log`。
- 收尾核对：三个本轮 evidence 目录共 15 个 resources.json，全部 stopped=true，按记录容器逐一 docker inspect 均不存在；9 个生命周期 verdict 均通过。未启动 Compose、未访问原数据库/店铺、未启动新业务 AFK、未删除卷或旧成果。

首次夹具调用因新 evidence 父目录未建立退出 1；修正既有夹具 mkdir 为 parents=True 后，新鲜夹具通过。该工具路径失败没有自动重试业务或改变业务验收结论。

本次运行时改进与先前已独立验证的候选业务交付分开保存；镜像未重建、未再次执行完整业务测试，候选验收结论仍依据既有独立及维护者人工证据。
