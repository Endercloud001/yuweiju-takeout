# Sandcastle 开发与无模型验证环境

WSL 工作目录 `/home/endercloud/projects/yuweiju-sandcastle-env`，分支 `codex/sandcastle-environment-20261007`。宿主 evidence `/home/endercloud/projects/yuweiju-sandcastle-env-evidence`，输入 `/home/endercloud/projects/yuweiju-sandcastle-env-input`。这是复用原独立 clone 的新 worktree，不切换/覆盖 #4 分支或证据。

## 镜像与隔离数据库

以下在 WSL 的登录 bash 中执行；它加载 Linux Node `/home/endercloud/.local/bin/node`。不要使用 `wsl -- npm`，本机那种调用会解析到 Windows npm。Java/Maven 在容器内，不以 WSL 找到的 Windows Maven 代替。

```bash
cd /home/endercloud/projects/yuweiju-sandcastle-env
npm ci
npm run check:types
docker build -t sandcastle:yuweiju-dev -f .sandcastle/environment/Dockerfile .sandcastle/environment
docker compose -f .sandcastle/environment/compose.yml up -d --wait
python3 .sandcastle/environment/verify.py --commit ecd2029
```

`verify.py` 从指定本地 Git 提交提取新 tracked snapshot；全新容器以 UID1000构建，Maven package 包括全部19测试，管理端 npm ci/lint/typecheck/test/test:refimg/build；检查脚本在新容器运行。安装阶段 bridge 网络只下载依赖，运行阶段内部网络隔离 MySQL/Redis；不挂认证、不调用编码代理或模型。Spring config.location 替换配置搜索，不加载个人 .env/application-dev.yml。启用 dev,afk 是因为天气/店铺/微信实现原本按 dev/prod 注册，afk 独占 profile 无法启动；隔离参数与任务开关仍使用本目录配置。

MySQL/Redis 不发布端口；MySQL仅这个隔离网络使用空口令，绝不能作为正式配置。schema.sql 是示例中24个建表语句的独立副本，不含 DROP/原账号/原商品数据。fixture.sql 明示人工合成数据。初始化期间不要改挂载的 SQL 文件；健康检查核对24表及夹具，不以临时 mysqld ping 冒充初始化完成。已有本轮卷部分初始化失败时，可启动服务后只对这两个具名隔离服务导入 schema.sql/fixture.sql；两文件可重复导入，不删原卷、不清表。

## 运行时重验与训练

运行时修复后可以复用已验证构建的 Linux snapshot：

```bash
python3 .sandcastle/environment/verify.py --commit ecd2029 --runtime-only /home/endercloud/projects/yuweiju-sandcastle-env-evidence/environment-review-1791354591421015527/snapshot
```

真实 Spring代理/MyBatis/MySQL：地址批量写入第二笔失败回滚；实际订单 Service 明细触发器失败回滚订单/明细并保留购物车。只有地图是测试专用 Bean 替身；不以Mockito替代事务。内部触发器在finally移除。XGBoost JNI真实训练/文件保存/重载/预测，Smile真实聚类和逻辑回归；真实 AliOss SDK只写本地HTTP替身。HTTP夹具仅返回坏输出/402/503/超时，不调用真实模型。

训练使用单独项目和新的数据卷；网络名与下面 -p 一致。默认普通夹具库用于事务/页面验证，不拿训练污染普通夹具：

```bash
docker compose -p yuweiju-sandcastle-training -f .sandcastle/environment/compose.yml up -d --wait
python3 .sandcastle/environment/verify.py --commit ecd2029 --runtime-only /home/endercloud/projects/yuweiju-sandcastle-env-evidence/environment-review-1791354591421015527/snapshot --training --network yuweiju-sandcastle-training_isolated
```

训练入口要求隔离库 orders 为空；这是防止覆盖本轮已保留训练材料的使用边界，重复训练应换一个新的 Compose项目名/数据卷（例如 `-p yuweiju-sandcastle-training-next` 及对应 `_isolated` 网络），不删除旧模型或清卷。人工合成订单/明细/特征与approve/reject标签覆盖实际90日训练+30日holdout、至少50训练正例。使用现有 Service 新训练/保存/预测/评分并核对SQL和文件；不改变算法、阈值或发布判断。合成数据结果不能作为业务风险标签/效果证明。每轮 `/runtime` 都绑定新证据目录，原模型从未挂载。

## 宿主连接及正常停止

可将已构建隔离 jar 启动成独立开发服务：

```bash
export SANDCASTLE_SOURCE=/home/endercloud/projects/yuweiju-sandcastle-env-evidence/environment-review-1791354591421015527/snapshot
export SANDCASTLE_RUNTIME=/home/endercloud/projects/yuweiju-sandcastle-env-evidence/dev-runtime
mkdir -p "$SANDCASTLE_RUNTIME"/uploads "$SANDCASTLE_RUNTIME"/analysis-models "$SANDCASTLE_RUNTIME"/order-risk-models
docker compose -f .sandcastle/environment/compose.yml --profile dev up -d gateway
curl --noproxy '*' http://127.0.0.1:18080/user/shop/status
docker compose -f .sandcastle/environment/compose.yml --profile dev down
docker compose -p yuweiju-sandcastle-training -f .sandcastle/environment/compose.yml down
```

dev后台仍只连接内部网络，gateway只转发到 `backend:8080`，发布 `127.0.0.1:18080`，不提供任意目的代理。只把backend放内部网络时，本机Docker29.8.1未实际发布HostConfig中的端口，已用这个固定目标网关修复。可由Windows访问18080；Windows原生短临时目录/JDK启动尚未复现。

## 微信原生隔离夹具

