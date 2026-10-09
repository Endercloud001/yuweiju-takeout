# Sandcastle Git 防误操作实施报告

日期：2026-10-09（Asia/Shanghai）。依据：[研究笔记](sandcastle-git-safety-review.md)、[实施方案](sandcastle-git-safety-plan.md)及本轮实施授权。

## 交付结果

已在项目内准备可运行的防护环境，构建 `sandcastle:yuweiju-dev-git-safe` 镜像。旧外部 WSL 环境及 #4 成果保持不变；本轮不启动真实编码模型、不挂认证进行测试、不写 GitHub、不操作原业务数据库。

- 可运行目录：Windows `E:/Learning Files/yuweiju-takeout/.scratch/sandcastle-git-safety/runtime`；Ubuntu WSL `/mnt/e/Learning Files/yuweiju-takeout/.scratch/sandcastle-git-safety/runtime`。
- 来源提交：`bcbaa03be56dbd54d0deaa15374e6e9f354b0353`，来自旧通用 runtime。运行时业务文件沿用该来源，不吸收主工作区未提交变更。
- 防护行为验证的代码准备提交：`10c03d96dbdb48f34dcd5b5a158153aba2659828`，分支 `codex/sandcastle-git-safety`。其后仅补齐部署文档封装；最终可选的准备提交读取本机 `preparation.json` 并核对 HEAD。这些是运行时准备提交，不是业务任务成果。
- 防护源码与部署说明保存在主项目 [`.sandcastle/README.md`](../.sandcastle/README.md)。主项目没有替用户提交现有变更；准备 clone 的运行时修改已单独提交。
- AFK [runtime 参考](../.agents/skills/yuweiju-afk/references/runtime.md)已更新到项目内入口。旧配置选择原镜像时准备检查会失败；新任务使用当前示例中的镜像字段及包含防护代码的启动提交。

## 实现行为

容器 `/usr/bin/git` 和 `/usr/lib/git-core/git` 指向 root 持有的包装器。按 Git 参数和 alias 判断 push、hard reset、实际删除的 clean、强制分支操作、强制 checkout/switch，以及覆盖修改的 checkout/restore，在真实 Git 执行前拒绝。拒绝消息只含类别、原因和保留成果/记录阻碍的下一步，不回显敏感命令。

正常检查、显式 stage、普通任务 commit、clean dry-run、纯 unstage 和恢复干净文件继续可用。读取安装的 Git 命令列表，支持正常内置命令及系统 git-upload-pack 等入口的隐含子命令；未知外部 helper、shell alias 和无法安全判断的选项拒绝执行。

`installCommands` 仅在独立准备进程内使用原生 Git，保持已授权依赖安装的正常行为。这个 PATH 不传给代理。准备前及代理启动前分别检查安装归属/权限、入口、实际拒绝及普通 Git 可用性；缺防护则停止，不降级。任意任务 prompt 都会加入拒绝后的处理说明。

宿主 Git 不经过容器包装器。原取消、资源停止观察、分支、进度及未提交成果保留机制继续使用。三端业务源码、API、数据库设计无需修改；已核对准备 clone 三端目录相对来源提交无差异。

## 验证证据

完整汇总在本项目本机临时文件 `.scratch/sandcastle-git-safety/delivery-verification-summary.json`；日志、临时仓库、worktree 和恢复副本均留在项目 `.scratch/` 内。这些本机证据不作为可跨机器访问的文档链接。

| 检查 | 工作目录/结果 |
| --- | --- |
| Docker 防护镜像构建 | 主项目根；exit 0，保留 image-build.log |
| `npm run check:types` | 项目内 runtime；exit 0 |
| `npm run check:git-policy` | 项目内 runtime；6 tests、0 failures，exit 0 |
| `npm run check:git-guard` | 项目内 runtime；57 项实际容器检查通过，exit 0；核对文件/分支保持、正常提交、参数/alias/绝对入口/子脚本、依赖查询、准备上下文隔离、权限与缺策略失败 |
| `npm run check:lifecycle` | 项目内 runtime 驱动项目内小型真实 Git fixture；9 例通过，exit 0：exit7、无完成标记、完成、取消、总时限、SIGINT、SIGTERM、idle、缺提交；核对停机与提交/未提交成果恢复 |
| 三例 Git 防护集成 | 当前镜像，真实 Sandcastle run、Docker provider 和 worktree；完成/取消/缺防护分别 exit 0；保留内容及提交已断言，原镜像未启动代理脚本 |
| 完整运行时三例 | 项目内完整业务 clone，此前完成/取消/缺防护各 exit 0；用于验证 DrvFS 下完整 worktree 路径，不作为业务验收 |
| 最终资源及范围审计 | exit 0；最新检查关联的 13 个具名容器均不存在，准备 clone 干净，三端文件相对来源不变 |
| 文档、Python、空白检查 | 本地链接无缺失；准备/验证脚本可编译；运行时 `git diff --check` 无错误 |

最新镜像下三例集成使用生命周期 fixture，与完整 runtime 使用相同的代理防护、Git 包装器和生命周期路径。可信依赖准备的独立 PATH 由 57 项批次中的实际调用验证；没有通过挂认证或调用真实 Codex 模型来验证这一点。

## 本轮发现并处理的环境问题

1. DrvFS 的完整 checkout 超过 Sandcastle 0.12.0 固定 30 秒创建时限：在原总时限/AbortSignal 内异步准备本轮新 worktree，再交给 Sandcastle 管理。已有 worktree 不覆盖；部分失败目录保留供检查。
2. 新 clone 缺提交身份：仅配置该 clone 的本地默认作者，不改全局身份。首次 clone checkout 失败已保留，准备脚本改为明确 cwd 下恢复文件。
3. tsx CLI 无法在项目内 DrvFS 临时目录建立 Unix socket：改用已有 supervisor 使用的直接 Node loader；后续启动说明采用同一命令。
4. deadline 夹具原 5 秒在启动阶段就耗尽：测试改为 20 秒并保留代理已启动与实际取消断言。正式一轮、30 分钟、600 秒 idle、60 秒 grace 未扩大。
5. Git 委托文件名、系统 symlink 子命令及标准命令发现均通过实际调用修正；一次宿主临时 worktree 删除失败后核对干净状态并正常移除，测试已补充 stderr 保留。没有以强制清理绕过失败断言。

## 边界与回退

本改进用于减少误操作。原生委托二进制、其他 Git 可执行文件、直接文件操作以及策略未覆盖的 Git 操作仍在其能力边界之外；共享 `.git` 挂载没有重构，不能声称任意绕过都已阻止。模型接到拒绝后的记录/停止行为由通用指令约束，本轮未进行付费模型行为试验。

回退可使用保留的旧 runtime，或撤销本轮项目内准备提交并选择相应旧镜像；旧环境不具备新增防护。保留本轮证据、准备分支和 worktree，不自动删除用户或旧任务成果。后续业务 AFK 按具体任务授权另行准备与启动。
