# Issue #15 执行报告

更新于 2026-10-13T14:43:40.960818+08:00。Issue：[人工客服职责与归属隔离](https://github.com/Endercloud001/yuweiju-takeout/issues/15)。

当前候选：`66faf5dfe5ab4ea899515db446796f7832618509`。业务起点 `4ef7679b203dbfb8832d8f8234bde7afdbfdf7bd`；中断草稿WIP `585970b`；已验收业务候选 `b5070c9`。旧执行报告完整保留在 `../.scratch/issue15-afk-20261013/supplement/prior-executing-report.md`，原输入、result、resources、检查批次与失败证据未覆盖。

## 当前结论

后端职责和真实消息事务已恢复；追加轮修复小程序右侧气泡出界。当前候选独立布局验证与官方SDK实测通过，已取得可查看的官方模拟器截图。用户已确认b5070c9双向/历史及关闭拒绝/重开通过；新候选只改客服WXSS两条声明，后端/管理端及小程序JS/WXML不变，因此复用原对应验收与准确提交验证，用户已确认新候选外观通过，全部必需人工验收满足。验收服务已按追加授权停止并保留卷/证据；远端交付进行中，合并/关闭结果在交付记录中核实。

| 阶段 | 真实状态 |
| --- | --- |
| 原运行 | 宿主exit1、Docker/WSL失联，缺result；不完整 |
| 授权恢复轮 | 本地b5070c9保存，Ponytail完成；因普通COMPLETE未匹配promise，监督器仍incomplete |
| b5070c9独立验证 | 三组exit0，107后端测试/17管理端测试/207真实探针断言，stopped=true |
| b5070c9人工反馈 | 双向/历史通过；关闭拒绝/重开通过；小程序视觉失败（右气泡出界） |
| 用户追加轮授权 | 由Agent决定追加一轮；选择20分钟，只修布局，保留原总预算 |
| 追加轮 | agent-reported-complete，收到promise；候选66faf5d，Ponytail/最终检查完成，stopped=true |
| 追加准确提交独立验证 | layout命令exit0，四宽度32消息，stopped=true，无auth/模型 |
| 官方模拟器 | 390px视口：列表390、右气泡368，短/长文本和左侧回复均在内；截图成功 |
| 新候选人工验收 | 用户明确反馈“候选66faf5d；外观通过”，连同已通过业务验收全部满足 |

## 职责与三端影响

HTTP/WS负责协议、可信认证身份、参数/响应、连接及广播；既有ApplicationService承担用户归属、会话状态、历史和发送/关闭用例。消息插入与会话更新时间在实际Spring代理事务内原子执行，会话行锁协调消息与关闭。具名Mapper提供最新进行中会话、进行中列表、历史游标/最后消息、条件更新/关闭；标准主键CRUD保留。原输入锁/解锁和认证黑名单保留，不扩展竞争优化。

核心事务失败不广播；事务提交后投递，IOException不能撤销SQL。Service直接复用仍守归属和状态，trim后空控制字符拒绝。探针ERROR等待会检查意外CHAT_MESSAGE，避免漏验未提交广播。

后端API/schema/原数据/ID/code/时间及认证兼容。管理端/inform和消息消费不变。小程序手写pages/customer-service/index.wxss的`.cs-list`原为content-box：默认100%宽度再加横向padding，挤出视口；新增width100%与box-sizing:border-box，把原padding留在容器内。其余颜色、角色方向、字体、气泡宽度规则、输入、滚动、JS/WXML和socket处理保持。不恢复源工程、不加依赖/层/schema/hash/contract/baseline/gate。

完整候选相对原main的文件：

- `.sandcastle/environment/Issue15Probe.java`
- `.sandcastle/environment/issue15-check.sh`
- `.sandcastle/environment/issue15-layout-check.cjs`
- `docs/issue15-plan.md`
- `yuweiju-backend/src/main/java/com/codeying/controller/admin/AdminCustomerServiceController.java`
- `yuweiju-backend/src/main/java/com/codeying/controller/user/UserCustomerServiceController.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/CustomerServiceMessageMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/mapper/CustomerServiceSessionMapper.java`
- `yuweiju-backend/src/main/java/com/codeying/service/CustomerServiceApplicationService.java`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/CustomerServiceApplicationServiceImpl.java`
- `yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceWebSocketServer.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/CustomerServiceRulesTest.java`
- `yuweiju-backend/src/test/java/com/codeying/websocket/CustomerServiceTransportTest.java`
- `yuweiju-weixin-miniapp/pages/customer-service/index.wxss`

## 验证与来源

### b5070c9准确提交（复用）

工作目录：原独立snapshot根，记录 `../.scratch/issue15-afk-20261013/independent/task-review-1791870753155914315/resources.json`。05:52:32UTC至06:04:01UTC，11分29秒。Maven compile/package107项，零失败/错误/skip；管理端npm ci/lint/typecheck/test17/build；真实Spring/MyBatis/MySQL/HTTP/JDK-WebSocket探针207断言，全部exit0。验证容器无认证/模型/原库，finally清理stopped=true。

真实探针覆盖双向事件及SQL持久化、游标/顺序/限额/时间/重连、匿名与错误scope/黑名单、两次正常合成登录且用户ID不同、他人读/写、空/失效/关闭、直接Service复用、INSERT和会话UPDATE触发器失败回滚且无聊天广播、失败投递保留commit、受影响输入锁失败/重试/解锁/断线。清理仅自建会话/消息/触发器/自身黑名单key，共享夹具保持。并非Mockito替代实际事务。

当前候选相对b5070c9的后端、管理端、小程序JS/WXML Git diff为空；复用的是这些精确不变源码的证据，不宣称为新候选重跑Maven/npm。唯一产品追加差异为WXSS两声明，另有普通渲染测试及计划补充。

### 布局追加验证

Agent内部工作目录为container `/home/agent/workspace`。实际WXSS和WXML结构进入浏览器夹具，保留native scroll-view默认100%宽的最小适配；模型不声称该适配等同官方工具。原b507源码red batch3退出1，320px时list347.28/right328.53。前两批适配错误和两次过强长文本/滚动断言失败均保留；修正探针，不改正确产品迎合断言。green及Ponytail最终检查exit0，320/375/390/430四宽度、32条短/长无空格/多行消息，检查列表/气泡/文字/时间、方向/颜色/输入及滚动可达。

Host从准确66faf5d创建新独立snapshot，命令 `node .sandcastle/environment/issue15-layout-check.cjs`，exit0。资源记录 `../.scratch/issue15-afk-20261013/supplement/independent/task-review-1791873405715204778/snapshot/../resources.json`，authMounted=false、modelCalled=false、stopped=true。

Host实际SDK命令：根目录 `node .scratch/issue15-afk-20261013/layout-probe.cjs layout-before` exit1；更新本票副本WXSS并经官方CLI重新加载后，同命令layout-after exit0。自动化9425、项目miniapp，未替换HTTP/WS；布局夹具仅暂时setData，finally恢复页面原消息，没有SQL演示写入。重连接首次未就绪失败保留，有限重试后同项目成功。

| 官方SDK实测 | 修复前 | 修复后 |
| --- | ---: | ---: |
| 视口宽 | 390 | 390 |
| 列表宽 | 422 | 390 |
| 右侧短消息边缘 | 400 | 368 |
| 右侧长消息边缘 | 400 | 368 |
| 左侧回复边缘 | 131.45 | 131.45 |

后截图 `../.scratch/issue15-afk-20261013/supplement/layout-after.png` 为官方SDK真实画面，包含合成短消息/长文本/回复，已目视确认。此前SDK保存文件限额及无效原生捕获依然是历史失败；本次官方CLI重载后SDK截图成功，没有清理他票存储或修改安全设置。用户原反馈截图保存于supplement/user-bubble-overflow.png，作为before；不新增像素差异gate。

### Ponytail与作用边界

原恢复轮完整复查并采纳Service控制字符和探针事件等待修正；追加轮复查全部3个追加路径、调用页/导入样式和原业务风险，保留最小列表盒模型修正，拒绝气泡/字体/颜色/角色/全局样式/JS/认证/新通用平台改造。明确stop ponytail，再重跑必需布局检查、显式暂存及提交66faf5d。身份缺失的首次commit失败保留，以per-command Codex身份解决，未改Git/global配置和guard。进度和ignored证据没有提交。

## Docker/WSL异常诊断

用户截图记录DockerDesktop/Wsl/ExecError，命令 `c:\windows\system32\wsl.exe --version` exit1。实际宿主日志 `com.docker.backend.exe.log.20261013-140731.503` 第917行，2026-10-13 13:28:20（+08）记录同一失败；stderr按UTF16LE解码为“系统无法访问此文件。”。此前还有Docker内部发行版bootstrap失败，属于宿主WSL/引擎启动阶段，并非项目Java业务异常。组件版本文件缺失warning本身不足以确认原因。

现在 `wsl --version` exit0（3.0.3.0）、Docker server29.8.1、具名MySQL/Redis健康、验收服务运行；追加预检passed/stopped，追加运行与独立检查未再遇该错误。旧编码container已确认exited255并移除，不删除挂载/卷。没有当前可复现的失败，无法证明访问失败由更新、权限、系统文件状态或资源压力哪一项引起；未声称修好WSL根因，也未做reset/reinstall/purge/关闭其他服务。完整脱敏来源/当前命令/退出码在supplement/wsl-diagnosis.json；用户截图在supplement/user-wsl-error.png。

采取的处理是保全草稿、确认旧资源停止、获明确恢复授权、扣除原预算、重新预检。若同类故障复现，按相同停止/证据/授权边界处理；不添加无依据系统改造或自动重启编码。

## 预算、资源、服务与交付

原两轮/80分钟，独立20分钟。原中断缺result，以开始及实际停机核查保守扣费；恢复20分钟验证实际11分29秒，原剩余8分31秒用于追加验证。前两轮保守编码约50分42秒，用户授权追加1轮，配置20分钟，实际2026-10-13T06:27:44.775Z至2026-10-13T06:36:16.235Z约8分32秒；合计约59分14秒，未重置80分钟。追加监督器正式完成，resource stopped=True；独立资源stopped=True；没有第四轮。首次原运行的缺result及恢复轮的incomplete不补写成功。

用户在交付指令中追加允许立即停服；本票五个服务已停止，卷/证据保留。URL保持：[管理端](http://127.0.0.1:18315/inform)、后端127.0.0.1:18215。隔离管理员sandbox_admin/sandbox-only-login，合成用户900003；dev mock稳定openid，不证明两个真实微信账号。当前66faf5d的后端/admin产物复用b5070c9精确不变源码构建，miniapp仅替换新候选WXSS。acceptance-state保存此映射和原snapshot，未把旧产物冒充重构产物。

用户已确认66faf5d外观；原双向/历史/关闭拒绝/重开反馈及最新全部通过状态已保存human-feedback.json。全部必需验收通过后沿用授权执行必要提交/推送/PR/合并/同步/关闭，交付按持久授权进行，最终状态见交付记录。停止入口为项目内acceptance.py stop，只操作已记录的本票backend/gateway/admin/MySQL/Redis；保留卷。无需无反馈自动到期或清卷。

未验证全部多管理员/多实例/首次open/重连竞争、中途WS token撤销、所有物理投递故障、真实微信身份隔离。原库/会话2未触碰，旧五份客服材料全项目含隐藏/忽略目录搜索零命中，未通过写原库重建。代码回退可撤销本票commit或只回退WXSS；不能撤回已广播消息或删除演示历史。

## retro: Loop复盘与实施状态

先查停止记录：原失联由recovery/stop.json补充确认；恢复、追加、两次独立容器均停止。旧报告/原失败/迭代证据、Git对象和WIP仍在项目内。

已实施：客服盒模型最小修正、实际样式几何回归、官方SDK red/green与有效截图；本追加prompt明确promise完成行，监督器实际识别成功。未改共享runtime/skill，不能称共享环境问题已永久修复。

仅建议：启动器持久化宿主stdout/stderr/结束状态以改善WSL失联证据；任务准备明确局部Git身份以减少commit环境错误。需要后续维护授权，不由此业务票自动实施。Docker故障因缺当前复现，不建议猜测性版本升级/重装；保持已恢复环境并保留具体错误时间/退出码。业务交付不递归等待维护项目。
## Loop复盘与实施状态

本节按维护者2026-10-13交付指令再次运行本机retro技能更新。来源为三轮原result/resources/Agent日志/逐轮progress、两轮准确提交独立验证、真实探针/渲染失败和绿结果、SDK前后几何/截图、Docker第917行日志、人工反馈、最终Git diff及停止记录。原复盘段落与旧执行报告已保留在本机项目内；不是只复读上一份总结。

先查停止：旧编码container45bdc869确认exited255后移除；授权恢复、追加、两次独立验证均stopped=true。新交付指令授权立即停止验收backend/gateway/admin和本票MySQL/Redis；acceptance-state保存stoppedAt，当前docker ps无本票运行容器。没有prune/reset/清卷/删除历史；根工作区用户分支和修改保持。

按影响排序的环境改进：

| 优先级/类别 | 具体证据与影响 | 建议及实现状态 |
| --- | --- | --- |
| 高：信息访问/资源故障 | 首轮Docker/WSL失联，宿主exit1但无result，资源末状态缺失；后来查日志才解码“系统无法访问此文件。”，无法证明底层原因 | 建议启动器将宿主stdout/stderr、exit/signal和最后已知资源状态写任务目录，失联后继续保留unknown而非补写成功；已有5秒采样/恢复stop记录保留。仅建议，未改共享runtime、未猜测性重装WSL |
| 高：可执行完成指令 | b5070c9代码/检查/Ponytail完成但只输出普通COMPLETE，监督器因未收到promise仍incomplete；66faf5d尾部明确独占promise后实际识别complete | 本追加prompt已实施且有真实识别结果；建议将精确完成行并入既有任务prompt准备器，并用现有input/config普通测试覆盖。共享准备器尚未修改；保留当前监督器，不新建gate |
| 高：自动检查/视觉能力 | SDK截图限额后，b5070c9没有有效模拟器图，实际列表422>视口390漏到人工反馈才暴露；官方size/offset仍可读 | 已实施实际WXML/WXSS普通几何回归（四宽度32消息）、官方SDK red/green及有效截图；建议截图失败时优先做尺寸检查并明确人工视觉未执行。新测试仅绑定本票checkCommands，根package/shared checks未接入该项，不宣称已持续运行 |
| 中：命令环境 | 恢复和追加最终commit首次均Author identity unknown；WindowsPowerShell5解析UTF8中文脚本失败；Vite预览配置目录只读导致EROFS | 本票用per-command Codex身份、ASCII窗标题、可写的本票preview目录及有限就绪等待修正；建议准备入口核实任务局部身份/执行器编码/工具临时输出目录。未改globalGit/安全/原项目配置 |
| 中：探针数据与副作用 | 首批Mockito重复设桩触发旧异常；JDBC DATETIME猜测时区导致错误失败；ERROR等待可能丢掉CHAT_MESSAGE | 两个Service/探针边界已在Ponytail修正，真实SQL207断言通过；现有startup已写明doReturn和实际时间映射，属于遵循已有导航的缺口，不再复制新规则。建议准备摘要链接到相应现有段落，保留全部失败批次 |
| 中：导航/工具经济 | /retro未列在可用技能目录，首次只查工具未发现入口，随后全局技能目录发现实际retro/SKILL.md；多次宽范围批读出现截断 | 本次已实际读取retro和writing-for-agents并重做复盘；建议显式命名技能先查真实文件，工具输出默认只回状态/数量/证据位置，大数组/日志保留本地。未改全局技能目录/AGENTS，不新增常驻重复说明 |
| 低：现有检查接线 | 当前GitHub Actions workflows数量0；项目已有Maven、admin lint/typecheck/test/build、runtime check脚本和统一verifier，layout新测试目前只有任务命令 | 建议后续独立维护将适用现有普通检查接入既有公共检查入口，必要时另行讨论CI。此请求仅复盘/业务交付，不实施CI、pre-commit或新合并gate，不把维护阻塞本票关闭 |

复盘与交付评审的限制：code-review的规范和规格两轴子代理因模型容量失败；规范轴一次同模型重试仍失败，不能声称独立双代理评审完成。宿主分别按backend/miniapp规范、ADR0001和issue15/追加授权复核：Service归属/状态/代理事务、具名Mapper、协议/黑名单/锁、错误消息与code兼容、两条WXSS及真实回归；未发现阻塞交付的问题。规范轴无新增必须修复项；规格轴四项验收与经授权视觉补充满足。所有竞争/真实微信和WSL根因限制继续保留，没有借复盘扩大行为或移除安全措施。

已实施仅限本票业务/样式/回归、任务prompt/本地准备修正和报告；共享环境建议均未实施。用户请求再次retro授权分析/文档，不自动授权维护PR或系统改造。建议按上表顺序作为独立维护候选，本票业务交付不等待或递归启动它们。

## 交付核对

2026-10-13：原生依赖#2关闭；GitHub最新main4ef7679与本票起点一致，项目内codex/issue15-delivery-20261013独立工作树squash集成66faf5d，无冲突，不改变应用/测试/探针源码。最终提交将只附加验收/复盘报告和脱敏可移植证据；未变源码复用准确验证，文档/渲染在最终提交新快照复核。GitHub API可读；Git直连github:443失败后，复用确认属于现有Clash的127.0.0.1:7897和单次HTTP1.1参数成功fetch，未改全局代理。远端PR/head/main/issue状态逐步核实，最终链接/提交存于交付报告。

完整原始任务证据仅在本机.scratch；可移植摘要和布局前后图见[交付证据](verification-evidence/2026-10-13/issue15/checks.json)、[修复前](verification-evidence/2026-10-13/issue15/layout-before.png)、[修复后](verification-evidence/2026-10-13/issue15/layout-after.png)。不假设远端clone包含本机临时文件。