在 Windows PowerShell，若 UNC 路径被现行签名策略拒绝，将此脚本及 wechat-fixture 复制到本轮本地临时目录再调用；不修改执行策略。执行本目录 `verify-wechat.ps1`（CLI/目录/IDE端口/自动化端口可配置）。复用本机已登录工具，不读取或复制账户资料，不上传/preview/publish。默认创建本轮 .scratch 中的游客夹具，不打开业务项目。保存后原生编译、截图只证明环境能力；#5/#6仍无业务可重建源码方案。

WSL已安装独立 automator 在 evidence/probe-tools；容器有全局 automator0.12.1。WSL连接 `ws://127.0.0.1:9420`；本机容器实测连接 `ws://192.168.10.10:9420`。这个地址只适用于本机本轮WLAN，应根据宿主实际地址设置 WECHAT_WS_ENDPOINT，不写死到业务代码。Docker host.docker.internal与host网络localhost连接均失败，日志保留。

```bash
docker run --rm --name sandcastle-wechat-check --entrypoint node   -e WECHAT_WS_ENDPOINT=ws://192.168.10.10:9420   -e WECHAT_SCREENSHOT=/evidence/wechat-check.png   --mount type=bind,src=/home/endercloud/projects/yuweiju-sandcastle-env/.sandcastle/environment,dst=/environment,readonly   --mount type=bind,src=/home/endercloud/projects/yuweiju-sandcastle-env-evidence,dst=/evidence   sandcastle:yuweiju-dev /environment/wechat-check.cjs
```

检查仅允许 pages/probe/index 并60秒超时；不读业务页面、不提交/下单。Windows停止用 `verify-wechat.ps1 -Close`，只关闭本轮项目，保留宿主IDE及其登录。page.data()曾超时，官方currentPage/screenshot成功，图已人工查看；不以数据JSON代截图。详见 miniapp-compile-investigation.md。

## 后续任务配置与独立复核

task-config.example.json 是模板，必须指定新分支、真实本地启动提交、prompt文件、只读input、镜像、安装与独立检查命令。既有 #4 分支被保留；现存任务分支不能自动重跑，须先检查其保留成果。专用 authDirectory指向原仓库外目录，不复制个人Codex配置。普通开发配置不带认证；只有另行授权的实际编码入口使用专用认证。

环境自检只运行本地夹具和独立检查；业务编码另按任务授权执行：

```bash
npm run check:lifecycle
python3 .sandcastle/run-config-fixture.py
python3 .sandcastle/verify-task.py --config /absolute/task-config.json --commit LOCAL_COMMIT
```

生命周期9例包括正常完成、exit7、无标记、取消、总时限、SIGINT/SIGTERM、空闲超时、缺提交。结果区分代理完成、独立验证和人工接受；缺提交返回未完成，不补提交或自动续跑。分支/工作tree/log/result/resource/progress按原机制保留；清理以具体容器和挂载归属为依据，不prune或全局停止。配置夹具从ecd2029另起分支，实际读取prompt/只读输入、使用指定Java镜像、提交一项人工文件并保留未提交进度；独立四条配置命令0退出，均不挂认证。

当前Issue材料在input目录；Windows用已安装gh读取正文/评论/状态，不复制GitHub认证。可从Windows运行prepare-input.py并指定 `--source-root 'E:/Learning Files/yuweiju-takeout' --issue 14 --output <隔离目录>`，用 `--material <相对路径>` 添加适用API/数据库文档。当前材料应在新票启动时刷新，不把本轮快照当永远最新。缺失历史日志/截图明确已清理，不恢复、不重跑原库写入。

失败恢复：读取本轮 resources.json/result和exit-status.tsv，确认具名容器/进程状态；必要时只stop/rm本轮资源。强杀/断电不保证finally，先核对拥有的容器与worktree。提交从任务分支获取，未提交成果/进度从保留worktree读取；恢复到新副本，不覆盖 #4 或原工作区。修复验证脚本后用新容器重验相应步骤，保留首轮失败日志。


## Issue #19 复盘改进（2026-10-08）

开发 provider、独立 verify-task 和 environment verifier 均显式设置 LANG/LC_ALL=C.UTF-8，镜像重建后也采用该默认值；中文 Excel 模板无需改名。resource 仅记录 Java 路径/版本及 locale，独立检查的 environment.log 记录同样信息，实际 checkCommands/逐步容器命令保留在既有资源结果中，不记录认证变量。

独立 environment verifier 默认挂载被验收 snapshot 中的 .sandcastle/environment，包括其业务探针。宿主 verifier 版本与候选 environmentCommit 分开记录；runtime-only 的 environmentCommit 留空，只记录调用者声明的 declaredCandidateCommit 和实际 snapshot 路径，不能据此证明旧快照来源。运行 #19 候选时，将 --commit 设为 5e9bc6d；runtime-only 指向已独立构建的同一候选 snapshot，按所属隔离 Compose 项目指定 --network，不以准备分支脚本替代候选探针。

编码运行的 Maven/npm 缓存绑定到本次 evidence 下独立 dependency-cache，跨本次迭代复用，保留退出状态、不自动重试；后续任务与独立验收不共享此缓存，独立构建仍检查干净容器。预装全部业务依赖暂不采用：依赖随候选变化，缓存已有本轮最小复用且不需要延长镜像构建。资源文件保持原顶层兼容字段，并追加 iterations 历史，收尾核对每轮具体 containerId；结果不代表业务验收通过。

这些调整只涉及 Sandcastle 开发/验证运行时；后端、管理端、小程序业务代码和公开接口无需改变。回退本次运行时提交即可恢复原启动行为，保留的 evidence/cache/旧 #4 成果不删除。
