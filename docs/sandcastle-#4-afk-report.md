# Sandcastle #4 单轮试点与独立复核报告

日期：2026-10-07（Asia/Shanghai）。依据维护者授权执行步骤 7–8。

**状态：代理报告完成；独立复核通过；维护者已审查并接受。** 接受依据为维护者后续明确回复“已审查，接受并继续推进”。代理遗漏了任务提交，宿主在独立验证通过后显式提交四个授权文件。试点运行已经停止，集成及下一项任务另行记录。

## 起点与成果

| 项目 | 实际记录 |
| --- | --- |
| 独立 clone | `/home/<user>/projects/yuweiju-afk-issue-4` |
| 准备提交 | `9b5078fdee4eda7f2620445a68498b3a95a51e53`；启动前 HEAD 一致、准备工作区干净，任务分支/worktree 尚不存在 |
| 任务分支/提交 | `codex/afk-issue-4` / `23d3e5b0578e6ba0ecd4e954e84cacd0687553f7` |
| 提交来源 | 宿主在独立复核后提交；4 files changed、143 insertions、12 deletions |
| 模型设置 | `gpt-6.1-sol / medium / Standard`；service_tier 未设置、fast_mode=false，与既有 worker 一致 |
| 认证与费用 | 复用专用持久 ChatGPT 认证；原 hook 执行 login status。没有登录/注销、手动修改认证、换 API 或购买额度；实际单轮正常结束，未出现认证/模型/额度异常 |
| 时限 | 1 轮，运行含 hooks 最多 30 分钟，idle 600 秒、completion grace 60 秒；未修改监督/IPC→AbortSignal/finally/资源审计实现 |
| 运行时间 | 11:57:19.288–12:01:16.689，约 3 分 57 秒；编排 exit 0、iterations=1、完成标记存在，未超时 |
| 工具 | 复用 Node 22.23.3、npm 10.9.9、Sandcastle 0.12.0、tsx 4.23.15、Codex CLI 0.160.1；同一 `sandcastle:yuweiju-afk-issue-4` 镜像，无重装/升级 |

相对准备提交仅改变管理端四个文件：

- `eslint.config.js`：仅为 `scripts/**/*.cjs` 配置 CommonJS、Node globals 和 require 适配，其他目录/规则继续参与完整 lint。
- `scripts/copy-ref-images.cjs`：默认源改为仓库素材目录，位置参数为 `[sourceDir] [destinationDir]`；复制失败设置非零退出，继续尝试其余顶层文件，保留覆盖、不递归、不删除语义。
- `scripts/fix-refimg-imports.cjs`：位置参数为 `[scanRoot] [assetsDir]`，按 Vue 文件位置计算素材相对路径，仅转换带 query 的别名并保留 query。
- `scripts/refimg-scripts.check.cjs`：Node 内置测试、真实脚本子进程、临时隔离夹具，无新增依赖，未被 Vitest 误收集。

页面、API、响应类型、路由、状态、后端、小程序、业务依赖及安全/忽略配置没有差异，其他两端无需修改。原始起点 `ab200a7f9b75f3ef30281e8241e70fa7b37b437d` → 准备提交仍为已有 21 个准备文件；准备提交 → 任务提交只有上述四个文件。Windows 原工作区用户变更保留。

## 独立复核

新 detached worktree 从准备提交取得仓库，复制代理保留的四个候选文件。新容器使用同一镜像及 UID/GID 1000:1000，不挂认证/技能、不调用模型；重新 npm ci，不复用代理或 Windows node_modules。全部验证通过后，宿主确认候选与复核副本逐字节一致，显式 stage/commit，随后再次确认提交内容与复核副本一致。

容器命令工作目录：`/home/agent/workspace/yuweiju-web-vue/yuweiju-admin`。

| 命令/检查 | 独立结果 |
| --- | --- |
| `npm ci` | exit 0 |
| `npm run lint` | exit 0；完整检查，原 22 条错误不再出现 |
| `npm run typecheck` | exit 0 |
| `npm run test` | exit 0；Vitest 2/2 通过 |
| `npm run build` | exit 0；保留两个约 1048/1058 kB chunk 超过 500 kB 的警告，未调整阈值 |
| `node --test scripts/refimg-scripts.check.cjs` | exit 0；4 pass、0 fail、0 skip、0 cancelled，覆盖全部六组矩阵 |
| 额外默认路径检查 | exit 0；在临时模拟工程从外部 cwd 无参数执行，验证默认复制源/目标及导入路径；也验证扫描根之外的配置素材目标 |
| Git 检查 | diff/cached 检查通过；只有四文件入提交，提交内容等于复核副本 |

