# Issue #13 retro 改进交付

日期：2026-10-09（Asia/Shanghai）。用户批准落实执行报告四项 retro，并授权必要提交、推送、PR、完成后合并及关闭 #13。业务验收见 [执行报告](issue%2313-executing-report.md) 与 [业务集成验证](issue13-integration-report.md)。

## 实施范围

1. `.sandcastle/README.md`、AFK `references/runtime.md` 给出实际核实的 WSL Node 绝对路径 `/home/endercloud/.nvm/versions/node/v22.23.3/bin/node`，要求启动前复核；Java/Maven 检查明确非登录 shell。实际 Node v22.23.3，Git-safe 镜像 Java `/opt/java/openjdk/bin/java`、Maven `/usr/share/maven/bin/mvn`。
2. 通用受保护 prompt 增加“检查批次”与宿主编排 iterations 分工；AFK 参考要求预算轮次仅来自 result/resource。未改变预算、模型、认证或停止控制。
3. `TaskConfig.networks`、Docker provider 和独立 verifier 传入同一显式网络列表，默认不填时沿用 Docker 默认网络；只迁移准备提交 09c2962 的最小接入，不纳入旧 runtime 历史。为保留维护者已升级的防护，同步现有 Git-safe overlay、普通 policy/guard/lifecycle 检查及三份历史支持文档。当前入口说明指向保护流程，旧记录仍保留。独立 verifier 默认产物也转到项目内 `.scratch/`。
4. 观察服务日志使用 operation、掩码 user/order 尾四位和至多八层异常类型/首帧源码位置。完整消息、敏感 Redis/HTTP 正文和 Throwable 日志参数均排除；新增普通测试断言秘密正文、完整 ID 不出现，cause 类型及位置仍可见。尾四位可碰撞，不能替代身份确认。`logOnlineObservation` 没有用户/订单入参，记录 `none`。

业务 API、schema、金额和三端页面未改，前端无需改源码；核心事务/权限失败不进入新增日志降级边界。没有新增 hash、冻结 contract、baseline 或 gate，没有关闭已有 Git 防护。

## 本次验证

代码检查候选含 `81ec53e` 的 runtime/日志源码；随后至 `c6a2eac` 仅说明/报告及业务文档 merge，未改被测源码。工作目录 `.scratch/issue13-retro-integration`，检查容器 user=1000、LANG/LC_ALL=C.UTF-8、非登录 bash，不挂认证、不调用编码模型。

| 检查 | 实际结果 |
| --- | --- |
| `npm run check:types` | exit 0 |
| `npm run check:git-policy` | exit 0，6 tests |
| `npm run check:git-guard` | exit 0，57 cases，containerStopped=true |
| `npm run check:lifecycle` | exit 0，9 cases，取消/停机及保留成果机制通过 |
| `python3 -m py_compile .sandcastle/verify-task.py .sandcastle/prepare-runtime.py` | exit 0 |
| AFK 相对链接检查（runtime/README/三份 support docs） | exit 0，无缺失链接 |
| `mvn -B -Dmaven.repo.local=/workspace/.scratch/backend/m2 -f yuweiju-backend/pom.xml clean package` | exit 0，38 tests，0 failures/errors/skips，BUILD SUCCESS |
| `bash .sandcastle/environment/issue13-check.sh` | exit 0，43 PASS，ALL_SCENARIOS_PASS |
| 实际 `dockerProvider(true).create` 的双网络 socket 检查 | exit 0，MySQL3306/Redis6379 reachable，沙箱关闭 |
| `verify-task.py --config <网络配置> --commit 81ec53e` | exit 0，同两服务 reachable，authMounted/modelCalled=false，stopped=true |
| 具名隔离库只读清理复核 | exit 0，issue13 triggers=0、probe synthetic users=0；人工订单1791543317096保留 |

Maven/probe证据：项目 `.scratch/issue13-retro-verification/{backend-utf8.log,backend-utf8-exit.json,probe.log,probe-exit.json,cleanup-check.txt}`。guard/lifecycle verdict 位于工作树 `.scratch/{git-guard-verification,lifecycle-verification}`；网络 verifier 精确快照 `.scratch/network-verifier/task-review-1791544389324724162`，`check-0.log` 与 `resources.json` 保留结果。管理端四项使用业务 PR 的同一前端源码结果（3 tests、build通过），未为日志修改重复运行。

本轮 guard/lifecycle 已通过后，同一 WSL 进程出现临时 cwd 失效（npm uv_cwd/Python relative import）；重新从绝对项目路径进入后继续，未重启编码代理。独立 verifier 首次遇到 Windows worktree `.git` 路径不能被 WSL 解析，未启动检查容器；通过本次显式 GIT_DIR/GIT_WORK_TREE 指向项目主仓库后运行成功。CRLF 在新增/修改 Linux 源码中统一为 LF；普通 whitespace 检查作为检查，不增加新规则。

测试专属容器均停止；维护者 live 的后端、管理端、gateway、MySQL、Redis五个服务继续运行。未清卷、prune 或访问原业务库。探针只创建/清理自身合成数据及触发器。

## 回退与限制

源码和文档可普通 revert；数据、Redis 或外部副作用仍不会随源码回退。双网络接入不扩大数据库授权；任务准备继续核对命名、隔离和清理责任。Git wrapper是防误操作措施，原生委托二进制与直接文件写入等已知边界保留；不能宣称绝对沙箱。日志可定位 cause 类与代码位置，但为脱敏不保留其正文、完整标识或完整堆栈。原真实存储43场景及人工验收的范围限制继续适用。