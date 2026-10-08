# Windows 本机开发

适用 issue #3：仅补充三端连接与停止说明，API、schema、认证、JDK/框架及页面均不变。已验证 Windows 11、Microsoft OpenJDK 21.0.10、Maven 3.9.10；不要求为此换版本，也不承诺开箱即用。结果和限制见随仓库提供的 [2026-10-09 宿主验证摘要](../verification-evidence/2026-10-09/issue3-host-verification.md)。2026-10-05 原始日志已主动清理，当前结论引用新证据。

## 前置准备与端口

以下为 PowerShell 命令；每个新终端先执行下面的 `$RepoRoot` 设置，按自己的 clone 位置调整。该根目录必须包含三个应用目录，不能指向任务产物目录；临时文件放工程根的 `.scratch`。

```powershell
$RepoRoot = 'E:/Learning Files/yuweiju-takeout' # 按自己的 clone 调整
Set-Location -LiteralPath $RepoRoot
foreach ($Dir in @('yuweiju-backend','yuweiju-web-vue','yuweiju-weixin-miniapp')) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $Dir) -PathType Container)) {
        throw "工程根目录不正确，缺少 $Dir"
    }
}
java -version
mvn.cmd -version
node --version
npm.cmd --version
Get-Service | Where-Object { $_.Name -match 'mysql|redis' } |
    Select-Object Name, Status, DisplayName

# 不按 LocalPort 查询，避免“无匹配对象”被误读为查询失败。
function Get-LocalListeners([int[]]$Ports) {
    try {
        Get-NetTCPConnection -ErrorAction Stop |
            Where-Object { $_.State -eq 'Listen' -and $_.LocalPort -in $Ports } |
            Select-Object LocalAddress, LocalPort, OwningProcess
    } catch {
        throw "无法枚举 TCP 连接；不能据此判定端口空闲：$($_.Exception.Message)"
    }
}
Get-LocalListeners @(3306,6379,8080,5173)
Test-NetConnection localhost -Port 3306 -InformationLevel Quiet
Test-NetConnection localhost -Port 6379 -InformationLevel Quiet
```

Java/Maven 依据 [后端 POM](../../yuweiju-backend/pom.xml)，Node/npm 依据 [管理端依赖](../../yuweiju-web-vue/yuweiju-admin/package.json) 与锁文件准备。MySQL/Redis 必须与本机私有配置中的地址、端口、库和权限一致；3306/6379 是当前参考，不代表密码/数据库已可用。已有服务保留；如已安装的服务未运行，在确认服务名称和启动权限后，仅启动所选服务：

```powershell
# 工作目录：$RepoRoot；每次只选一个已确认的 MySQL 或 Redis 服务。
$ServiceName = Read-Host '输入上表中确认要启动的服务 Name'
$Service = Get-Service -Name $ServiceName -ErrorAction Stop
if ($Service.Status -ne 'Running') { Start-Service -InputObject $Service -ErrorAction Stop }
Get-Service -Name $ServiceName
```

若 Redis 以独立进程运行而非 Windows 服务，用本机已配置的启动入口，由其所有者管理，不盲目安装或重启。服务缺失或连通检查为 False 时，先准备服务并核对地址/防火墙；不导入 SQL、重建库、清表或清缓存来排障。8080/5173 已占用时，根据 OwningProcess 用 `Get-Process -Id <实际PID>` 查所有者；协调其使用或等待释放，不按端口杀他人进程。

## 私有配置、构建与后端启动

fresh clone 只有 [主配置示例](../../yuweiju-backend/src/main/resources/application.example.yml) 和 [dev 示例](../../yuweiju-backend/src/main/resources/application-dev.example.yml)，示例名不会自动加载，占位值也不能直接运行。由获授权的本机配置维护者准备未跟踪的 `application.yml`、`application-dev.yml` 及所需环境值；已有配置保留。不要复制私有配置到文档、日志或输入目录，不显示密码、AppSecret、token、OpenID。

