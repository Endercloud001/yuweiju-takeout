# 代理环境与协作文档审计

> 执行状态（2026-10-06）：本报告保留已确认审计与原路径清单。后续迁移已执行，当前结果与 GitHub 待同步文字见 交接报告（历史引用：`docs/agent-environment-refresh-report.md`，当前文件缺失）；下文“未实施”描述审计阶段，不代表当前状态。执行时另核实：旧 To-do 的 execplans/archived 两路径均不存在，不恢复文件、不再作为前置；此前归档定位保留为审计时记录。


调查日期及更新日期：2026-10-06（Asia/Shanghai）。本阶段仅调查和更新本报告；没有迁移、删除、恢复 PLANS.md、修改现行规则或运行会写入数据库的验证脚本。目标路径及清理清单记录维护者已确认的方向，具体实施尚未进行。

## 结论

保留根目录及三端 AGENTS.md 作为就近发现入口；协作流程放 `docs/agents/`，按当前推进的 issue 重写三端规范到 `docs/standards/`，确有当前用途的项目技能放 `.agents/skills/`。Codex 已支持该技能位置。维护者明确不日常使用 Trae/Claude，因此后续删除 `.trae/`、`.claude/` 及其过时内容，不保留专属适配入口。[S1][S12]

现行规则有 PLANS 退出后仍被引用、To-do/素材路径失效、COBOL/模块/技术模板不符等问题。维护者已决定删除互相矛盾的旧规范，按当前代码、accepted ADR 和相关 issue 重写统一规范；安全、认证、事务、数据兼容及三端影响要求继续保留，不因移除废弃工具规则而移除业务安全实现。[S1][S3][S4][S6][S7]

维护者已明确不需要过时训练、启动、负载测试的原始输出，空与非空历史日志均列入删除。运行目录是否保留只由仍存在的运行依赖决定；旧启动脚本同时退役后，不再为它保留 `.codex-logs/`。TokenPrinter 未发现频繁使用证据，列为删除候选，若后来取得频繁使用证据再单独判断。[S13]

PLANS.md 已退出开发流程是本次明确约束，不是待确认项。后续应移除要求恢复、遵循或先补齐它的现行条款；历史记录可保留“当时引用 PLANS”的事实，不能继续作为执行条件。无需用另一份冻结 contract、baseline、hash 或 gate 替代它。

## 范围、方法与证据边界

- 覆盖根目录、后端、管理端、小程序中 `.claude`、`.trae`、独立 `rules`、`skills`、`logs`，以及相邻 `.codex`、`.codex-logs`；以包含隐藏和 ignored 文件的扫描补充普通文件清单。
- 当前范围包含根目录及三端四份 AGENTS.md；通过隐藏/ignored 扫描补充配置与工具，依赖缓存、构建输出和第三方安装包不作为本项目制定的规则。
- 读取根及三端 AGENTS、两份规则、四份技能、三个 `docs/agents` 文件、GLOSSARY 和两份 ADR；核对仍存在的 API、数据库、计划及其引用。外部技术链接只作为原文来源，不扩展为重新论证全部业务方案。
- 复用现有架构研究、后端规范研究、判断与恢复访谈、恢复规格、最小验证报告和 8 月自检；对涉及本次处置的关键事实重新查源码、脚本、配置和路径存在性。[S6][S7][S8]
- `CreationTime < 2026-06-18` 只用于早期候选筛选。Windows 文件时间可能受复制影响；不能将它当作作者首次创建日期。Git 当前只有导入提交 `2106c3d`（2026-09-09T20:27:15+08:00）；这些已跟踪规则首次出现在该提交，无法从当前 Git 还原 3–6 月的逐项创作过程。ignored、未跟踪材料没有可用的仓库历史。
- 已有工作区状态为根 AGENTS 修改、PLANS 删除、后端答辩文档删除及未跟踪 `.scratch/`、GLOSSARY、docs。本轮保留这些状态，不以工作区差异推断本轮已改规则。
- 静态检索与当前进程观察能够识别已见依赖，不能证明未来 IDE、用户脚本或手动重定向不会使用某目录。本次不运行应用、不连接数据库、不改变 IDE 配置、不执行凭据生成脚本，不声称重新完成三端业务验收。

## 目录与发现机制

