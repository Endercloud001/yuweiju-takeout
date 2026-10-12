# Ponytail AFK 接入实施报告

日期：2026-10-12（Asia/Shanghai）。依据：[已确认方案](../yuweiju-document/ponytail-integration-plan.md)。本轮授权是实装项目级技能与默认收尾、验证并按 implement 技能形成本地提交；没有业务票模型试点或 GitHub 发布授权。

## 结果与运行位置

新任务默认顺序为：实现与首次内部检查 → Ponytail full 复查 → 有依据的范围内精简 → 最终检查 → 成功候选提交 → COMPLETE → 宿主独立验证、人工验收/交付与全 Loop 复盘。无精简项可完成；未完成复查或检查时保留候选、剩余修改和证据，状态仍不完整。

可运行 checkout：Windows `E:/Learning Files/yuweiju-takeout/.scratch/ponytail-afk/runtime`；WSL `/mnt/e/Learning Files/yuweiju-takeout/.scratch/ponytail-afk/runtime`。分支 `codex/ponytail-afk-integration`，从 main `1e42c5364681436ea812fd8568aeb7ab00cd3304` 独立 clone；只读 `git ls-remote origin refs/heads/main` 核实远端当时同版本。实际本地提交可在该 clone 用 `git log` 读取。

原工作区位于旧 issue4 分支，有既有修改；旧 protected runtime 和历史任务保留。依赖只读复用已有 Linux node_modules，使用已核实 Node `v22.23.3` 的绝对入口；npm cache、TMPDIR、夹具、容器证据与日志均在项目 `.scratch/ponytail-afk/` 或该 runtime 内。

## 实施差异

- 新增 [yuweiju-ponytail 技能](../.agents/skills/yuweiju-ponytail/SKILL.md)，上游 `5.1.0` 正文原样保留于 reference，附 MIT 许可。来源为 Git 提交 `9b58c1ffb790c075ca32e70a89cf1d80588f4abf`；没有执行上游插件脚本或修改用户配置。
- [prepare-input.py](../.sandcastle/prepare-input.py) 默认复制三个材料；缺少任一材料时在读取 GitHub 前退出并报告路径。原额外材料、路径限制和完整读取证据继续保留。
- [common.mts](../.sandcastle/common.mts) 在现有编码提示中追加收尾入口和 startCommit，适用于所有新编码迭代，smoke 夹具保持原提示。只改变提示文本，不改模型、预算、deadline、closing、取消或安全包装器。
- [AFK 技能](../.agents/skills/yuweiju-afk/SKILL.md) 与 [runtime](../.agents/skills/yuweiju-afk/references/runtime.md) 默认准备并读取该技能，宿主核对复查与最终检查证据；缺证据不能从 COMPLETE 推定成功。
- 新增材料准备 CLI 普通测试及 `npm run check:input`，纳入现有 checks.example 的 runtime 组；同步 [流程图](../yuweiju-document/sandcastle-afk-loop.md) 与 [初研状态](ponytail-research.md)。

方案原本优先只调整准备指令。实际发现手写 task prompt 可能遗漏收尾，因此在已有提示注入点加一条入口指令；没有新阶段引擎或配置字段。材料文件检查是准备行为，复查证据仍由宿主审阅，不新增 hash、冻结 contract、baseline、通用 gate 或提交 hook。

Java 后端、Vue 管理端和微信小程序的业务代码、API、schema 均未改，三端无需源码调整。验收、数据写入与交付权限仍按具体任务处理。

## 验证记录

以下命令工作目录均为该 runtime，宿主经 `wsl.exe -d Ubuntu --exec` 调用；使用 Node 绝对目录加入该子进程 PATH。完整结果保存项目 `.scratch/ponytail-afk/evidence/`，不把本机目录当 fresh-clone 必需文档。

