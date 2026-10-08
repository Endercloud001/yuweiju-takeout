# Issue #3 Windows 宿主验证摘要

验证日期：2026-10-09，Asia/Shanghai（原始日志带 +08:00）；本摘要整理于容器日期2026-10-08 UTC。范围为已获聊天授权的宿主准备、原生 Windows 构建/启动、真实登录与资源停止。文档整理没有重跑构建、服务或原库写入，没有更改业务源码、认证、schema、JDK/框架、安全措施或专用 Codex 认证。依赖 issue #2 已关闭；本票只落实 B1 本机启动，操作说明见 [Windows 本机开发](../../agents/local-development.md)。

## 环境、目录与来源

- Windows 11，Microsoft OpenJDK 21.0.10，Maven 3.9.10；Spring Boot 3.5.0，后端单 Maven 工程。
- 宿主验证仓库根为 `E:\Learning Files\yuweiju-takeout`；构建/Java工作目录为其 `yuweiju-backend`，Vite工作目录为其 `yuweiju-web-vue\yuweiju-admin`。小程序复制到项目内 `.scratch` 做验证，原工程和用户既有项目未改。
- 本摘要中的命令目录均指宿主验证工程，不把后续任务产物目录或 runtime 当工程根。普通读者使用包含三端应用的实际 clone 根目录。
- 原2026-10-05启动、复现、短目录、环境和小程序日志已主动清理，不能重新检查它们。以下引用新宿主证据，不通过重跑原库写入重建旧记录。

本次读取的输入副本位于只读 `/home/agent/task-input/.scratch/issue3-windows-startup/`。下面均是本机 **非跟踪** `.scratch` 来源路径，不是 fresh clone 必备文件，也不作为 Markdown 文件链接；本摘要保留可评审结论：

| 来源（相对宿主验证仓库根） | 内容 |
| --- | --- |
| `.scratch/issue3-windows-startup/verification-report.txt` | 授权、环境、原生探针、业务结果、存储范围、最终资源保留报告 |
| `.scratch/issue3-windows-startup/host-status.json` | 首轮构建/默认及参数启动/Vite PID、命令和退出结果；早期强制停止限制 |
| `.scratch/issue3-windows-startup/backend-default.log` | dev profile；默认启动异常和 exit1 对应日志 |
| `.scratch/issue3-windows-startup/admin-checks.json` | 真实浏览器密码登录、分页、未授权拒绝及页面错误检查 |
| `.scratch/issue3-windows-startup/miniapp-checks.json` | 实际 wx.login、真实身份交换、受保护历史查询 |
| `.scratch/issue3-windows-startup/database-before.txt`、`database-after.txt` | 用户数及有限库观察，不是完整快照 |
| `.scratch/issue3-windows-startup/shutdown-run/backend-projecttmp.log`、`shutdown-run/host-status.json` | 最终 Ctrl+C 正常关闭日志、退出码和 PID |

未复制私有 `.env`、`application-dev.yml`、密码、AppSecret、token 或 OpenID；选读 JSON/log 已脱敏。宿主报告确认已查看管理端 dashboard 和小程序首页截图，本摘要不嵌入未提供的图片。

## 命令与退出结果

以下记录宿主已执行内容，不是本轮 Linux 执行结果。命令中的保守开关跳过分析业务任务体、抑制风险评分及定时训练触发，并关闭显式 mock-login；不取消 scheduler 注册，也不排除其他副作用或 dev 实现已有模拟兜底。

```powershell
# 宿主工作目录：E:\Learning Files\yuweiju-takeout\yuweiju-backend
Set-Location -LiteralPath 'E:\Learning Files\yuweiju-takeout\yuweiju-backend'
mvn.cmd -B package
# exit0；19 tests，0 failure/error/skipped。
java -jar target/proj-boot-1.0-SNAPSHOT.jar `
    --analysis.task.enabled=false --order-risk.task.scoring-enabled=false `
    --sky.wechat.mock-login=false
# exit1；没有 Started App。

# 相同新构建 jar；目录已创建。主轮参数目录：
$SocketDir = 'E:\Learning Files\yuweiju-takeout\.scratch\issue3-windows-startup\sockets'
# 最终正常停止轮改用 .scratch\issue3-windows-startup\shutdown-run\sockets。
New-Item -ItemType Directory -Path $SocketDir -Force | Out-Null
java "-Djdk.net.unixdomain.tmpdir=$SocketDir" -jar target/proj-boot-1.0-SNAPSHOT.jar `
    --analysis.task.enabled=false --order-risk.task.scoring-enabled=false `
    --sky.wechat.mock-login=false
```

私有主配置激活 dev，日志确认 dev；上述历史命令未显式传 profile，当前入门命令显式传 `--spring.profiles.active=dev`。JVM `-D` 放在 `-jar` 前；Spring `--` 放在 jar 后。示例配置 import 的 `.env`/`../.env` 随工作目录解析，不能将本记录理解为免配置启动。

默认 jar（PID271496）出现 `Unable to establish loopback connection`，底层 `UnixDomainSockets.connect` / `Invalid argument: connect`，exit1。报告另记无业务依赖的 WindowsNioProbe 默认 `Selector.open` 两次 exit1，原 Temp 完整名称也失败；`C:\Windows\Temp` 和项目内 sockets 探针 exit0。项目内目录启动同一 jar，主轮 PID270560和最终轮 PID272392均 `Started App`，8080可达；最终轮日志启动时间 `00:30:45.457+08:00`。历史短目录 fallback 和项目内目录均有证据，入门优先项目内目录。未查明 Windows 底层根因；AppData/Local Encrypted 属性仅为未证实线索，未修改加密措施。

