# 启动前交接

先查 issue、依赖、当前代码、main 与旧报告。记录已确认 main 提交、runtime/verifier 来源，新准备默认复用该来源的工具，业务起点按 issue 单独确认；确需旧工具时说明兼容原因，不为更新工具偷偷升级业务范围。标记已解决项并跳过；本机工具、路径、网络、端口等可查事实由 Agent 核实。保留根目录用户修改；需要隔离时在项目 `.scratch/` 下建工作树。准备说明不表示已经启动 AFK。

## 一次收集五项

| 项目 | 发给用户的信息与缺项 |
| --- | --- |
| 目标与范围 | issue、消费者、验收项、三端影响；旧模块先调查实际入口与风险，再收集确需人工的去留决定 |
| 人工动作 | 真实账号由本人在页面输入；真实微信测试账号及配置入口；只能记录脱敏“已就绪”，密码/token/密钥不进入聊天 |
| 业务决定 | 已知决定沿用；缺项给事实、选项与影响；仅调查就明确后续仍待决定 |
| 操作授权 | 记录隔离验收服务启动/保留/停止、提交/推送/PR/合并/同步/关闭分别是否已授权；Release 和部署另记具名目标与范围 |
| 预算与恢复 | 最大轮数、编码总时间、验证时间、失败后交接、人工等待及服务到期处理 |

一次消息列出已知项和真正待答项；回复可以一次补齐。普通实现选择、可查事实及已授权动作无需重问。新的业务范围、副作用或权限变化要说明具体原因。真实验收能力缺失时允许明确选择“交付候选，issue 等待验收”；候选验证不能代替人工声明。

## 实际环境与恢复

读取实际 checkout 的 `.sandcastle/task-config.example.json` 和 `task-config.mts`。用同一 image/networks 执行：

```sh
python3 .sandcastle/preflight.py --config /absolute/project/task/config.json
# 编码入口会在同一配置上重复预检，并只读检查专属登录状态。
SANDCASTLE_TASK_CONFIG=/absolute/project/task/config.json node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/main.ts
```

预检不调用模型：检查非登录 shell 的工具、Git guard/元数据、HTTPS DNS/TLS 与显式 `preflightServices` 的 socket；配置 `preflightUrls` 为当前模型服务的无凭据 HTTPS 入口。HTTP 4xx 能证明服务可达，不能证明身份或模型请求成功。认证仅检查 exit，不输出认证正文。数据网络继续 internal，另接具名出口网络；服务 host/port 必须事先核对属于本票隔离服务。预检只连接端口，不进行业务请求。端口选择与已有服务归属另查。

失败分类记录在 `preflight.json`：工具、Git、防护、网络/服务、认证、超时、取消或运行时故障。配置错误在编码启动前修正，不消耗业务轮次。Linux 临时脚本在写入处使用 UTF-8/LF，复用统一 verifier 的路径转换。GitHub 写入超时按 issue-tracker 先读远端再重试。

编码运行内的明显命令/路径错误可在同轮修正并重跑受影响检查。已经结束的不完整、失败、取消运行继续遵守 SKILL 第 4 步：明确授权后才可续跑。启动前可以收集用户对具名技术恢复的显式授权，宿主执行前仍需核对停止记录、剩余原预算/轮数、资源归属及具体原因；监督器不自动重启。用户取消、认证失败、越权、未知数据副作用或归属不明必须交接，不能使用技术恢复授权。每次恢复保留原输入/result，使用新输入目录，预算不会重置。

## 预算与逐轮证据

恢复配置只分配原授权剩余 `totalMs` 和 `maxIterations`：从原 result/resources 的开始、结束和真实轮数扣除已消耗额度，并记录原运行路径与计算。未知耗时不能按零计算；一轮/30分钟已耗尽就不再启动，额外预算/轮数需要新的明确授权。具名技术恢复许可不能增加额度。

`totalMs` 是所有编码轮次共享总上限，含 worktree 准备、安装、编码、检查与收尾。默认一轮、30 分钟保持不变。复杂全栈任务可建议两轮/60 分钟编码和20分钟独立验证，须按用户实际选择写入配置；用户30分钟就保持30分钟。`verificationMs` 单独供独立 verifier 使用，未填时兼容原 `totalMs`。`closingMs` 是原总预算内的提前收尾时间，不延长时限，也不是独立单轮预算。

监督器在 deadline 前写 `/afk-evidence/closing.json`，prompt 提供绝对截止时间，要求每步后检查并保存候选提交、剩余修改、检查与阻碍。时限到照常 abort/停机。模型未读取提示或 SIGKILL 时无法保证提交成功；ignored 证据采用 Agent 包装器退出前保存与5秒采样，最后5秒以及尚未完成的写入可能缺失，明确记录限制。

`evidencePaths` 选择本票进度和检查目录，如 `[".sandcastle/task-progress.md", ".scratch/issue7"]`。各轮在宿主 `<run>-iterations/iteration-N/` 保留脱敏文本、HEAD/status、Agent exit/signal、观察到的完成文本、开始/结束时间；resource/result 记录监督器轮数、资源、截止与最终状态。允许的文本扩展、大小上限和跳过理由见 `save-iteration.py`，不复制认证目录、二进制、node_modules 或业务库。脱敏只能处理常见命名秘密，源日志仍须不输出敏感 HTTP/SQL 正文；提交或分享前审查证据。超限/跳过不算已保存。

下一轮承接上轮提交并读取 `/afk-evidence`，ignored 文件不会自动回填工作树。保存的 evidence、失败输入与旧日志不以重跑覆盖。轮数以 resource/result 为准，重复检查称检查批次。采样和收尾不产生额外模型请求；安装/编码/检查具体阶段时间由进度记录，包装器只保证实际开始/结束，不猜测阶段耗时。

超时候选交给宿主精确提交验证；通过后可以交付候选，但编码状态仍为超时。开发、独立验证、等待人工和交付各有独立状态；缺失 result 或 COMPLETE 文本不能补写成功。