| 范围 | 实际内容 | 发现及依赖判断 |
| --- | --- | --- |
| 根目录 | 无 `.claude`、`.trae`、独立 `rules/`、`skills/`；`logs/` 六文件；`.codex/config.toml`；`.codex-logs/` 十一个文件（含子目录） | 根 AGENTS 是共享入口；`.codex`、`.codex-logs`、logs 均 ignored。不存在的目录不需要创建或“清理”。 |
| 后端 | `.claude/settings.local.json`；`.trae/rules/rules.md`；`.trae/skills/yuweiju-java-backend/SKILL.md` | 根及三端入口显式引用后端规则，属于文档读取依赖；Claude 文件为本机权限配置。未发现业务构建依赖 `.trae/skills`。 |
| 管理端 | `.trae/rules/project_rules.md`；三个 `.trae/skills/*/SKILL.md` | 规则由根/管理端入口显式引用；`yuweiju-ui` 又依赖该规则。真实应用位于 `yuweiju-admin/`，不能以子项目根 `src/` 解释实际路径。 |
| 小程序 | 无上述配置/规则/技能/日志目录；有 AGENTS | 保留自身入口，不能为统一目录而复制后端或管理端技能。`common/`、`pages/` 等实际为编译产物与手写页面混合。 |

Codex 官方文档明确：从当前工作目录到仓库根目录的父目录链扫描 `.agents/skills`，每个技能以独立目录与 `SKILL.md` 存放；支持符号链接，同名技能不自动合并。当前仍有用途的项目技能可放根 `.agents/skills/`，包括经筛选后的后端或接口维护技能；UI 技能只有当前 issue 确实需要时才重写保留，不自动迁移旧视觉开发流程，并避免与用户级同名技能重复。官方还允许通过 skills 配置禁用技能，后续迁移须确认没有相关禁用条目并在新会话验证发现结果。[Codex 技能文档](https://learn.chatgpt.com/docs/build-skills)

当前会话提供的技能目录没有这四个项目 `.trae` 技能，因此本报告不假定其已生效。根 `docs/agents/issue-tracker.md` 等是协作文档，不是带 `SKILL.md` 元数据的可发现技能；无需为发现而复制成技能包。用户级 Matt/research 等已安装技能继续留在用户级，不纳入项目迁移。[S1][S2][S12]

`.codex/config.toml` 的 `[project]`、`[docs]`、`[workflow]`、`[tracking]` 记录 COBOL、旧路径、`plan_required`、`autoplaybook` 等。官方配置参考没有这些工作流键，仓库也未发现读取这些自定义表的程序。因此只能确认文本残留，不能称其实际强制执行 PLANS。根 AGENTS 的强制条款则是明确存在的文档依赖。后续将仍有用的信息写入协作说明，只保留确认受支持的 Codex 配置；不借审计改变安全或审批设置。[配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)[S9]

## 早期候选与当前冲突

| 文件组 | 本机创建时间 | Git / 内容 / 当前证据 | 判断 |
| --- | --- | --- | --- |
| 根及三端 AGENTS | 根 04-09；三端 04-08 | 全部由 09-09 导入；根另有 10-05 未提交的协作入口；存在旧 COBOL/PLANS 和有效安全条款 | 保留入口并修订，不按日期删除。 |
| 后端规则及技能 | 03-23、03-24 | 多模块包名与 javax 示例不符实际单工程、Boot 3.5.0/MP 3.5.7/Java 21 | 删除冲突规范，按实际代码和当前 issue 重写；技能按当前用途筛选。 |
| 管理端规则及三技能 | 04-02 | 技术栈主体匹配 package.json，但动效要求冲突、素材路径失效 | 删除冲突旧规范，按当前 issue 重写；web-design-guidelines 已明确删除。 |
| API HTML、数据库设计、原 db_init.sql | 03-21 | 旧 HTML 已包含 camelCase 字段；当前 `Orders.userId/orderTime/payStatus` 亦为 camelCase。`ApiResult` 还含 success/message，不止模板中的三个字段 | 是兼容证据，不能强制改为 snake_case，也不能直接以新模板删字段。 |
| 根 logs 与 `.codex-logs` | 04–06 月 | 历史输出与手工工具混合 | 过时输出删除；旧启动工具退役；TokenPrinter 按使用证据单独筛查。 |
| 8 月七份计划/规范与自检 | 08-08（不属早期候选） | 包模块化、Spring AI 1.1.x 已采用、冻结契约和全面门禁等要求，与 10 月常规分层 ADR、当前 POM、用户约束冲突 | 同样需降为历史方案；不能因更新日期较新而提升为现行规则。 |

具体需校正：

1. 后端规则第 1 节及后端 AGENTS 目录地图改为实际单工程 `src/main/java/com/codeying`；沿用已接受常规分层，不为适配模板创建三模块。[S3][S6]
2. 后端规则 §12 的 `Result`、PageHelper、`javax.validation` 示例改为已有 `ApiResult`、`PageData`、MP 与 `jakarta.validation`；保留兼容响应字段。后端技能同步修订，避免两份权威指向不同做法。[S3][S4][S7]
3. 后端 AGENTS 的“数据表字段/JSON 一律 snake_case”拆清数据库列与 API 字段；旧 API 本身已有 camelCase，不制造接口迁移。[S3][S7]
4. 管理端入口定位 `yuweiju-admin/src/`，检查命令从实际 package.json 所在目录执行；素材分别指向外部 `yuweiju-web-vue/reference_images/` 与应用内 `src/assets/reference_images/`。真实 `copy-ref-images.cjs` 使用本机绝对源路径，素材不是纯文档资产，不能随规则清理而删除。[S4][S7]
5. `yuweiju-stack` 的模板守卫、401、返回类型、store/Cookie 使用应核对真实 `http.ts` 和 router。实际 `http.ts:5,30–36` 直接读取 VITE_API_BASE、失败时抛 Error，未实现技能所述 API 层 ElMessage、code 401 跳转或代码内 `/api` fallback。UI 技能说“消息不在 api 层”，stack 模板又要求 API 层 ElMessage，必须形成一个错误提示责任约定，避免双提示；不据模板改认证实现。[S4][S7]
6. 根和三端 COBOL 强制段落及 config 描述：检索未找到已证实的 COBOL 流程/数据源；近期研究亦明确没有该业务依据。建议移除其当前必做要求，保留真正的三端影响与兼容性规则，无需维护者再次证明“没有 COBOL”。[S1][S3][S4][S6][S9]

## 处置清单：保留并修订

目标“原位”表示保留当前位置；迁移后仍需保持入口可发现。以下条目均未实际修改。

| 原路径 | 目标路径 | 证据与应修订内容 | 引用迁移点 |
| --- | --- | --- | --- |
| `AGENTS.md` | 原位；流程正文收敛至 `docs/agents/workflow.md` | 保留目录地图、优先级、安全、兼容、三端影响；移除 PLANS 前置条件与重复规范；保留已有 Matt 入口。[S1] | 根现有 §目录地图、工作模式、参考资源；三端入口回链 workflow。 |
| `yuweiju-backend/AGENTS.md` | 原位；规范指向 `docs/standards/backend.md` | 修正包名/单模块、JSON、失效 COBOL 要求，保留脱敏、事务和 Mapper 边界。[S3][S7] | 该文件参考/流程/PR 说明；根 AGENTS 后端规则入口。 |
| `yuweiju-web-vue/AGENTS.md` | 原位；指向 `docs/standards/admin.md` | 修正应用子目录、检查命令、动效冲突、rtk 来源、PLANS/COBOL。[S4][S7] | 根及该文件规则引用；与 UI/stack 技能交叉引用。 |
| `yuweiju-weixin-miniapp/AGENTS.md` | 原位；按当前 issue 重写规范到 `docs/standards/miniapp.md` | 保留 wx API、归属/脱敏、编译与接口一致；区分编译产物、可编辑页和缺失源工程；清除 PLANS/COBOL。[S5][S6] | 该文件参考、流程和后台规范链接；根目录标准索引。 |
| `docs/agents/issue-tracker.md` | 原位 | 已有 GitHub 项目、授权边界和 body-file 约定可用；计划链接可继续引用历史/任务材料，但不能推导恢复 PLANS；wayfinder 操作只在使用该流程时适用。[S2] | 根 AGENTS 已正确链接，无需移位。 |
| `docs/agents/triage-labels.md` | 原位 | 五个分流标签映射与按需创建策略，未发现需要复制到其他目录的理由。[S2] | 根 AGENTS 保持链接。 |
| `docs/agents/domain.md`、`GLOSSARY.md` | 原位 | single-context 与演示数据/模拟支付/历史图片术语有效；将“既有计划要求”明确为读取背景，不暗含 PLANS 前置条件。[S2][S6] | 现有根入口、ADR、Issue/规格术语链接。 |
| `docs/adr/0001-retain-conventional-backend-layering.md`、`0002-preserve-historical-order-images.md` | 原位 | accepted 决定，未被本任务推翻；仍是规范合并时的依据，不折叠进历史方案。[S6] | 保留其访谈/研究链接；新 backend 标准引用 ADR 0001。 |
| `docs/yuweiju-backend-standards-research.md`、`docs/yuweiju-architecture-research.md` | 原位 | 前者仍将简单查询边界写作候选，恢复规格已选择具名 Mapper；后者记录 accepted ADR。保留研究证据，补充“现行选择见规格/ADR”即可，不改写历史比较。[S6][S7] | 前者旧 `.trae/rules` 链接改为 backend 标准；研究/ADR/规格互链。 |
| `docs/yuweiju-judgement-grilling.md`、`docs/yuweiju-restore-grilling.md` | 原位 | 维护者取舍与范围依据，不能删除或冒充代码验收；近期恢复访谈仍列 PLANS 缺口，后续追加已退出流程的说明。[S6] | restore-grilling 第 162、200、206 行相关措辞；判断记录的历史 Git 删除事实保留。 |
| `docs/yuweiju-restore-spec.md` | 原位 | 当前首期需求与验收；§前置条件第 140 行仍把 PLANS 缺口放入实施前置，应按本次确认撤掉；其余待验证业务事实保留。[S6] | 前置条件及旧规则描述改链新 standards/workflow；不重开已接受范围。 |
| `docs/yuweiju-minimal-verification.md`、`docs/verification-evidence/2026-10-05/` | 报告原位修订；过时测试输出删除 | 报告保留必要结论与局限，过时结果/截图/原始输出无需为留证保留；脚本按是否仍服务当前 issue 区分。[S6] | 输出清理后将来源标为已清理的历史证据，删除悬空附件链接；可复用测试脚本不按输出文件一并误删。 |
| `yuweiju-document/api/余味居-管理端接口.html`、`余味居-用户端接口.html` | 原位 | API 历史兼容资料，与源码按接口逐条更新；不能作为冻结 contract，不能因为早期日期删除。[S7] | 根/三端 AGENTS 与旧计划引用保留；标准只声明阅读触发条件。 |
| `yuweiju-document/db/数据库设计文档.md`、`db_init.example.sql`、本机 `db_init.sql` | 原位 | schema 与数据来源；原 SQL ignored，示例受 Git 管理。不能用规则清理授权执行 SQL、清库或改 schema。[S7][S10] | AGENTS、API/计划现有数据库引用保留；本机 SQL 不搬入 docs。 |

## 处置清单：合并去重

此处合并用于消除重复入口。互相矛盾的旧规范须删除并重写，不能全文拼接或继续维护两套权威；新规范以实际代码、accepted ADR 和当前推进的 issue 为依据。技能不默认全量迁移，仅保留当前任务确有用途的流程；下表技能目标均以用途核对通过为条件，否则随旧目录删除。

| 原路径 | 目标路径 | 合并内容与证据 | 引用迁移点 |
| --- | --- | --- | --- |
| 根及三端 AGENTS 的流程、兼容、TODO、三端影响重复段 | `docs/agents/workflow.md`；各 AGENTS 留短入口 | 明确任务范围、变更影响、普通针对性验证和已有安全边界；不恢复 PLANS 固定格式，不引入新 gate。[S1–S5] | 四个 AGENTS 的工作/交付流程与 PR 说明；docs/agents/domain、issue-tracker 的相关表述。 |
| `yuweiju-backend/.trae/rules/rules.md`、`yuweiju-document/execplans/Code Sytle Guide.md` 及后端技能重复规范 | 重写 `docs/standards/backend.md` | 以当前 issue、代码和 accepted 分层决定重写；保留必要安全/兼容要求，删除旧技术模板、强制模块化和无依据门禁，不保留冲突原件为第二套规范。[S3][S6–S8] | 根/三端 AGENTS、技能、计划/矩阵、自检、研究和规格的规范链接统一迁移。 |
| `yuweiju-web-vue/.trae/rules/project_rules.md`、管理端 AGENTS 摘录及 stack/UI 重复条款 | 重写 `docs/standards/admin.md` | 按当前 issue 和现有可运行管理端重写技术规范；删除冲突动效、消息层及过时强制视觉要求，不借规范重写重做现有布局。[S4][S7] | 根/管理端 AGENTS、仍有用途的 skills、计划/研究/验证报告旧链接迁移。 |
| `yuweiju-backend/.trae/skills/yuweiju-java-backend/SKILL.md` | `.agents/skills/yuweiju-java-backend/SKILL.md` | 保留接口兼容、职责调查与回归操作，引用 backend 标准而非复制；将 javax 改为 Jakarta；`scripts/api_smoke.ps1` 实际存在，但构建/烟测命令需明确 cwd 和演示写入影响，不能用 skipTests 构建宣称测试通过。[S3][S7][S12] | 后端规则/入口的技能说明；规范链接从新 skill 目录正确回到 docs。 |
| `yuweiju-web-vue/.trae/skills/yuweiju-stack/SKILL.md` | `.agents/skills/yuweiju-stack/SKILL.md` | 保留请求、响应、路由、store 的操作步骤，契约描述来自源码/standard；避免对不存在或不同实现的模板宣称不可变。[S4][S7][S12] | 管理端入口、admin 标准和 UI skill 交叉链接。 |
| `yuweiju-web-vue/.trae/skills/yuweiju-ui/SKILL.md` | `.agents/skills/yuweiju-ui/SKILL.md` | 保留项目视觉实现步骤，规范正文集中 admin 标准；修复 `reference_documents/reference_images/`，移除与标准重复的 palette/SFC 大段。[S4][S7][S12] | SKILL 中 project_rules、素材路径；管理端入口。 |
| `docs/yuweiju-matt-setup-draft.md` | 已有 `docs/agents/{issue-tracker,triage-labels,domain}.md` + 根 AGENTS | 草案的入口与主体已落地，不再维护第二套正文；没有必要复制用户技能到项目。[S2] | 未发现当前入口引用草案；保留落地文件即可，草案见删除建议。 |
| `.codex/config.toml` 中有用的项目说明/文档定位 | `docs/agents/workflow.md` + 根 AGENTS；受支持工具项才留 config | 自定义表未见消费者，COBOL/PLANS 不保留为执行要求；不将 config 文本误称强制机制。[S9][S12] | 根 AGENTS:8；config 的 backend_rules/frontend_rules/todo_list 旧路径；不要新增工具不识别的替代键。 |

维护者已选择以 Codex 为主，后续不保留 Trae/Claude 规则适配文件。四个 AGENTS 继续原位，统一链接新规范；旧规则和技能的有效内容核对后按需重写，不原样复制旧目录。

## 处置清单：删除

以下清单结合已核实的失效/重复情况与维护者明确的清理决定。实施时检查活跃写入、现行引用及业务安全边界；历史输出已确认无需提取或留证。

| 原路径 / 条款 | 目标路径 | 删除理由与证据 | 引用处理 / 条件 |
| --- | --- | --- | --- |
| PLANS.md 的现行强制要求（文件已是工作区删除状态） | 无；不得恢复 | 用户明确退出开发流程。不是需要补齐的缺口。[S1][S6][S9] | 移除根 AGENTS:8,11、管理端:37、小程序:29、config:7；矩阵:92的旧依赖改作历史说明；restore-spec:140、restore-grilling:162,200,206 不再阻塞。 |
| 根/三端/config 中无实际来源的 COBOL 必做条款 | 无；有效兼容要求并入 workflow | 未发现 COBOL 资产或已证实流程，近期研究亦有此结论。[S1][S3–S6][S9] | 各三端 COBOL 专节、流程步骤、PR 凭据要求及 config project description/focus。 |
| `yuweiju-backend/.trae/`、`yuweiju-web-vue/.trae/` | 新规范与确有用途的 `.agents/skills/` | 维护者明确尽可能删除废弃工具目录；冲突规则重写，不留适配入口。[S3][S4] | 先写新规范、筛选必要技能并迁移引用，再删除旧目录；web-design-guidelines 不迁移。 |
| `yuweiju-backend/.claude/` | 无 | 不再日常使用 Claude，局部工具配置退役。 | 更新相关工具说明，不迁移该工具专属许可。 |
| `yuweiju-web-vue/.trae/skills/web-design-guidelines/` | 无 | 维护者明确已过时且不会再使用。 | 不迁入 `.agents/skills/`，不保留远程布局审查前置步骤。 |
| `.codex-logs/run-backend.cmd` | 必要参数写入启动说明，旧文件删除 | 不依赖个人手工工具；删除后解除旧日志重定向依赖。 | 更新旧启动说明中的脚本链接。 |
| `docs/verification-evidence/2026-10-05/` 中过时测试输出 | 无 | 不再保留原始输出用于历史留证。 | 更新验证报告附件链接；脚本按现行用途筛选，不与业务数据混同。 |
| `docs/yuweiju-matt-setup-draft.md` | 无（内容已在落地文件） | 已落地初始化草稿；未发现当前规则依赖它。[S2] | 保留 docs/agents 三文件与根入口；如后续需要初始化变更记录，可仅保留摘要而非全量重复。 |
| `logs/*.log`（包括两个非空训练日志） | 无 | 维护者确认所有过时训练/启动输出不需留证。[S13] | 清理时检查没有活跃写入；不要求摘录、迁移或备份历史输出。 |
| `.codex-logs/*.log`、`jmeter.log` | 无 | 维护者确认过时启动/负载测试输出删除，不区分是否非空。[S13] | 同步退役旧重定向脚本；更新报告中附件引用，不清理模型工件或业务数据。 |
| `.codex-logs/TokenPrinter.java`、`token-classes/TokenPrinter.class` | 无 | 未发现调用引用或频繁使用证据；列为删除候选。[S13] | 若后续发现近期反复调用或现行任务依赖，再单独判断；不执行工具、不生成 token。 |

后端 `.claude/`、废弃 `web-design-guidelines`、旧 `run-backend.cmd` 和所有过时测试输出均已明确列入清理。目录移除不改变业务认证、安全实现、运行数据或全局工具权限。

## 历史方案、引用闭包与运行输出

### 计划与说明材料

以下材料是 AGENTS 的目录型引用或旧 To-do/计划的进一步引用，已核对它们的定位、相关内容和当前矛盾。方案和自检可原位作历史背景，不继续赋予全局约束；其中冲突的 Code Sytle 规范须删除并重写。本轮不迁移整个 `yuweiju-document/`。

| 原路径（均相对仓库根） | 目标路径 | 当前用途与引用处理 |
| --- | --- | --- |
| `yuweiju-document/execplans/ExecPlan - 立即清理并轮换敏感凭据.md` | 原位，历史安全方案 | 保留安全来源，不把文档存在等同轮换已完成；与其他四份计划相互链接。正式发布/真实凭据仍遵守项目安全要求。 |
| `yuweiju-document/execplans/ExecPlan - 统一三端接口与环境配置.md` | 原位，历史方案 | 冻结 contract、OpenAPI/CI 门禁是拟议步骤；没有证据授权自动实施。旧规范引用迁至 standards；API 兼容要求保留。 |
| `yuweiju-document/execplans/ExecPlan - 补齐生产级可靠性与质量门禁.md` | 原位，历史方案 | 双节点/Redis/CI/outbox 等不成为本机维护前置。区分已有安全实现与未实施建议，保留故障场景来源。 |
| `yuweiju-document/execplans/ExecPlan - 把 AI 从“同步调用模型”升级为可运营系统.md` | 原位，历史方案 | Spring AI/流式/运营化不是当前代码事实；不凭计划修改 POM。可供专项研究参考。 |
| `yuweiju-document/execplans/ExecPlan - 拆分后端业务边界.md` | 原位，未采纳的全面迁移方案 | 模块化目录与 ADR 0001 冲突；抽取职责问题背景，不能作为现行分层规范。 |
| `yuweiju-document/execplans/计划执行顺序与依赖矩阵.md` | 原位，历史索引 | 只索引当时五方案；不能继续当当前总控。PLANS 链接失效需标记；Code Sytle 有效条款合并后改链/注明来源。 |
| `yuweiju-document/execplans/Code Sytle Guide.md` | 重写到 `docs/standards/backend.md`，删除冲突旧规范 | 按当前 issue 和实际代码重写，不保留旧规范继续充当约束；迁移计划、矩阵、自检中的链接。 |
| `yuweiju-document/自检报告.md` | 原位，2026-08-08 的检查记录 | 外部 `C:/Users/Endercloud/Downloads/Agent自检提示词-升级前工程校验.md` 已不存在；将其标为不可复现的当时来源。原报告“需确认全量规范范围”已被 10 月恢复访谈/规格具体化，不再问同一问题。 |


### 配置、代码与日志依赖

检索覆盖三端源码、构建清单、脚本及 ignored 本机配置；未发现业务构建或应用代码读取 `.trae/rules`、`.trae/skills`、`.claude` 内容。它们的主要消费者是代理/IDE 与显式文档引用。`.gitignore:23–29` 忽略 `.codex`、`.codex-logs`、`.claude`、logs；保留这些隔离措施。[S9][S10][S13]

| 路径 | 当前类别 | 依赖 / 处置边界 |
| --- | --- | --- |
| `logs/` | 装有过时输出的目录 | 六文件合计 1,663,664 字节，最近写入 2026-06-01；全部历史输出删除。未发现当前固定路径依赖，清空后可删除空目录；未来运行仍需要日志时由启动流程创建输出位置。 |
| `logs/analysis-manual-retrain-run.log`、`analysis-retrain-8081.log` | 历史训练/调试输出 | 分别记录端口占用与训练产出；仅为历史事实。维护者明确不需要原始证据，删除，不迁移摘要或原件。 |
| `.codex-logs/` | 历史输出和废弃工具混合 | 历史日志与旧启动脚本删除；TokenPrinter 无频繁使用证据，列为删除候选。成员清理且无现行依赖后删除空目录。 |
| `.codex-logs/run-backend.cmd` | 废弃本机启动脚本 | 第 5 行依赖同目录输出文件；按维护者不依赖手工工具的决定删除脚本，其重定向不再成为保留日志目录的理由。必要启动参数写进可复现说明。 |
| `.codex-logs/TokenPrinter.java` | 认证调试工具，删除候选 | 无已见调用或频繁使用证据，最后写入 04-19；详见补充核查。不执行、不输出 token；仅在发现频繁使用证据时单独判断。 |
| `yuweiju-backend/.claude/settings.local.json` | 已退役工具的本机配置 | 随 `.claude/` 删除，不迁入 docs 或共享技能。该局部配置仅有 rtk allow；删除废弃工具入口不改变项目业务认证或 Codex/全局安全设置。 |
| `.codex/config.toml` | 本机工具配置 / 旧元数据 | 引用旧规则和 PLANS；仅文本层依赖已证实。按 supported keys 单独整理，不用它扩大运行权限。 |
| `jmeter.log` | 过时负载测试输出 | 维护者已明确不需要原始输出，删除，不再为历史留证保留。 |
| `yuweiju-backend/runtime/` 及配置的日志/模型输出位置（若存在） | 业务运行资产，非本次规则目录 | 模型工件、训练状态与运行数据不能当 logs 清理；不移动路径、不清缓存、不更改轮转策略。 |
| `docs/verification-evidence/2026-10-05/` | 旧验证输出与脚本混合 | 过时结果、截图和原始测试输出删除；按用途核对脚本，仍用于当前 issue 的普通测试可保留，未再使用的手工驱动退役。报告文字结论可保留，附件链接随清理更新。 |

本机 YAML 和示例的 `logging` 仅配置级别，未见固定日志文件路径；当前进程观察未见本项目 Java/node 服务。这只支持“当时无已见运行写入”，不能证明上述脚本下一次执行不再依赖目录。后端 `scripts/api_smoke.ps1:87,91,104` 明确向分类和菜品新增接口发 POST；技能中的验证步骤必须解释这些演示写入，不能无条件自动运行。[S7][S13]

`rtk` 是外部 CLI，并非 `.trae/skills` 提供的快捷。新规范不依赖废弃 Trae/Claude 配置或个人工具，普通命令应能完成检查；退役局部 Claude 配置不涉及全局安全设置。[S4][S9]

## 维护者已确认的处置决定

2026-10-06，维护者已回应原待确认事项。以下决定替代本报告此前的保留或询问建议；本轮仅更新报告，文件清理与规范重写尚未实施。

| 事项 / 原路径 | 已确认决定 | 目标路径与引用处理 |
| --- | --- | --- |
| 后端 `.claude/`、后端及管理端 `.trae/` | 以 Codex 开发为主，不保留 Trae/Claude 专属适配入口；尽可能删除目录及过时内容。仍有用的要求按当前 issue 重新写入统一规范，技能仅保留有当前任务用途的部分。 | `docs/agents/`、`docs/standards/`、必要的 `.agents/skills/`；更新根和三端 AGENTS、研究及配置中的旧路径，不复制整套旧规则。 |
| 互相矛盾的规范 | 删除冲突的旧规范并重写，依据当前代码、已接受决定及推进中的 issue；不再询问旧动效、字体或纹理规则的适用范围。 | 统一规范按三端分文件，AGENTS 只保留入口与必要边界；重写前读取相关 issue 正文、评论与状态。本轮没有读取远端 issue，不宣称已对齐其最新状态。 |
| `yuweiju-web-vue/.trae/skills/web-design-guidelines/SKILL.md` | 已明确不再使用，删除，不迁入 `.agents/skills/`。 | 无目标文件；清除发现入口及直接引用。项目已能三端跑通，不保留为新布局开发准备的废弃审查流程。 |
| 所有过时测试输出，包括 `logs/*.log`、`.codex-logs/*.log`、`jmeter.log` | 删除，不需要为论文、答辩或复现保存原始训练、启动、负载测试输出，也不要求先归档摘要。 | 删除原始输出；报告保留必要的文字结论，将来源标为已清理的历史输出。10 月 5 日验证证据中的过时结果、截图和测试输出同样纳入清理；运行中的输出及模型/业务数据另行按实际用途区分。 |
| `.codex-logs/run-backend.cmd` | 废弃本机启动脚本 | 第 5 行依赖同目录输出文件；按维护者不依赖手工工具的决定删除脚本，其重定向不再成为保留日志目录的理由。必要启动参数写进可复现说明。 |
| `.codex-logs/TokenPrinter.java` | 认证调试工具，删除候选 | 无已见调用或频繁使用证据，最后写入 04-19；详见补充核查。不执行、不输出 token；仅在发现频繁使用证据时单独判断。 |

### TokenPrinter 补充核查

2026-10-06，在三端、docs、`.scratch/`、`.codex-tmp/` 及项目隐藏文件中检索 `TokenPrinter` / `token-classes`，除工具自身与本报告外未发现引用；源码和 class 的最后写入均为 2026-04-19，两者创建相隔数秒。Git 不跟踪该 ignored 工具。上述事实支持“没有已见频繁使用证据”，不能从写入时间推断执行次数；本次未读取私人 shell 历史或执行认证工具。[S13]

原待确认事项已得到处置方向，不再重复向维护者提问。只有后续取得 TokenPrinter 近期频繁调用的具体证据，才需要单独判断。

## 后续引用迁移顺序（未实施）

1. 读取相关 issue 正文、评论、状态及当前实现，依据 accepted ADR 重写 `docs/agents/workflow.md` 与三端 standards；删除互相矛盾的旧要求，不复制全量门禁或恢复 PLANS。
2. 仅重写当前 issue 仍需使用的项目技能到 `.agents/skills/`，核验 Codex 发现与触发；web-design-guidelines 删除，不保留 Trae/Claude 适配入口。
3. 更新四个原位 AGENTS、现行 docs/agents、restore-spec 和受影响研究的入口；历史计划/验证记录保留当时来源身份，补充迁后路径和退出流程说明。
4. 更新 `.codex` 的有效元数据，清除失效 To-do 前置引用，校正实际素材路径。旧计划中拟创建文件与真实已存在文件分开，不能把“计划目标”报告为失效的当前运行依赖。
5. 新规范落地并更新引用后删除 `.trae/`、`.claude/`、冲突旧规范、旧手工启动工具与过时测试输出；核查活跃写入和真实运行依赖，不清理业务数据/模型工件。TokenPrinter 仅在发现频繁使用证据时单独判断。

不新增 hash、冻结 contract、baseline 或 gate；本报告没有需要这些机制而 Git、版本号、主键、事务、唯一约束、类型和普通测试不足以解决的具体失败场景。

## 来源索引与本次验证

下列编号引用的是本次直接读取的原始文件或已有研究；行号用于定位关键证据，迁移后应更新相对链接，不把行号当冻结机制。

- **S1**：[根 AGENTS](../AGENTS.md)，第 8–18、41–55 行；根/三端文件的 Git 首次导入记录 `2106c3d`。
- **S2**：[domain](agents/domain.md)、[issue-tracker](agents/issue-tracker.md)、[triage-labels](agents/triage-labels.md)、初始化草案（已退役，原路径见表）。
- **S3**：[后端 AGENTS](../yuweiju-backend/AGENTS.md)、[后端规则（迁后）](standards/backend.md)（§1、§11、§12）与 [后端技能（迁后）](../.agents/skills/yuweiju-java-backend/SKILL.md)。
- **S4**：[管理端 AGENTS](../yuweiju-web-vue/AGENTS.md)、[管理端规则（迁后）](standards/admin.md)、[stack（迁后）](../.agents/skills/yuweiju-stack/SKILL.md)、[UI（迁后）](../.agents/skills/yuweiju-ui/SKILL.md)、外部审查技能（已退役，原路径见表）。
- **S5**：[小程序 AGENTS](../yuweiju-weixin-miniapp/AGENTS.md)、[app.json](../yuweiju-weixin-miniapp/app.json)，以及实际 common/pages 内容。
- **S6**：[架构研究](yuweiju-architecture-research.md)、[判断访谈](yuweiju-judgement-grilling.md)、[恢复访谈](yuweiju-restore-grilling.md)、[恢复规格](yuweiju-restore-spec.md)、[最小验证](yuweiju-minimal-verification.md)、[ADR 0001](adr/0001-retain-conventional-backend-layering.md)、[ADR 0002](adr/0002-preserve-historical-order-images.md)、[GLOSSARY](../GLOSSARY.md)。
- **S7**：[后端规范研究](yuweiju-backend-standards-research.md)、[POM](../yuweiju-backend/pom.xml)、[ApiResult](../yuweiju-backend/src/main/java/com/codeying/result/ApiResult.java)、[PageData](../yuweiju-backend/src/main/java/com/codeying/common/page/PageData.java)、[Orders](../yuweiju-backend/src/main/java/com/codeying/entity/Orders.java)、[UserAiAssistantController](../yuweiju-backend/src/main/java/com/codeying/controller/user/UserAiAssistantController.java)、[管理端 package.json](../yuweiju-web-vue/yuweiju-admin/package.json)、[http.ts](../yuweiju-web-vue/yuweiju-admin/src/api/http.ts)、[素材复制脚本](../yuweiju-web-vue/yuweiju-admin/scripts/copy-ref-images.cjs)、[烟测](../yuweiju-backend/scripts/api_smoke.ps1)、[API](../yuweiju-document/api/)、[数据库设计](../yuweiju-document/db/数据库设计文档.md)。
- **S8**：[8 月计划目录](../yuweiju-document/execplans/)、[自检](../yuweiju-document/自检报告.md)，尤其 Code Sytle 的冲突规范与历史方案身份。
- **S9**：`本机 config`（本机配置，已忽略）、Claude 本机权限（已退役，原路径见表），以及源码/构建/脚本搜索结果；本报告没有修改这两份 ignored 配置。
- **S10**：[.gitignore](../.gitignore)，凭据、输出、工具隔离条目；`git ls-files` 与 ignored/hidden 清单对照。
- **S12**：[Codex 官方技能发现](https://learn.chatgpt.com/docs/build-skills)、[官方配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)。技能文档由 `developers.openai.com/codex/skills/` 跳转；本次只据其证明 Codex 行为。
- **S13**：logs（已退役，原路径见表）、.codex-logs（已退役，原路径见表） 的文件级大小/时间/类型清单，脚本与配置静态引用检索、当前进程命令观察；未输出密钥、token 或日志正文中的敏感参数。

验证方式：隐藏/ignored 范围补扫、Git 文件历史与状态对照、路径存在性检查、源码/配置/脚本引用检索、报告的本地链接与分类覆盖检查；本次补充检查 TokenPrinter 引用和文件时间，并按维护者回应核对各节处置是否一致。本轮没有执行构建或业务烟测；已有三端验证结论引用 10 月 5 日材料，并保留其失败和限制。技术事实与维护者取舍分列，未将可调查问题扩成新的权限请求。