Vite工作目录如上，使用进程级 `VITE_API_BASE=/api`，宿主实际执行 `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort`；入门使用等价 npm.cmd dev 入口并恢复原环境值。浏览器连接5173，经 `/api` 代理连接8080。小程序由已登录官方开发者工具连接 localhost:8080；确认 AppID后 auto 成功、首页渲染，真实 SDK wx.login/请求见下表。早先不匹配 AppID 被官方工具拒绝为非小程序类型，不是最终成功结果。

## 验收与业务结果

| issue #3 条件 | 新证据与结论 |
| --- | --- |
| 实际环境、可构建 | Windows/OpenJDK/Maven如上；package exit0、19测试，无更换版本 |
| 默认失败与参数成功 | 默认 exit1；同 jar项目 sockets参数 `Started App`/8080可达；非开箱即用 |
| 管理端正常登录后保护请求成功 | 现有 admin 员工启用；admin/admin HTTP200 code0。使用现有凭据经真实浏览器登录页进入 `/dashboard`，HTTP200 code1；正常 Cookie/token 员工分页 HTTP200 code1、total2，pageErrors为空，未改密码 |
| 小程序真实身份与保护请求成功 | 实际 wx.login 获得code；wx.request `/user/user/login` HTTP200 code1；返回身份未进入 mock 身份路径。正常返回 token 的 `/user/order/historyOrders` HTTP200 code1、total10 |
| 未登录仍拒绝 | 管理端与用户端无token均 HTTP401、code0，未绕过认证 |
| 正常停止并保留原服务 | 最终 Ctrl+C自行退出、PID/端口消失；MySQL/Redis/原DevTools保留，详见下节 |
| 可操作说明和排障 | 配套入门文档包含显式目录、前置服务/端口、构建、两种启动、Vite base、微信连接、所有权停止、服务缺失和端口冲突处理 |

三端影响仅为开发说明：后端 jar/8080、管理端5173及VITE_API_BASE、小程序现有请求及工具连接；接口、schema、业务和UI无需修改。以上核对业务 code及查询结果，没有把 HTTP200、驱动 exit0或测试 JWT当作正常登录。

限制：未重新执行完整首页 getUserProfile授权弹窗点击流程；小程序证据限于真实 wx.login→后端交换身份→受保护查询，不扩大为完整UI、真机或生产认证验收。保守模式不证明普通开发任务已运行或训练完成。

## 正常停止、原服务与数据边界

早期记录保留：第一轮 CTRL_BREAK未完成正常关闭，25秒后兜底终止；第二轮独占控制台 CTRL_C开始Spring/Tomcat关闭，但25秒不足后仍兜底终止。这两轮不是成功正常停止，主轮 host-status 的 exit1不代表最终轮结果。

最终轮独占控制台 Ctrl+C启动 shutdown，后端 PID272392 自行 exit130：

- `00:30:47.133` 开始Tomcat graceful shutdown，`00:30:47.148` complete。
- `00:31:17.161`，`analysisTaskScheduler` 等待30秒后记录 `Timed out while waiting for executor`。
- `00:31:17.164` Druid closing，`.165` closed，然后JVM退出。

[AnalysisTaskConfiguration](../../../yuweiju-backend/src/main/java/com/codeying/config/AnalysisTaskConfiguration.java) 设置 `waitForTasksToCompleteOnShutdown=true`、`awaitTerminationSeconds=30`，与日志一致。即便任务体开关为false，已有scheduler仍注册；正常停止应至少等待60秒，并核对PID及监听消失，不能承诺25秒或掩盖timeout，也不推荐Ctrl+Break正常停Java。

最终Vite PID273724以Ctrl+C退出，原生信号exit3221225786不作为构建/业务失败。报告核查本次后端/Vite PID不存在，8080/5173/验证自动化9420无监听；MySQL PID7360、Redis PID268960及3306/6379保留，原微信开发者工具及43692服务保留。只关闭本次 `.scratch` 小程序验证项目，没有退出用户既有工具或停止其他进程。

获授权宿主准备曾访问原库：用户数前后13，登录复用既有用户；检查到的应用SQL日志中 INSERT/UPDATE/DELETE Preparing行为0，未改员工密码、未下单、未清表/缓存。结束观察员工2、订单955，但没有这些表的完整前后快照，也没有完整Redis比较；自动页面请求可能访问缓存。不能声称所有表/字段或存储均未变化。停止或代码回退不能逆转SQL、Redis、模型文件、HTTP或自增序列副作用；宿主脚本授权不自动转移给后续执行者，不应为补证无条件重跑原库。

## 验证来源与交付边界

本摘要是新宿主历史证据的整理，不是本轮 AFK 执行 Windows 命令的记录。原失败 AFK 的文档只是未提交草稿，旧独立审查只针对草稿，不证明本次候选已验收。文档依据当前配置和调用点核对；代理实际文档检查记录在本地 `.sandcastle/task-progress.md`，不把宿主 package/登录冒称为代理测试。后续独立快照将从本次确切候选提交创建，其路径和审查结果目前待定，尚未通过。无需为纯文档修改重跑构建、服务或原库操作；不新增 hash、contract、baseline、gate 或测试框架。