| 必须行为 | 实际观察 |
| --- | --- |
| 两个顶层文件和子目录 | 文件内容正确，子目录未复制 |
| 同名目标、无关文件、重复执行 | 覆盖正确，无关文件保留，两次结果一致 |
| 源不存在 | 非零退出，不创建目标；已有目标也保持不变 |
| 非 root 部分目标不可写 | 真实 EACCES、复制 exit 1；成功文件复制，失败目标保持原内容 |
| 根层、多层 Vue 带 query | 相对路径指向配置素材，`?url`/`?raw&x=1` 原样保留 |
| 无 query、无关导入、非 Vue、重复转换 | 内容不变，重复运行不继续改写 |

测试只向临时夹具写入，结束清理本例目录，未向真实素材/src 写测试数据。代理日志记录 lint 修复前 22 errors；复制失败测试修复前 3 pass/1 fail、修复后 4 pass/0 fail。独立验证重新执行真实脚本，没有仅信任代理完成标记。

首次独立启动遗漏 entrypoint 覆盖，镜像默认 `sleep infinity` 将验证命令当成 sleep 参数，exit 1；没有执行项目检查。宿主修正验证启动命令后通过，不改候选代码或镜像，失败日志与资源记录保留。启动前认证配置与示例的文本比较不完全一致，只读取非敏感字段确认固定设置一致，未修改认证。

安装报告当前依赖有 23 个漏洞（1 low、5 moderate、15 high、2 critical），本任务不自动 audit fix/升级。未验证 Windows 执行、页面视觉、真实数据库联调或生产发布；没有改善前后对照实验，不声称 AFK 效率提升比例。

## 证据、保留与停止

证据根：`/home/<user>/projects/yuweiju-afk-evidence`，完整日志留本地，报告不含凭据。

- 编排：`codex-afk-issue-4-1791345439288.log`、同名前缀 `-result.json`/`-resources.json`，控制台为 `issue-4-authorized-run-console.log`。原 result 的 commits=[] 如实保留；宿主后续提交另记在 delivery 中。
- 任务 worktree：`/home/<user>/projects/yuweiju-afk-issue-4/.sandcastle/worktrees/codex-afk-issue-4`；成果已提交，仅未跟踪 `.sandcastle/issue-4-progress.md` 保留，步骤日志在 `.sandcastle/logs/issue-4-*.log`。代理原进度记录未提交，保留原文。
- 独立副本：`issue-4-independent-review`；日志：`issue-4-independent-review-logs/{install,lint,typecheck,test,build,behavior}.log`、`exit-status.tsv`、`resources.json`。首次启动失败在 `attempt-1-entrypoint/`，额外默认路径检查为 `default-path-check.cjs`/`.log`，不加入项目 gate。
- 恢复副本：`recovery/issue-4-1791345439288`，保留四文件、原进度、完整步骤日志及宿主状态说明，不复制凭据/node_modules，不自动恢复运行。
- 交付与差异：`issue-4-delivery.json`、`issue-4-task.patch`；停止及差异审计：`issue-4-final-audit.json`。

12:04:53 最终审计确认正式容器 `a89821bce80d56299130fae30093b890e3f64202ae983858e66240ab4b32fea1`、独立复核容器 `87c90e71518ebd8312d3f9f1dea536f8ee117fc81bc3253e4051cadd2f090085`、首次错误启动容器 `faf27b79d3ee0177b19a12a7d7ba8851269bfbfdc850d8820b24e09adca81941` 及额外路径验证容器均已移除。worker PID 9236、hook PID 9369、独立宿主 PID 9657、docker 客户端 PID 9658 已停止；按具体资源检查，未全局清理。

**人工审查已完成并接受任务提交，随后明确授权集成四个文件到原工作区；集成结果见下节。** 本轮没有模型续跑、GitHub 写入、push/PR/merge/issue 关闭、原库操作或发布。分支、进度、日志及恢复副本继续保留，没有因接受而自动删除。

## 已接受成果集成到原工作区

维护者明确确认后，在 `<original-workspace>` 的 `main`，从原 HEAD `ab200a7f9b75f3ef30281e8241e70fa7b37b437d` 本地 cherry-pick 已接受任务提交，形成新提交 `219548e155b2152af56759535c3b7cafc8fddc7f`。仅通过 WSL 本地路径取得 Git 对象，未访问或写入 GitHub，未合并准备分支。

集成前检查四文件无用户修改、暂存区为空、无进行中的 merge/cherry-pick，补丁应用检查通过。集成后检查提交只改变上述四文件；四文件的 Git 内容与已接受提交完全一致，工作区四文件无额外差异，`git diff --check HEAD~1 HEAD` 通过。原有 PLANS.md/后端文档删除及全部未跟踪文件保留，未 reset/clean/stash，不将报告等用户未跟踪资料加入提交。

