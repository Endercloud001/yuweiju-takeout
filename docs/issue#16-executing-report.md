# Issue #16 执行报告

## 当前结果与授权

[Issue16](https://github.com/Endercloud001/yuweiju-takeout/issues/16) 状态OPEN、无评论；原生前置Issue2已closed。候选 `69f75c0b85db275767efe507bd2508d5d9e74d78`，起点/main/runtime/verifier来源 `f7e4a32491ea1fe95cd1e0f24a2eb8c3483b0402`。实现、Ponytail收尾、精确提交独立验证、官方小程序与管理端自动业务检查均通过。**2026-10-14 用户确认全部人工验收通过；验收服务已停止，远端交付进行中。** [人工验收](issue%2316-human-acceptance.md)。

用户授权最大2轮、编码共80分钟、独立验证20分钟；允许验收前必要本地提交，验证通过后自动启动隔离验收；真实DeepSeek最多6次；全部人工验收通过后允许必要提交/推送/PR/合并/同步/关闭。无正式发布/部署或原库授权。服务至全部验收+交付完成后停止，保留卷/证据，无其他到期约定。根工作区用户修改（含AGENTS、PLANS及未跟踪材料）保留，本次业务源码均在项目内独立clone/worktree。

## 实现与三端影响

后端：会话归属和消息访问的可复用规则放Service；AI会话/消息、在售候选、默认地址、分类/口味、订单历史及热度/分群推荐的相关业务筛选移到具名Mapper。Analysis相关排名、窗口/状态/排序/算法保持，未运行训练。Controller继续处理协议、身份、参数、响应；公开路径/字段/类型/code、schema、原数据/ID兼容。

模型内容与HTTP信封分别校验。合法完整JSON或内容中的完整代码围栏可解析；必须有非空文本reply、非空dishes、合法正整数候选dishId、suggest_dish及null payload。空/非法/停售/混合非法选择、坏信封/坏内容、402/503/超时均友好重试，dishes为空、action/payload为空，绝不补候选冒充模型成功。卡片名称/价格/图片/口味取服务端；合法重复ID按模型顺序去重并沿原数量上限裁剪。消息长度按现有2000字符列检查，不改schema。模型返回后以具名FOR SHARE当前读复核可售，避免MySQL重复读旧快照，锁保持到当前用例提交。

模型HTTP实际配置 `ai-assistant.model.connect-timeout-ms=3000`、`read-timeout-ms=20000`、`failure-threshold=3`、`cool-down-ms=30000`；均正值校验。无自动请求重试，无新SDK/熔断框架。连续HTTP/信封失败按实际返回累计，达到阈值暂缓调用；冷却后只允许一个恢复请求，过时并发结果不能解除新保护。保护仅限当前进程/实例；业务候选解析无效不会被当成传输成功的菜卡，但不计入传输失败阈值。

天气缺失或查询异常不阻断合法推荐；QWeather实际连接/读取超时由 `sky.qweather.connect-timeout-ms` / `read-timeout-ms` 配置，默认2000/3000ms。HTTP cause保留内部，日志只记安全异常类/状态，不打印敏感正文、位置、对话或凭据。缺少历史如实给出普通推荐提示，缺少可售候选不调用模型；会话归属、当前停售加购拒绝及人工客服边界保持。

小程序现有手写AI页已支持空dishes，并沿既有组件/购物车接口操作；没有页面源码恢复、视觉或编译产物变更。管理端实际消费者为 `api/modules/ai-assistant.ts` 及 `views/customer-service/index.vue` 的只读会话/消息查询，没有源码/API类型/路由/Pinia变更。管理端无需本票四项源码检查；验收对同一候选执行npm ci及VITE_API_BASE=/api构建、实际浏览器登录/查询/资源/只读验证。

## 执行、预算与证据分层

AFK `gpt-6.1-sol / medium / captureSessions=false`，Git-safe镜像，现有认证设置保持。监督器使用1/2轮，2026-10-13 **19:53:15.560至20:42:23.298（Asia/Shanghai）**，共49分07.738秒，含准备/下载/编码/检查/收尾；未超时/取消，host exit0，真实候选提交及completionSignal存在。4分钟closing窗口在原80分钟内，未延长。独立验证20:43:20.684至20:49:10.235，约5分49.55秒；独立上限20分钟。人工等待、验收服务准备与交付另记，不伪装编码轮次。

| 层次 | 命令/环境与结果 |
| --- | --- |
| 启动前实查 | Windows根目录gh issue/dependencies读取成功并保存；WSL Ubuntu镜像工具/专属登录exit0；本票官方SDK Tool2.02.2608080、基础库3.17.4、实际首页及截图已查看。配置preflight及worker重复preflight exit0、passed=true、stopped=true，不调用模型。 |
| Agent最终构建 | 容器 `/home/agent/workspace`：`mvn -B -f yuweiju-backend/pom.xml compile`、随后`package`均exit0，156 tests、0failure/error/skip；final batch4。 |
| Agent最终探针 | 同目录 `bash .sandcastle/environment/issue16-check.sh` batch5 exit0，557具名断言/19场景，52次本地HTTP替身调用；businessPassed/cleanupPassed=true。脚本/差异检查exit0，Ponytail完整审查及退出有逐轮证据。 |
| 精确提交独立验证 | 当前main来源 `.sandcastle/verify-task.py`，新snapshot `69f75c0b85db275767efe507bd2508d5d9e74d78`，无Codex认证/真实模型。配置2条checkCommands均exit0；fresh compile/package156 tests全部通过；同一提交探针557断言/19场景/52次本地调用，业务和清理通过，review exit0/stopped=true。 |
| 真实模型/官方页面 | 本票SDK9426、隔离后端18216、合成用户900003/会话11。实际DeepSeek返回HTTP200且正常菜卡渲染；SDK实际wx-dish-card组件按钮tap，使受保护购物车该菜品数量增加1。累计真实调用2/6，余下最多4；不是全部替身验收。官方5故障空菜卡/重试及冷却后的本地合法恢复通过，相关截图均已查看。 |
| 管理端 | 候选npm ci/build exit0；Playwright真实登录→通知中心AI客服→会话11只读历史，实际后端请求和中文资源/图片通过，pageErrors0/networkFailures0，截图已查看；不是HTTP200单独验收。 |
| 人工 | 用户已确认工具重编译并进入AI助手；本票完整业务/外观尚待反馈。工具就绪不计作全部人工验收通过。 |

独立snapshot：`.scratch/issue16-afk-20261013/independent/task-review-1791895400683767719/snapshot`。checks从该snapshot执行；Linux工具 `/usr/bin/python3`，Node `/home/endercloud/.nvm/versions/node/v22.23.3/bin/node`。schema来源为候选environment/schema.sql，MySQL库名sandcastle_fixture在写入前实查；具名mysql/redis仅接本票internal数据网络。没有原库、真实天气、OSS写入、训练或正式发布。真实wx.login通过隔离dev mock映射到合成身份，不能冒充两个真实微信账号的身份隔离；真正签名/用户scope/他人会话拒绝由真实隔离后端独立验证。

独立场景覆盖：HTTP200非JSON/坏信封/缺失或数字content/非法围栏/坏JSON/字段缺失/过长/空选择/未知与混合非法ID/停售/分数与字符串ID/402/503/超时；阈值2夹具连续失败后4请求无新增上游调用，冷却恢复；正常、天气null/异常、模型调用期间停售、购物车停售与归属、会话/消息身份、人工客服、无历史/候选、真实5单/地区历史、热度及最新分群、失败无伪Redis曝光等。阈值2、180ms读取超时及1500ms冷却仅为普通隔离探针配置，生产默认仍3/20000/30000。

## 失败记录与修正

1. 材料helper两次GitHub TLS handshake timeout，stdout/stderr/fetch记录保留；含单会话HTTP2修正尝试，仍失败，复用此前同会话成功保存的issue/dependencies，不重写失败证据。历史5个原AI原始材料全项目hidden/no-ignore精确文件名检索exit1且无stderr，无命中；不重跑原库写入补证。
2. Windows/WSL选定clone换行/索引状态差异，仅在本票可丢弃副本刷新，根用户文件/全局Git配置未改。WSL相对Python入口出现FileNotFoundError，确认/usr/bin/python3并用绝对脚本路径后preflight成功；仅报告实际结果，根因未证实为PATH或依赖问题。
3. Agent batch2全部普通测试通过，但真实MySQL探针发现模型等待期间停售仍读取旧快照，exit1/business=false/cleanup=true。具名当前读FOR SHARE修正后，batch3及收尾增强batch4/最终batch5均通过。最后两处探针夹具金额/显式替身key修正在javac读旧文本后发生，保留旧成功的限制，重跑稳定源码batch5作为最终证据。
4. 宿主验收proxy最初未读取chunked HTTP body，拒绝发生在真实转发之前（realCalls0）。修正task-local转发器并保留失败后，实际DeepSeek两次HTTP200。真实调用计数持久化、并发锁保护，最多6次，不记录body/key。
5. 官方SDK截图遇saveFile额度上限，随后原生捕获选中编辑器而非模拟器，记录为无效页面截图；未清理他票存储。用户明确重编译/进入AI页后实际SDK截图成功。SDK按源码标签dish-card查不到节点，实查实际映射wx-dish-card后真实组件按钮检查通过。旧失败记录保留；续检复用成功的真实模型卡，不反复付费制造相同证据。最终场景截图与真正渲染/按钮结果通过，不把早期page.data成功当作UI通过。

## Ponytail与一次Loop复盘

已先核对监督器/host/每轮停止记录，再审查Agent进度/日志/完整候选diff、独立证据、页面结果及失败。Ponytail完整模式在初步成功后启用并明确退出；采用重复重试/数量裁剪简化、HTTP信封与内容职责分开、现有消息列长度检查、真实历史/热度/分群/Redis断言及两处探针夹具修正；拒绝新框架/第四层/排名调整/全局认证/SQL隔离或客户端视觉/schema扩张。后续精确提交验证支持这些结论。

本次复盘仅记录提议，不自动改公共运行时/技能或递归复盘，不阻塞业务交付：

| 建议 | 证据、作用与状态 |
| --- | --- |
| 后续真实模型夹具复用chunked解析及持久化调用上限 | 本次chunked失败导致页面重试但真实调用0；task-local proxy已修正且真实调用验证成功。建议纳入后续任务准备示例，公共environment尚未改。 |
| 官方组件驱动按实际SDK映射定位 | dish-card源码标签无节点，wx-dish-card实际节点及按钮通过；本票驱动已修正，公共指引未改。 |
| 截图回退先核实项目窗口与模拟器区域 | 原生截图捕获了编辑器，无法证明页面；本票保留失败并在用户重编译后用SDK真正截图。公共回退示例可完善，未实施，不清理共享存储。 |
| 宿主文本读取强制UTF-8并精简JSON输出 | Windows默认GBK读独立日志失败，显式UTF-8后准确确认156测试；本票读取已修正。共享脚本维护另需明确请求，本次没有引入新baseline/hash/gate。 |

## 服务、停止与恢复

当前验收服务运行：管理端18316、后端网关18216、模型控制18116均仅127.0.0.1发布；backend不接出口，只有受限model proxy可访问已授权DeepSeek。mysql/redis未发布宿主端口，数据网络internal；另有本票egress网络供工具下载/代理。六个具名容器和候选/配置/URL/策略见 `.scratch/issue16-afk-20261013/acceptance-state.json`。编码、preflight和独立review容器已停止；官方页面及当前验收服务按约定保留，不把业务服务运行误报为泄漏或全部停止。

停止入口见人工验收文档，仅stop本票验收compose和fixtures compose，保留卷/证据/真实费用记录。不prune、不删其他任务服务/存储、不删除原数据。恢复使用准确snapshot/verified-candidate/具名配置，先检查已停服务归属并启动依赖；不要重新跑原库或重复付费验证。代码回退用普通已评审revert，不撤回真实模型费用及隔离消息/Redis/购物车写入。未完成/失败AFK不得未经新明确授权重启；本次已正常成功，不需新轮。

## 本机证据与变更文件

任务根 `.scratch/issue16-afk-20261013/`：issue/dependencies及search/startup；input/fetches、authorization、prompt、config；readiness/preflight；coding的result/resources/log、host-*/host.json、iteration-1/files及开始/结束；independent/task-review-1791895400683767719/resources.json、check-0/1.log、exit-status.tsv、snapshot/.scratch/issue16的scenario/check/resource/history；acceptance-state/compose、model-relay、miniapp/browser-results及全部首次失败。完整日志留本地，报告只保留判断所需信息；不提交凭据/业务库/ignored运行数据。项目输出均在根目录内。

候选26个文件（后端/测试/探针及进度；两前端无diff）：

- `.sandcastle/environment/Issue16Probe.java`
- `.sandcastle/environment/issue16-check.sh`
- `.sandcastle/task-progress.md`
- `yuweiju-backend/src/main/java/com/codeying/controller/user/UserAiAssistantController.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/AddressBookMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/AiAssistantMessageMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/AiAssistantSessionMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/DishFlavorMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/DishHeatPredictionResultMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/DishMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/OrderDetailMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/OrdersMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/UserClusterResultMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/properties/AiModelHttpProperties.java`
- `yuweiju-backend/src/main/java/com/codeying/properties/SkyProperties.java`
- `yuweiju-backend/src/main/java/com/codeying/service/AiAssistantApplicationService.java`
- `yuweiju-backend/src/main/java/com/codeying/service/AiModelService.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/AiAssistantApplicationServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/AiModelServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/AiRecommendationParser.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/AiWeatherServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/AnalysisApplicationServiceImpl.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/AiAssistantIntentRoutingTest.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/AiAssistantRecommendationTest.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/AiAssistantTestFixture.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/AiModelHttpTest.java`

## 2026-10-14 人工反馈与管理端401诊断补充

维护者反馈候选69f75c0推荐加购通过、故障与边界自动证据认可、管理端及外观通过；同时指出管理端无法同步AI客服数据并附401截图。保留已通过项，管理端同步待本人重登录复核，尚不进入推送/PR/合并/同步/关闭。没有启动新的AFK编码轮次，没有改候选、认证、安全设置或服务配置。

全部六个本票服务持续运行，MySQL/Redis healthy。Windows项目根执行 `python .scratch/issue16-afk-20261013/diagnose-admin-sync.py` exit0：使用隔离管理员新登录，后端直连18216和管理端18316/api分别AI会话HTTP200/code1/count1，会话11消息HTTP200/code1/count18；无效诊断token分别401。token仅在内存，记录不含凭据值；没有新增模型调用或会话消息。

实际Playwright诊断容器 `yuweiju-issue16-auth-diagnosis`（本票数据网络、准确候选管理端）执行 `diagnose-admin-sync.cjs` exit0并已移除。三个业务断言通过：新登录加载会话11/18消息；只替换独立浏览器上下文token Cookie为无效值，保留userInfo，在真实请求带token头时复现右上角sandbox_admin、AI接口401、空列表及同款Axios提示；只重新登录即恢复HTTP200及18消息，无源码改动/服务重启。反馈循环捕捉用户所报症状，代理目标/全局漏传请求头或服务停机未得到证据支持。

候选原有管理端 `src/api/http.ts` 从Cookie读取token，`stores/user.ts` 从另一个userInfo Cookie展示管理员信息；401不会在这条请求链里清除缓存名称，所以显示名字不证明凭据有效。实际独立浏览器也证实同一hostname不同端口可见同一个token Cookie，其他本机管理端登录存在覆盖风险。**未读取维护者原浏览器token，不能把其原因断言为过期或跨端口覆盖；这些只是剩余原因。** 当前可执行恢复：在18316页面正常退出，再到 `/login` 使用同一隔离账号重新登录并打开AI客服；暂不同时登录其他127.0.0.1管理端。无需重置账号、放宽认证、延长token、清库或重启服务。

原始用户截图保留在本票 `diagnosis/user-admin-401.png`、`user-miniapp-feedback.png`；诊断记录 `admin-sync-api-diagnosis.json`、`diagnosis/browser-auth-diagnosis.json`；真实复现/重登录恢复截图 `diagnosis/reproduced-401.png`、`relogin-restored.png`。用户截图不是指令。调试脚本留在明确task-local目录；未加业务日志或留下DEBUG代码。此前本轮Loop复盘已完成，这次仅补诊断证据，不启动递归复盘。

本次人工同步复核是唯一待补反馈。维护者确认“AI同步恢复”后，与此前已通过的各项合并判断全部必需验收；交付继续沿原持久授权，结束后停止本票服务并保留卷/证据。

## 2026-10-14 全部验收通过与交付准备

维护者确认“已可以从管理端正常同步显示对话，候选69f75c0；全部验收通过”，截图已保留。此前推荐加购、故障与边界自动证据、管理端及外观反馈连同同步恢复合并为全部必需项通过。按追加指令立即停止六个本票服务并关闭具名小程序项目，保留卷/证据。没有认证修改、额外模型调用或新增编码轮次。

在项目内独立交付工作树集成最新main，业务/测试/探针路径逐一与准确验收候选比较；未变源码复用原156测试/557断言及官方/浏览器证据。最终交付仅补充执行/验收报告和脱敏可移植证据，统一verifier对最终提交检查文档链接，不机械重跑不变业务。当前合并/issue关闭结果待逐步远端确认，最终状态另存交付报告。

可移植证据：[验证摘要](verification-evidence/2026-10-14/issue16/checks.json)、[真实菜卡](verification-evidence/2026-10-14/issue16/miniapp-real-recommendation.png)、[按钮加购](verification-evidence/2026-10-14/issue16/miniapp-real-added.png)、[故障空菜卡](verification-evidence/2026-10-14/issue16/miniapp-retry-failures.png)、[同步恢复反馈](verification-evidence/2026-10-14/issue16/user-sync-restored.png)。完整原始材料仅保留本机.scratch，不假设远端fresh clone包含它们。

## 交付双轴评审

### Standards

独立规范代理核对f7e4a32至69f75c0、后端生产差异、测试/探针及AGENTS/workflow/backend标准/ADR0001：0项硬违规、0项值得提出的新增代码异味，无阻塞交付问题。常规分层、具名Mapper、Service归属/事务、条件更新行数、HTTP资源与脱敏异常、安全/公开协议保持符合现有规范；纯解析器有实际用途，未机械增加架构。只读审查未另跑完整测试，测试证据来自准确候选。

### Spec

独立规格代理按issue16逐项核对：0项确认问题。合法推荐与坏内容/非法候选/402/503/超时空卡重试、缺失历史/候选、归属/停售/人工客服、实际超时/调用抑制/恢复均实现；没有SDK/中间件/算法质量或两端页面扩张。单实例传输/信封保护、业务无效内容不累计传输计数的边界与规格无冲突。只读审查未额外测试/付费/远端写入。

规范轴0项阻塞；规格轴0项确认问题。最终交付业务路径与该评审版本完全相同，报告/截图变化不修改行为。