| 检查 | 实际结果 |
| --- | --- |
| `python -X utf8 .../skill-creator/scripts/quick_validate.py .agents/skills/yuweiju-ponytail` | exit 0；frontmatter 有效。Windows 默认 GBK 首次读取失败，改用 Python UTF-8 模式后通过；不修改校验器。 |
| `npm run check:types` | exit 0；runtime 类型检查通过。 |
| `npm run check:input` | exit 0；4 个 CLI 行为测试：默认三文件、缺正文退出、额外材料与 raw issue 证据、拒绝项目外材料。前两项分别先复现缺默认材料及静默缺文件，再修改实现后通过。 |
| `npm run check:git-policy` | exit 0；6 个普通测试。 |
| `npm run check:evidence` | exit 0；3 个脱敏/证据边界测试。 |
| `npm run check:git-guard` | exit 0；57 个案例，containerStopped=true。 |
| `npm run check:lifecycle` | exit 0；9 个无模型生命周期案例，包含取消、停止及成果保留。 |
| `npm run check:config` | 修正调用环境后 exit 0；真实两轮无模型 worktree、缓存/证据承接、候选独立检查与环境挂载通过。 |
| `npm run check:preflight` | exit 0；5 个无模型案例，包含真实镜像网络及取消。 |
| 实际材料只读挂载 | exit 0；离线准备入口复制本次实际技能，Git-safe 容器 UID1000 可读三文件，写入得到 EROFS，network=none、authMounted/modelCalled=false、stopped=true。 |

第一批日志：`evidence/1791807718996659275`；其中 config 的两轮和候选检查先通过，后续环境快照因未设置统一 evidence 根而 exit 2。第二批仅重跑受影响 config 与尚未运行的 preflight：`evidence/1791808083542628947`，均 exit 0；原边界未绕过。材料探针首次未覆盖镜像默认 sleep entrypoint，exit 1 且容器停止；改用 `--entrypoint python3` 后证据在 `evidence/material-mount-1791808162972236339`，exit 0、stopped=true。

静态技能场景由独立代理读取技能演练：接入前旧技能允许检查后直接提交，不要求精简；接入后覆盖重复 helper、无改动、精简后失败、剩余15秒、上轮提交后再修改、纯文档、缺材料等8个场景，均给出符合方案的处理。它是阅读指令的场景演练，不是容器 Codex 实际执行或质量收益验证。

候选源码的精确提交独立快照检查、文档可移植性检查与两轴审查将在提交候选后记录于下节；上述宿主/夹具验证不能冒称为这项检查。

## 候选独立验证与审查

待形成候选提交后运行现有 verifier，分别记录精确提交、命令、退出、容器停止和结果；code-review 的 Standards 与 Spec 两轴结果单列。不会用历史夹具的候选替代本次维护提交。

## 真实试点准备与限制

本轮只读查询当前 open issue：#1、#15–18、#20–21。#16 为候选：正文涉及 AI 输出/外部 HTTP/三端，native blocker #2 已关闭，评论为0；原始正文保留在项目 `.scratch/ponytail-afk/readiness/issue16.json`。它不是可以直接当“小型精简例子”的低风险任务，尚未选择或启动。

下一票使用该 clone 的真实 HEAD 为 preparation/startCommit，沿用现有 task-config 字段和 startup 交接；按该票授权填写分支、摘要、输入、认证、网络、验收命令、轮次与时间，执行现有 preflight。材料准备会默认带入 Ponytail，无需另开开关。具体 issue 与预算、真实验收能力尚待该票授权/核实；本轮不编造 ready 配置或补开业务任务。

尚未运行真实编码模型，不证明 Codex 一定遵守收尾，也不证明时间/成本/代码质量净收益。完整插件生命周期与自动 slash 解析不属于本次实现。宿主应核对进度和差异；独立 verifier 不读取未提交修改，人工验收与发布仍另有状态。

## 回退

在该维护 clone 通过普通新提交撤回本次技能、默认复制和提示入口，形成新的准备版本再供后续任务使用；保留失败输入、worktree、提交和证据，不改写旧运行。单票精简只撤回其自己的修改，不覆盖用户改动或使用 hard reset/clean。真实试点失败或取消后不自动续跑。
