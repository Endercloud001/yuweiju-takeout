# 文档迁移与 AGENTS 更新评审 v1

日期：2026-10-06（Asia/Shanghai）。使用 code-review 技能分别开展 Standards 与 Spec 评审，主评审补充路径、引用、差异及依赖检查。本次只新增本报告，不修订被评审文件，不写入 GitHub，不运行业务测试或新增 gate。

评审结论：未发现必须修复项；Spec 轴有 1 项建议改进。文档迁移、入口精简及安全边界在可核验范围内符合已确认审计。此结论不代表后续业务实现或维护者查询/事务练习已完成。

## 差异基点与归因边界

仓库唯一提交为 `2106c3d2702b6c438b9bd7ea0d33a50613a7d5f1`（导入提交）。维护者在本对话确认迁移尚未提交、没有迁移前快照或其他比较基点。因此不能把 `git diff HEAD --` 全部差异或全部未跟踪文件当成本轮新增。

- 可复核来源：`git rev-parse HEAD`、`git log -4 --oneline`、`git diff HEAD --`、`git status --short`，以及 `git show HEAD:<旧路径>`。`git log HEAD..HEAD --oneline` 的提交范围为空；技能通常使用的 `git diff HEAD...HEAD` 不能覆盖本次未提交迁移，故采用工作区差异并补读相关未跟踪文档。
- 本轮范围依据：[已确认审计](agent-environment-audit.md) 与 执行报告（历史引用：`docs/agent-environment-refresh-report.md`，当前文件缺失），旧规则原文用于检查有效条款是否承接，当前代码用于核对规范中的事实描述。
- 明确排除已有用户变更：`PLANS.md` 删除、`yuweiju-backend/答辩常见问题.md` 删除，`.scratch/`、`GLOSSARY.md` 及已有 `docs/` 的原始内容。没有要求恢复这些文件或把它们作为迁移缺陷。
- 根 `AGENTS.md` 已有用户修改，是混合变更；只核对授权迁移后的入口与边界，不能逐行断言所有相对 HEAD 的改动均来自本轮。已有未跟踪文档的维护更新也只能按审计/报告所述内容归因，无法还原其迁移前全文。
- ignored 工具配置、日志和原始附件没有可用 Git 前像。能复查当前存在性、静态依赖和报告记录，不能独立重建删除前 143 文件清单、进程占用检查或删除先后顺序。

本轮纳入的具体内容如下：

| 类别 | 范围 |
| --- | --- |
| 协作入口 | 根目录及后端、管理端、小程序四份 `AGENTS.md` |
| 集中流程与规范 | `docs/agents/workflow.md`、`local-development.md`，`docs/standards/backend.md`、`admin.md`、`miniapp.md` |
| 项目技能 | `.agents/skills/yuweiju-java-backend/SKILL.md`、`yuweiju-stack/SKILL.md`、`yuweiju-ui/SKILL.md` |
| tracked 删除 | 后端及管理端 `.trae/` 的两份规则、四份技能；`yuweiju-document/execplans/Code Sytle Guide.md` |
| tracked 历史文档维护 | 五份 ExecPlan：AI 可运营系统、后端业务边界、敏感凭据轮换、三端接口与环境配置、生产可靠性；计划执行顺序与依赖矩阵；自检报告 |
| 已有未跟踪文档的迁移维护 | `docs/agents/domain.md`、`issue-tracker.md`，两份后端/架构研究、判断/恢复访谈、恢复规格、最小验证报告、ADR 0001，以及审计与执行报告中的迁移说明和引用 |
| 本机清理 | `.codex/config.toml` 元数据迁出；报告列出的 `.claude/`、日志/工具目录、旧草案与验证输出清理。只复查可见现状，不把 ignored 删除数作为独立复核结果 |

tracked 差异共 20 个路径，排除两项已有删除后有 18 个候选迁移路径，其中根入口仍是混合变更。其余已有文档、ADR 0002、分流标签文档、保留验证源码及业务代码只作为引用或事实证据检查。

## Standards

### 必须修复

无。未发现新入口或规范违反现行工作流、安全边界及已接受分层决定的证据。

### 建议改进

无。纯文档迁移不适用的代码 smell 未强行套用；删除重复规范、保持单一详细规范来源也没有产生需要新增抽象或 gate 的问题。

核对结果：

- 后端规范区分“当前结构事实”和“后续实施目标”。单 Maven、`com.codeying`、Java 21、Boot 3.5.0、MP 3.5.7 与 POM/源码一致；`ApiResult` 的 `code/success/message/msg/data`、`PageData` 的 `total/records`、Jakarta 及 MP 分页均与当前实现相符。具名 Mapper、Service 事务和查询不写历史图片是已确认目标，未将尚未整改的代码描述为已符合。
- 管理端规范与 `package.json`、TypeScript 配置及 `src/api/http.ts` 相符：实际工程目录、VITE_API_BASE、Cookie token、`token` 请求头、成功 code 1、返回响应体与失败抛 Error 均准确，没有恢复旧模板的 `/api` fallback、API 层弹错或 401 跳转设想。
- 小程序规范明确编译产物/手写页面及缺失可重建源工程的现状，保留 `authentication` 和模拟支付语义，没有假定 npm build 可用，也没有把 Node 语法检查当开发者工具编译验收。
- 四入口分别为 25、7、7、6 行，按任务触发详细流程、端规范、领域与 issue 文档；项目技能只保留操作步骤并引用集中规范。三个名称已实际出现在本会话可用技能目录，发现验证已获得本会话证据，无需继续仅标为“等待新会话”。
- 根入口、workflow 和 backend 保留接口/原数据兼容、认证/归属、隔离失败测试、原库演示授权、SQL 与外部副作用区别、异常 cause 和日志脱敏。`.gitignore` 无差异，敏感凭据专项计划正文未删；workflow 明确其仍为安全来源，文档存在不证明轮换完成，正式发布仍需核验实际结果。