示例默认启用 dev，并导入相对当前工作目录的 `.env` 与 `../.env`。Spring Boot 默认还搜索 classpath、当前目录、当前目录的 `config/` 及其直接子目录；dev 激活时加载相应 `application-dev.*`。这里固定后端目录运行，避免改变配置与 runtime 模型路径的含义。jar 构建后才加入的资源配置不会自动进入旧 jar；使用本机已确认的外部配置或重新构建，不随意覆盖配置搜索位置。

启动和页面请求可能触发现有 SQL、Redis、模型文件和 HTTP 副作用。普通开发按实际配置启用业务任务，先核查环境和写入授权；失败/边界验证用隔离环境。下列保守验证参数与普通开发模式有区别：`analysis.task.enabled=false` 跳过分析任务体，`order-risk.task.scoring-enabled=false` 抑制风险评分及定时训练触发，`sky.wechat.mock-login=false` 关闭显式模拟登录。它们不取消已有 scheduler/定时任务注册，也不保证全部副作用消失；dev 微信实现仍有既有模拟兜底，必须核对实际返回身份，不能只凭 `mock-login=false` 宣称真实登录。

```powershell
# 工作目录：后端；本轮文档整理不执行这些命令。
Set-Location -LiteralPath (Join-Path $RepoRoot 'yuweiju-backend')
mvn.cmd -B package
if ($LASTEXITCODE -ne 0) { throw '后端构建失败，停止启动' }

# 相对 .scratch 有意以仓库根计算，不以当前后端目录计算。
$SocketDir = Join-Path $RepoRoot '.scratch\local-development\sockets'
New-Item -ItemType Directory -Path $SocketDir -Force | Out-Null
$SocketDir = (Resolve-Path -LiteralPath $SocketDir).Path
# 独立前台终端：保守验证模式，目录含空格时整个 JVM 参数必须加引号。
java "-Djdk.net.unixdomain.tmpdir=$SocketDir" -jar target/proj-boot-1.0-SNAPSHOT.jar `
    --spring.profiles.active=dev `
    --analysis.task.enabled=false --order-risk.task.scoring-enabled=false `
    --sky.wechat.mock-login=false
```

`-D...` 在 `-jar` 前，是 JVM 参数；`--...` 在 jar 后，是 Spring 应用参数。普通开发可用同一目录及 socket 参数，去掉三个保守验证开关，按本机配置启用任务；显式 dev profile 可保留。等待 `Started App` 并核对8080，再做正常登录后的业务请求；HTTP200 不等于业务 code1。

默认路径排障对照（仅在获授权的隔离/保留环境需要复现时运行，不必重复）：

```powershell
# 工作目录：后端；同一个已构建 jar。
Set-Location -LiteralPath (Join-Path $RepoRoot 'yuweiju-backend')
java -jar target/proj-boot-1.0-SNAPSHOT.jar --spring.profiles.active=dev `
    --analysis.task.enabled=false --order-risk.task.scoring-enabled=false `
    --sky.wechat.mock-login=false