本次集成只进行内容、范围和工作区保留核对，没有修改候选实现或重复安装/调用模型；前述独立 Linux 容器验收对应同一份四文件内容。Windows 运行仍未验收，不能把内容一致性检查称为 Windows 行为测试通过。集成证据：`issue-4-integration.json`，位于原 evidence 目录。保留试点分支、worktree 和全部证据。

## 复盘改进记录

维护者授权逐项改进后，本轮完成五项调整，没有调用模型或重新启动 #4：

1. 任务摘要及正式 prompt 明确“检查通过 → 显式提交授权文件 → 核对提交范围/剩余状态 → 输出完成标记”。宿主结果新增 `incomplete-missing-task-commit`；有标记但本轮 commits 为空仍返回未完成、worker 非零退出，不自动重试或补提交。该状态用本次真实 AFK 的缺提交结果、已提交 smoke 结果及无标记结果回放验证。
2. 素材行为测试明确要求 Linux；Windows 原生给出说明并非零退出，不跳过权限用例。独立验收仍使用非 root Linux 容器，不声称 Windows 权限验收通过。
3. WSL 新增 `.sandcastle/verify-admin.py`/`.sandcastle/verify-admin.sh`，复用固定镜像、`--entrypoint sh`、UID/GID 1000:1000。从明确提交建立新的 detached worktree，或复核 evidence 下明确标注的隔离候选副本；新容器执行 npm ci，安装限 5 分钟、全程限 30 分钟，不挂认证、不调用模型。
4. 管理端规范已移除“本机绝对路径”“lint 待 #4 修复”的过时描述，说明实际路径参数、写入行为和隔离测试要求。
5. 管理端新增 `npm run test:refimg`，与 `npm run test` 相邻记录；前者执行 Node 素材测试，后者执行 Vitest，两个入口不可相互代替。原显式 Node 命令仍有效；验证入口采用该命令，也兼容尚无 npm 别名的历史任务提交。

本地提交：

| 位置 | 提交与范围 |
| --- | --- |
| 原工作区 main | `7767930e278ba2fc93204cdf9b1277431069cabf`，仅管理端规范、package.json 测试别名及行为测试的平台说明，共三文件；依赖和锁文件不变 |
| WSL 准备分支 | `44b2e6e` 导入已接受 #4 修复，`8c05a2b` 同步上述三文件；`2fd5f71e1b45cf9442d91a0a541a66a4c26aad5b` 为最新准备提交，含九个编排/资料文件的改进 |

静态摘要已部署到原 input/documents/issue-4-summary.md，内容与新准备提交一致。历史任务分支、原运行结果及原代理进度没有改写；准备 README 明确已接受 #4 不自动重跑。后续任务须另行明确授权范围、分支和启动提交。

本轮验证：

- TypeScript 检查、Python 编译、shell 语法检查、完成状态的真实结果回放均通过。
- 新验证入口分别通过隔离候选模式及最终准备提交模式；最终新容器的 npm ci/lint/typecheck/test/build/行为检查全部 exit 0。Vitest 2/2，行为测试 4/4、0 fail、0 skip，覆盖全部六组矩阵。
- Windows 原生 `npm run test:refimg` 明确非零退出并说明 Linux 要求，属于预期的平台拒绝，不算行为通过。
- 独立入口用单独临时夹具验证 lint exit 7 不丢失；真实 SIGTERM 取消返回非零，并移除具体容器、停止客户端，保留日志和副本。夹具不是业务验收。
- 共享 common/main/launch/resource/smoke/config/Dockerfile 与旧准备提交逐字一致。只改变结果展示与缺提交的退出状态；没有改共享取消/清理，未无理由重跑完整八场景。新增独立入口自身的成功、失败、信号清理路径已验证。

证据仍在 `/home/<user>/projects/yuweiju-afk-evidence`：`retro-final-audit.json`、`retro-{review,committed-review,exit7,cancel}-console.log`、`retro-cancel-verdict.json`、`retro-result-summary-check.log`、`retro-typecheck.log`。最终提交模式检查日志为 `admin-review-1791348560331859533-logs/`，逐命令退出码见 `exit-status.tsv`，完整结果/资源见 `resources.json`；各夹具的记录目录见最终审计。

12:50:38 审计确认本轮四个验证容器均移除、相应宿主/客户端 PID 均停止，WSL 准备工作区干净，原工作区既有删除与未跟踪资料保留，暂存区为空。build chunk 警告及当前依赖审计结果仍保留；不新增 hook、gate、缓存、hash、baseline、认证变更、GitHub 写入、原库操作或发布。报告等既有未跟踪资料未夹带入代码提交。

交付状态：五项复盘改进及其无模型验证通过；此前 #4 的人工接受仍为历史任务接受，本轮没有新的 AFK 任务验收或自动扩大授权。
