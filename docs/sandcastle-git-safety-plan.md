# Sandcastle Git 防误操作实施方案

日期：2026-10-09。授权：维护者要求实施 [研究笔记](sandcastle-git-safety-review.md) 的改进项。沿用笔记的首批范围，不重构共享 Git 存储、不新增 hash、baseline 或通用 gate。

## 设计与范围

采用 root 持有的容器内 Git 包装器，逐参数判断操作并检查恢复目标是否已有修改。相较于提示词，它能在实际 Git 调用前拒绝操作；相较于重构为每任务 clone，它保持既有 Sandcastle 生命周期。它属于防误操作措施，不能限制备用 Git 二进制、取消环境保护的替代执行路径或直接改写文件。

当前外部 WSL checkout 仅作为只读来源。改进源码放根 `.sandcastle/`；部署脚本在项目 `.scratch/sandcastle-git-safety/runtime` 创建独立 clone，复制源码、形成单独准备提交并复用已有 Linux 依赖。日志和测试仓库同样位于项目 `.scratch/`。外部旧 runtime 与 #4 成果不改。

组件：`git-policy.mjs` 判断参数、别名及恢复范围；`git-wrapper.mjs` 调用真实 Git 并输出脱敏拒绝消息；`guard-ready.mjs` 检查 root 持有的实际安装；`Dockerfile.git-safe` 从既有镜像派生。`common.mts` 在依赖准备前及代理开始前验证，`business-worker.mts` 给任意任务 prompt 加入拒绝后的行为说明。缺安装则准备失败，不降级为无保护运行。

正常 status/diff/show/log、显式 add、普通 commit、不会丢弃修改的 checkout/restore 继续允许。push、hard reset、实际删除的 clean、强制删除/重写分支和覆盖已有修改的 checkout/restore 默认拒绝；shell alias 不在无人值守代理内执行。允许 clean dry-run。被拒后保留进度、报告阻碍，不允许换路径绕过重试。

三端业务、API、schema 不变；只有开发运行时修改。回退时使用外部保留的旧环境，或撤销项目内本轮运行时准备提交；保留测试 evidence 与 worktree，不清理旧数据。

## 实施与验证步骤

- [x] 编写包装器与判断逻辑；建立无远程、无认证的临时 Git 仓库，断言拒绝后文件及引用保持。
- [x] 构建专用防护镜像；验证非 root 不可修改包装器，缺依赖/缺防护时不执行真实 Git 或启动代理。
- [x] 接入通用 prompt、启动检查与新镜像默认值；保持宿主 Git 不受容器内限制影响。
- [x] 在项目内准备可运行 clone；将准备提交与业务提交分离。
- [x] 运行参数、alias、绝对路径、子脚本、恢复范围及普通提交正反例；通过实际 Sandcastle provider 做无模型完成/取消集成验证。
- [x] 运行既有类型与九例生命周期检查；记录命令、工作目录、退出状态、资源停止、成果恢复和已知绕过边界。

实际结果见 [实施报告](sandcastle-git-safety-implementation.md)。依赖安装在单独 hook 进程使用原生 Git，代理上下文保持默认防护；安装前及代理启动前均检查防护。

验证命令从项目内 runtime 执行：`npm run check:types`、`node --test .sandcastle/git-policy.test.mjs`、`python3 .sandcastle/verify-git-guard.py`、`npm run check:lifecycle`。容器测试使用专用镜像、临时仓库和任务 evidence，不挂认证，不调用编码模型、不写 GitHub或原业务数据库。