```

2026-10-09 默认方式 exit1，出现 `Unable to establish loopback connection`、`UnixDomainSockets.connect` / `Invalid argument: connect`；加入项目内 socket 目录后同一 jar 成功。该参数不改变应用/模型临时文件目录。`C:\Windows\Temp` 也有成功探针证据，优先用项目内目录便于保留与清理。若目录方式仍失败，先查目录已创建、可写、引用完整、JVM 参数位置与本机权限，再看端口/服务及异常 cause；不把该 Windows 环境问题定性为业务缺陷。AppData 加密属性只是未证实线索，不能据此认定根因或删除安全措施。

## 管理端与微信开发者工具

在另一个前台 PowerShell 终端运行（按自己的 clone 调整根目录）：

```powershell
$RepoRoot = 'E:/Learning Files/yuweiju-takeout'
Set-Location -LiteralPath (Join-Path $RepoRoot 'yuweiju-web-vue\yuweiju-admin')
# fresh clone 需依赖安装；已有可用依赖无需重复。
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw '依赖安装失败' }
$PreviousApiBase = [Environment]::GetEnvironmentVariable('VITE_API_BASE', 'Process')
try {
    $env:VITE_API_BASE = '/api'
    npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
} finally {
    [Environment]::SetEnvironmentVariable('VITE_API_BASE', $PreviousApiBase, 'Process')
}
```

浏览器打开 `http://127.0.0.1:5173`，用获授权的现有账户经登录页面登录。admin 账户存在，但 `admin/admin` 无效；不在公共文档提供密码或查询密码的明文操作。`VITE_API_BASE=/api` 仅影响当前进程；[http.ts](../../yuweiju-web-vue/yuweiju-admin/src/api/http.ts) 使用它并从 Cookie 发 `token`，[Vite 配置](../../yuweiju-web-vue/yuweiju-admin/vite.config.ts) 去掉 `/api` 转发到 `http://localhost:8080`（已有 `VITE_PROXY_TARGET` 可覆盖，需核对）。`--strictPort` 防止5173冲突时静默改端口。Ctrl+C 返回后恢复原环境值。

```powershell
# 工作目录：仓库根。显示要导入的实际小程序目录。
Set-Location -LiteralPath $RepoRoot
$MiniappDir = Join-Path $RepoRoot 'yuweiju-weixin-miniapp'
Resolve-Path -LiteralPath $MiniappDir
```

在微信开发者工具登录后导入该目录，使用现有匹配的小程序 AppID 与本机私有后端配置，不使用不匹配的 AppSecret，也不改用户已有工具项目。现有本地配置及请求使用 `http://localhost:8080`；核对开发者工具本地调试域名设置，保留安全措施。工具服务端口由本机工具实际显示（本次保留43692）；自动化9420与后端8080不是同一个端口，无需为普通手动连接启动自动化服务。经正常登录调用 `/user/user/login`，随后受保护请求带 `authentication`；本次证据涵盖真实 wx.login→身份交换→历史订单查询，未重跑完整 getUserProfile 弹窗点击流程，不代表真机/生产认证验收。

## 正常停止与确认

只停止本次启动的后端/Vite，在各自前台终端按 Ctrl+C。先记录后端 `Started App` 日志 PID，并在第二个终端核对8080/5173的 OwningProcess 与自己启动的进程；不要把已有监听者当作本次进程。停止时留足至少60秒：本次 Tomcat 正常关闭，`analysisTaskScheduler` 等待30秒后记录 timeout，Druid 随后关闭，JVM 自行 exit130。该警告应保留；早期25秒后强制终止不是成功正常停止。不要用 Ctrl+Break 作为 Java 正常停止方式。

```powershell
# 在前面运行过前置检查的终端操作，沿用 Get-LocalListeners 函数。
# 工作目录：$RepoRoot。
Set-Location -LiteralPath $RepoRoot
Get-LocalListeners @(8080,5173)
# 停止前填入已核实属于本次启动的 PID（不是 PowerShell 的 $PID）。
$TaskBackendPid = [int](Read-Host '本次后端 PID')
$TaskVitePid = [int](Read-Host '本次 Vite/node PID')
# 在各启动终端 Ctrl+C 后，等待并核对 PID 与端口两项。
Start-Sleep -Seconds 60
Get-Process -Id $TaskBackendPid,$TaskVitePid -ErrorAction SilentlyContinue
Get-LocalListeners @(8080,5173)
Get-LocalListeners @(3306,6379)
```

成功停止要求本次 PID 不存在且8080/5173监听消失；若出现不同 PID，重新核对所有者。超过60秒仍存活时检查关闭日志与本次进程，不按名称批量终止 Java/node，不自动强杀或停止 MySQL/Redis。只关闭自己新开的验证小程序项目，保留用户原 DevTools及其服务。停止或文档/代码回退均不能撤销 SQL、Redis、模型或外部 HTTP 副作用；本次存储观察范围见证据摘要。