## Spec

### 必须修复

无。对旧 AGENTS、后端 rules/skill、管理端 rules/skills 和 Code Style 的有效条款作对照后，未发现本轮造成的关键内容缺失或超范围业务改动。

### 建议改进

**S1：更新恢复规格中 issue #2 的同步状态。**

处理状态（2026-10-06）：已按维护者授权修订恢复规格第 140 行，明确 issue #2 已同步并回读确认，补充交接报告链接，保留查询/事务评审与练习及后续业务任务待完成的边界。以下保留评审时发现与建议。

- 位置：[恢复规格](yuweiju-restore-spec.md)第 140 行，仍写“GitHub issue #2 的旧前置文字待授权同步”。
- 依据：审计要求“更新四个原位 AGENTS、现行 docs/agents、restore-spec 和受影响研究的入口”；执行报告（历史引用：`docs/agent-environment-refresh-report.md`，当前文件缺失）第 52 行已经记录 2026-10-06 追加授权、更新 issue #2 并回读确认。本次只读回查 [issue #2](https://github.com/Endercloud001/yuweiju-takeout/issues/2)，标题为“首期前置：统一协作入口与三端技术规范”，正文已经撤掉旧前置，状态 OPEN、无评论。
- 影响：当前规格与交接状态不一致，后续执行者可能重复请求同步授权或误判远端仍未更新；其余正文已说明 PLANS 前置解决，因此不构成本轮阻塞。
- 建议：将末句改为已授权同步并链接交接报告，保留查询/事务评审与业务任务尚未完成的边界。无需再次写入 GitHub。

其余需求核对结果：

- 有效兼容、对象校验、Mapper/事务边界、线程上下文清理、资源生命周期、异常与脱敏要求由 workflow/backend 承接；前端请求、路由、素材与视觉要求按源码纠正后承接。旧三模块、javax、响应重命名、COBOL、矛盾动效与远程设计审查要求按审计退出，没有原样复制成第二套权威。
- 历史计划、访谈、研究及验证报告保留背景身份，并指向现行流程/规范；原输出改为已清理说明。旧 Code Style 中的未来支付、MCP、outbox、ticket 等设想不能当作现有安全实现，也不要求因文档迁移新增业务能力。
- 素材目录和复制脚本仍保留。验证目录现有 12 个源码脚本和 `model-fixture-mode.txt`，fixture 输入未误删；含下单、客服、训练、JWT 调试或 Redis/文件副作用的脚本没有运行，复用边界仍保留。
- 未见已退役目录的现行运行/配置依赖；历史来源名称和 `.gitignore` 隔离条目保留不算失效依赖。未新增 hash、冻结 contract、baseline、业务测试或 gate。

## 验证记录与限制

命令工作目录均为仓库根目录；以下为本次实查结果，不沿用交接报告的成功结论代替执行。

| 检查 | 结果 |
| --- | --- |
| Git 基点、状态及限定差异 | 基点有效、工作区差异非空；已区分已有删除、混合变更及未跟踪材料 |
| 本地 Markdown 链接 | Python 一次性解析四入口、docs 文档、三个技能与七份 tracked 历史文档：32 份文件、131 个本地链接、0 个失效；exit 0。没有本地锚点链接需额外检查 |
| 技能格式 | `python -X utf8 C:/Users/Endercloud/.codex/skills/.system/skill-creator/scripts/quick_validate.py <技能目录>`，三个分别输出 `Skill is valid!`；本会话目录已列出三技能，未见 yuweiju 禁用配置 |
| 废弃依赖 | `rg` 检索项目源码/构建/配置及保留脚本，排除第三方依赖、构建产物和已有发布草稿；没有发现废弃工具路径消费者。专门复查保留验证脚本、应用 scripts 与项目 config，匹配数为 0 |
| 路径现状 | 报告列出的旧工具/日志目录不存在；两素材目录、api_smoke 脚本、fixture 输入存在 |
| 业务与安全差异 | tracked 后端源码/POM/scripts、管理端实际工程、小程序业务文件无差异；`.gitignore` 无差异；凭据安全计划仅新增历史说明 |
| 差异空白检查 | `git diff --check` exit 0；仅 Git 已有 LF/CRLF 转换提示 |
| 远端状态 | gh 只读获取 issue #2 的 title/body/state/comments，exit 0；没有写入 |

辅助配置读取首次尝试 `tomllib` 因本机 Python 缺少该模块失败；改用限定文本检查完成 yuweiju 禁用条目核对，没有安装依赖或修改配置。此失败不影响三个技能格式及实际发现结果。

本轮未构建应用、启动服务、连接数据库、生成 token、运行保留业务脚本或验证凭据轮换。静态依赖搜索不能证明不存在仓库外个人脚本或未来手动调用。没有迁移前快照的材料仅能作范围限定的现状评审，不能宣称完整恢复每一项历史前像。

Standards：必须修复 0、建议改进 0；Spec：必须修复 0、建议改进 1，最高为 S1 的同步状态滞后。
