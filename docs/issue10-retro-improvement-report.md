# #10 宿主复盘改进结果

2026-10-10，按用户指定的 yuweiju-retro-improve 在当前聊天实施。[方案](issue10-retro-improvement-plan.md)说明范围、现状和回退。交付摘要的对应环节实际位于 [#10 执行报告](issue%2310-executing-report.md)；本轮处理其六项建议，保留历史失败与最终人工验收记录。

实现位于 `E:/Learning Files/yuweiju-takeout/.scratch/issue10-retro-improve-20261010`，分支 `codex/issue10-retro-improve-20261010`，HEAD/main 起点 af04c520666f692a2a355a7bfca5658061162d4d。Ubuntu 只读远端确认同一 main；Windows Git 首次连接443失败，未修改永久网络配置或凭据。根工作区仍在原分支，用户修改和其他任务资源保留。

## 逐项结果

| 建议 | 实施与验证 |
| --- | --- |
| 真实金额链路 | 已解决，跳过业务修改；复用 d0f1874 的真实 HTTP/MySQL/Redis 及74测试、checkout14测试和最终人工21/40/旧38证据。startup 补齐报价/响应/两端详情/SQL比较，明确 amount 回显替身的限制 |
| prompt 输入路径 | git-safe-agent 的最外层提示首行增加只读 `/home/agent/task-input` 和 `/home/agent/workspace`。原 Git safety/readiness 完整保留。runtime 要求 task prompt 首行列实际已存在 summary；真实 protectAgent 无模型捕获通过，smoke 提示保持原样 |
| JDBC/schema | 现有 issue10-check.sh 已从完成的 Boot jar 提取真实依赖，无需重写。startup 补齐安装后驱动加载/只读 schema 检查成功再写入的顺序。场景审阅：猜测驱动目录失败归准备失败；不会进入写入或迁移 schema |
| Mockito 重复设桩 | 本票已解决并通过74测试，不改业务夹具。startup 给出先异常后正常的 doReturn 操作，保留失败并完整重跑受影响集合。场景审阅：普通 when 在设桩时调用旧 throwing mock，应改 doReturn；正常设桩不批量替换 |
| 官方/人工与截图 | 最终人工验收已通过，跳过重复业务验收。acceptance 补齐受信任项目、维护者配置保留、编译确认、有限截图超时及自动/官方/人工分别记录。场景审阅：未确认编译时不运行依赖新产物驱动；截图超时保留其他结果但不补写人工通过。没有修改官方 SDK 驱动或宣称现场截图实测 |
| 独立 npm 下载缓存 | Python `--npm-cache` / PowerShell `-NpmCache` 可显式挂载已有目录到 `/npm-cache`，设置 npm_config_cache 并记录来源；默认不挂载。真实 Windows→Ubuntu→Docker 含空格路径验证通过，新快照离线安装340包并完整检查。缺包仍失败、后续检查未执行 |

无未选或受阻条目。部分建议通过现有实现/历史业务证据跳过重复源码改动，准备和验收指导仅验证文档场景，没有冒充监督器/SDK新能力。#13 既有 Node、UTF-8、网络和脱敏日志，以及 #7 preflight/预算/证据/停机行为保留。

## 本轮验证

宿主命令工作目录均为上述维护树。独立命令实际 cwd 为各资源记录中的新 snapshot，容器 `/workspace`；检查的应用提交为 af04c520。运行 verifier 的 Python/PowerShell 则是维护树未提交的新源码，因此本轮验证新入口和缓存行为，不宣称未提交技能文件已进入提交快照。

证据根：维护树 `.scratch/retro-verification/`。

| 检查 | 结果及证据 |
| --- | --- |
| TypeScript 全 runtime 类型检查 | exit 0；typecheck-result.json/typecheck.log，使用真实 tsc 和全部 .sandcastle/*.mts/main.ts |
| 真实 protectAgent 提示捕获 | exit 0；prompt-check.mts/prompt-result.json，首行定位、原 safety/readiness、smoke 分支通过；仅映射源码检查，无现场编码模型 |
| Python compile / PowerShell parser | exit 0 / 0 errors；prepare-checks.py；编译不向项目外写 bytecode |
| 文档相对链接及 git diff --check | exit 0；覆盖改动参考、方案及报告；CRLF提示保留，不是检查失败 |
| 无缓存统一 verifier | exit 0、stopped=true、npmCache=null；default-run/task-review-1791645364389873405/resources.json；容器未见 /npm-cache、环境未设置缓存 |
| 可选缓存新快照 | 两组 exit 0、stopped=true；cache-run-1/task-review-1791645399055322100/resources.json；npm ci --offline 安装340包，lint/typecheck/3 tests/build通过 |
| 缓存缺包失败新快照 | 预期 exit 1、stopped=true；cache-failure/task-review-1791645802174681386/resources.json；仅修改该失败快照 package.json 的 Vue 为不存在于缓存的999.0.0，npm ENOTCACHED，第二组 not-performed |
| 最终容器查询 | Docker ps 名称列表为空；不 prune、不清卷、不操作其他任务 |

缓存来源为项目内此前 #10 独立快照的 `.scratch/issue10/admin-npm-cache`，确认没有活动任务使用后显式复用；其下载内容及 npm 新日志保留，不复制 node_modules/旧通过证据。所有三个 verifier 记录 authMounted=false、modelCalled=false、humanAcceptance=not-performed。缓存未默认启用、未建立自动共享策略；离线验证证明可复用充分下载的内容，不宣称性能提升或冷缓存联网下载已测试。构建原有大 chunk 警告保留。

首次本机验证有两类调用错误，未当作业务失败或通过：WSL 相对路径重复，未生成配置；直接 Node 的 `*.mts` 不经过 shell 展开，tsc TS6053。更正为实际绝对路径及 Python glob 后通过；一次 Python 汇总遇到 WSL cwd 失效，显式 chdir 到绝对路径后重新检查并保存成功证据。保留在本报告，不启动或重启 AFK。

## 边界与恢复

三端业务源码、接口、schema、原库与发布状态不变，未访问原库974，未创建业务订单或启动人工验收服务。只有失败验证快照的合成 package.json 临时变更；真实应用候选和用户工作区未改。未新增 hash、冻结 contract、baseline 或 gate，既有 Git/认证/数据防护保持。

维护实施完成时，改动尚未提交、推送、创建 PR、合并或部署；业务 #5/#10 没有重开。维护者随后明确授权本维护改动的提交、推送、使用 pr 技能创建 PR 和合并，部署仍不在范围内。恢复评审从维护树、本报告和方案开始，原交付摘要及历史证据继续保留。停用缓存时移除参数即可；源码可普通 Git回退，缓存和证据保留，代码回退不代表撤销数据或外部副作用。实际远端交付结果以本维护 PR 和宿主 publication 记录为准。
