# Issue #12 复盘改进

本轮授权：按执行报告 `/retro` 八项建议实施本地维护；不包含提交、推送、PR、合并或业务 AFK 重启。工作树 `.scratch/issue12-retro-improve-20261011`，分支 `codex/issue12-retro-improve-20261011`，起点为已用 `git ls-remote` 确认的 main `3e7c64b65dc9e8002431238280db5a264cc86d10`。根工作区仍是旧 #4 分支，用户修改、删除及未跟踪文件均保留。

## 现状与方案

| 建议 | 当前证据与本轮处理 |
| --- | --- |
| bridge 与自定义 network 冲突 | main 已含 #13 双网络 provider/verifier；补充 runtime 与配置模板旁的组合限制和具名示例，不新增网络检查规则。 |
| 登录 shell 丢 Java/Maven PATH | runtime 已说明非登录 shell，preflight 已直接执行 bash 脚本；补充实际注入编码代理的通用 prompt，启动前核实路径，不修改历史 #4 prompt。 |
| Vue 迟到 error | #12 已修复，独立管理端 11/11 与浏览器证据已交付；跳过业务源码修改。 |
| swiper/展开/迟到坏图 | 票内驱动已修正；将当前 pane、展开状态和等待实际结果写入人工验收参考，避免固定数量/短延时误判。 |
| 微信旧端口截图限额 | 通用参考尚无本票截图预检；补充每票可用专属端口、项目身份、真实截图与有限超时要求，不清他票存储。 |
| 中文静态资源 | main 没有共享管理端静态服务；新增标准库只读 SPA 服务，使用 URL 解码及 MIME，缺失资产返回 404；在本机隔离目录复现并检查中文路径、路由、错误资源及目录边界。API 代理继续由验收编排提供。 |
| Git index.lock | 保持普通 commit 和 Git 防护；runtime 指引在提交期间宿主使用不刷新 index 的检查，锁冲突保留记录后协调重试，不删锁。 |
| 输出过大/GBK | 已有 UTF-8 输入规范；补充摘要读取明确 UTF-8，完整数据留文件、仅打印判定字段。 |

三端业务源码、接口、schema 和数据均不改。前端只在验收时可选使用静态服务；后端与小程序无需改源码。改进限于通用 prompt、AFK 参考与环境服务，不改变监督器、预算、认证、网络授权或已有防护。

## 验证与回退

逐个参考以具体场景推演：双网络准备、登录 shell 误报、swiper/展开、截图超时、提交锁、中文摘要。服务用隔离 HTTP 测试；通用 prompt 改动运行既有类型与 Git policy 检查，文档使用既有链接检查。其他未变业务检查复用 #12 交付证据，不启动业务服务或模型。维护证据保存在本工作树 `.scratch/retro-verification/`，记录命令、cwd、退出及停机。

回退只需撤销本工作树的所列文件；无数据库迁移或原库写入。不得撤销主线 #12 图片修复或 #13 防护。结果及未执行项在验证后补记。

## 实施结果与证据

八项均已处理：Vue 业务修复跳过；其余七项将现有票内经验落实到通用 prompt、runtime/acceptance 参考、README 或共享静态服务。没有新增依赖、hash、冻结 contract、baseline 或 gate，也没有修改监督器行为。

验证根目录为本维护工作树；实际 Linux 入口为 WSL Ubuntu `python3`，容器工具检查使用 `sandcastle:yuweiju-dev-git-safe`、UID1000、非登录 bash、C.UTF-8；无认证挂载、无模型调用。完整命令和退出在 `.scratch/retro-verification/run.py`、`verdict.json`、各具名日志及 `network-retry/verdict.json`；第一次总结果为失败并保留，修正项以第二次记录为准。

| 检查 | 结果与解释 |
| --- | --- |
| 内置 bridge + 本票自定义网络 | exit125，复现 Docker 限制；两个本票自定义网络修正后 exit0。首轮正例误传 `true` 给镜像默认 sleep 入口导致 exit1，保留日志，仅改 `--entrypoint true` 后重验网络项。 |
| 登录/非登录 shell | 登录 bash 路径查询 exit1；非登录 exit0，Java `/opt/java/openjdk/bin/java`、Maven `/usr/share/maven/bin/mvn`。缺工具误报可复现。 |
| 中文路径 HTTP | 使用未解码 translate_path 的合成旧实现，中文 SVG 请求404、测试 exit1；共享服务测试 exit0。检查三种 SPA 路由、URL 编码中文/空格/查询参数、SVG/JS MIME、HEAD、缺资源及 API404、目录/越界路径，finally 关闭服务器。已读取原票 `.scratch/issue12-afk-20261011/admin-server.py`，其最终版本已有 unquote 修正；本轮为同故障的合成复现，未冒称执行原失败版本或原票服务。 |
| 类型 / Git policy / prompt | 容器 npm ci、check:types、check:git-policy 全部 exit0，6 policy tests；protectAgent 应用场景检查 exit0，任务文本与 opt-out 保留，非登录提示被注入，guard-ready 仍先执行。 |
| 相对文档链接 / whitespace | 链接检查 exit0、无 missing；宿主 git diff --check exit0。 |

人工场景审阅覆盖修改的两个 AFK 参考：准备双网络时只选择本票资源并使用既有 preflight；提交中需要 status 时选择 `--no-optional-locks`；旧9420成功不作为新票截图成功；saveFile失败保留错误而不清他票存储；三个 swiper pane/默认折叠详情先选择当前状态；坏图等待行兜底并有总超时；Windows摘要明确 UTF-8 且不输出 data URL。该审阅验证指引可执行，不冒称重跑官方 SDK 或真实业务验收。

维护创建的具名网络全部成功移除，工具容器已退出，HTTP线程已停止；本机 `docker ps` 为空。读取 #12 coding 的逐轮 resources、independent resources 及 delivery/verification resources，均 stopped=true；最终交付报告与本轮容器观察同时确认 live 已停止。旧执行日志不作为本轮检查证据。没有访问原库、启动 AFK、重新打开 issue 或进行 GitHub 写入。

未执行：全栈、三端业务测试、官方微信截图与真人验收，本轮业务源码未变，复用 #12 交付结论；通用截图规则将在下一次已授权业务票实际准备时应用。本地实施结束时尚未提交、推送或合并，当时没有相关授权。

恢复入口：在本工作树审阅五个 tracked 文件的 `git diff`，并审阅新增 `admin-server.py`、`admin-server.test.py` 和本报告；临时验证代码/日志只留 `.scratch/retro-verification/`。后续交付只能显式选择本轮这八个文件，不纳入用户根工作区或临时证据。

## 授权交付

2026-10-11，用户随后明确授权本轮提交、推送、使用 pr 技能创建 PR 及合并。重新读取远端 main 仍为上述起点，#12 已 CLOSED，没有本维护分支的既有 PR。本轮不重开或重复关闭业务票，也不部署。按项目 workflow 与本报告八项范围审阅最终差异，未发现业务越界或防护移除。

提交后使用统一 `.sandcastle/verify-task.ps1` 在精确候选快照运行 HTTP 回归、runtime 类型/Git policy 与相对链接检查；双网络、shell 与 prompt 的未变实现复用前述证据。交付配置、完整检查、退出/停止与远端确认记录保存在本工作树 `.scratch/publication/`。合并状态以 GitHub 实际返回为准，本文不预写成功。
