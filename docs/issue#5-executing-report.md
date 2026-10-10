# Issue #5 执行报告（2026-10-10）

最终候选 d0f18743e4b394aa9304cba20d2528d76a7684ca 的独立自动检查与维护者人工复验全部通过。按已授权 yuweiju-deliver 流程交付；下文保留历史失败及其修复过程，远端结果另见交付报告。

需求来源：[issue #5](https://github.com/Endercloud001/yuweiju-takeout/issues/5)。开始时 OPEN、无评论，原生阻塞 #2 已 CLOSED。会话授权最多两轮、编码累计 60 分钟，独立验证另留 20 分钟；通过后允许自动启动隔离服务，全部人工验收通过后才授权交付。维护者确认优先保留 uni-app/Vue 的最小 `.vue` 编译链，允许原生 Page 作为后备。本候选无需后备方案。

## 候选与范围

起点 `612c6376a7a5e86c062424300bc722e581d7f590`，候选 `e6c8af5f413fc21fd83b3d0a2100b7325df6ee81`，任务分支 `codex/afk-issue-5-20261010-resume1`。仓库为项目内 `.scratch/issue5-afk-20261010/runtime`，候选工作树为该目录 `.sandcastle/worktrees/codex-afk-issue-5-20261010-resume1`。根工作区既有用户修改未覆盖，未提前集成到根分支。

14 个变更文件：

- `yuweiju-document/execplans/Issue5-checkout-source-recovery.md`。
- `yuweiju-weixin-miniapp/checkout-source/README.md`、`build.cjs`、`package.json`、`package-lock.json`。
- `checkout-source/src/Checkout.vue`、`checkout.js`、`legacy.js`。
- `checkout-source/tests/build.test.cjs`、`checkout.test.cjs`、`legacy-harness.cjs`。
- `yuweiju-weixin-miniapp/pages/order/index.js`、`index.json`、`index.wxml`。

恢复结算模板、样式和逻辑的可编辑来源，锁定最小编译依赖，输出结算四个配套文件。Node 要求 22；uni-template-compiler `2.0.0-22420190823021`、Vue 模板编译器 `2.6.10`、ES2015 模板编译器 `1.9.1`、esbuild `0.25.12`。复用现有 Vue/uni/Vuex/API 运行时，未以手工修改 vendor 代替源码恢复。生成 WXSS 与旧文件相同，其他 12 页、共享 vendor、App 和 API 不变。增加提交处理中状态、防重复和失败后可重试；费用仍按旧规则，2 份单价 18 的示例为打包 2、配送 6、合计 44。

三端影响：仅结算页及其构建来源改变；地址、备注、购物车、支付等旧页联动保留。后端和管理端无业务修改，公开路径、字段、authentication、数据库均不变。模拟支付保留，不证明真实微信收付款。

## 编码运行与恢复

证据统一在 `.scratch/issue5-afk-20261010/`。模型 `gpt-6.1-sol`，medium；镜像 `sandcastle:yuweiju-dev-git-safe`，专用认证保持既有设置。idle 600 秒、completion 60 秒、收尾 180 秒包含在编码预算。

第一次 supervisor：10:17:33.508Z 至 10:31:51.529Z，14 分 18.021 秒，1 轮。Codex 会话记录报 `failed to record rollout items: thread ... not found`，exit 1，无完成标记、无提交；不能以先前构建/测试通过声称本轮成功。`coding/*result.json`、`*resources.json`、日志及草稿保留，容器 stopped=true。

维护者明确要求“从中断的位置继续完成任务”后，保留草稿并用剩余预算恢复，未重置两轮上限。恢复配置只允许 1 轮、剩余 2,741,979 ms。第二次 10:34:00.164Z 至 10:41:40.926Z，7 分 40.762 秒，exit 0、完成标记和上述候选提交均存在。编码累计 21 分 58.783 秒，两轮；未开展第三轮。

恢复证据 `resume1/coding/*result.json`、`*resources.json`、`resume1/supervisor.log`；只有运行时进度文件未进入候选提交。首次准备的空提交、Windows 对 WSL 链接检查及非登录 shell 的 Node PATH 错误发生在启动 supervisor 前，修正路径后运行，不增轮次。首次代理测试夹具缺 Page/setStorage 支持后修正，最终 11 项通过；这些早期错误保留为历史，不能冒称每次检查都通过。

## 精确候选独立验证

宿主记录开始 10:42:15.870Z，结束 10:58:15.971Z，16 分 0.101 秒，未超 20 分钟。`verification-start.json`、`verification-end.json` 保留。精确快照：`independent/task-review-1791628936606130765/snapshot`；资源、逐项日志和退出记录在其父目录。

独立容器不挂认证、不调用模型，执行目录为快照根，前 3 项进入 `yuweiju-weixin-miniapp/checkout-source`：

| 命令 | 结果 |
| --- | --- |
| `npm ci --cache ../../.scratch/issue5/npm-cache --no-audit --no-fund` | exit 0 |
| `npm run build` | exit 0，四个结算产物 |
| `npm test` | exit 0，11/11，无 skip/fail |
| `python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . yuweiju-weixin-miniapp/checkout-source/README.md yuweiju-document/execplans/Issue5-checkout-source-recovery.md` | exit 0 |
| `node --check yuweiju-weixin-miniapp/pages/order/index.js` | exit 0 |

宿主 runtime 的 `git diff --check 612c637..e6c8af5` exit 0，实际 diff 范围已检查。独立资源记录 exitCode=0、stopped=true；不将代理 VM 测试当成官方编译。

官方开发者工具 CLI 为 `E:/微信web开发者工具/cli.bat`，IDE 57377、自动化 9420，SDK miniprogram-automator 0.12.1。旧页副本由起点提取，候选四个文件来自精确独立构建快照，替换同一受信任 `baseline-miniapp`，配置中的维护者 AppID 与信任选择保持。副本唯一服务地址改为 `127.0.0.1:18085`，未修改正式 vendor。`simulator-candidate.json` 记录来源。

官方 SDK 合成场景：地址跳转/选择返回、备注跳转/保存返回、餐具 3 份确认、配送日期与时间选择、提交跳转旧支付页、旧模拟支付成功页通过。检查请求 authentication 和提交 amount=44、addressBookId=900002、备注、tablewareNumber=3 均一致；exception 为空。证据 `after/scenarios.json`。API 全部使用宿主合成替身，这些场景没有访问原库，不能替代真实后端验收。

截图 `before/checkout.png` 与 `after/checkout.png` 已实际查看。卡片布局、字体、颜色、商品数量、费用和底栏一致；截图顶部状态区域显示不同造成约 37 像素的内容起点偏移，结算 WXSS 无差异。截图接口多次 8/45 秒超时，重新连接后成功取得候选主页截图。餐具/配送/支付弹层截图未取得，视觉需人工复查；最终重复截图批次达宿主 150 秒期限，不将该批次记为成功，之前成功的业务场景证据保留。一次旧支付页超时是夹具使用 UTC 字符串而旧页按本地时间解析，改为本地格式后复验成功，未改候选代码。路由检查从过短固定延迟改为最长 10 秒条件等待。

副本截图失败诊断使用过窗口抓取；未保留非目标窗口抓取，后续必须校验目标窗口确为前台，否则拒绝捕获。人工提供的截图仅作为当前界面线索，不作为候选通过证据。

## 本机人工验收

隔离 Compose 项目 `yuweiju-issue5-live-20261010`，目录 `.scratch/issue5-afk-20261010/live`；独立验证结束后启动，五个容器 mysql/redis/backend/gateway/admin 刻意保持运行。仅发布 localhost 18085（API）、18086（管理端），MySQL/Redis 使用新合成卷及 internal 网络，无认证挂载/模型调用、不读写原库。`stop.ps1` 只停止本项目并保留合成卷。

后端/管理端复用历史独立候选 `c211467e6b90e277e39495a02d03a514b5c14813` 的构建产物；Git 实际比较该提交与 e6c8af5 的两端源码无差异，见 `live/prepared.json`。这是相同源码的历史构建复用，不冒称新后端精确构建。地图只在验收启动器中替换为固定 7 分钟，dev mock-login=true，真实 JWT/归属校验保留。初始化 SQL 首行 `SET NAMES utf8mb4`，中文口味使用正常 UTF-8。API readiness 10:59:51.782Z exit 0：页面资源、shop code1、直接与代理匿名拒绝、隔离模拟登录及认证 cart/address 读取均通过；仅合成用户创建，无购物车/地址写入。

维护者点击“编译”后，SDK `live/ide-ready.json` 记录首页、syntheticRequest=false、syntheticToken=false、hasToken=true，确认旧替身清除。重载准备的本机路径错误已纠正；未清理原项目缓存。CLI auto 曾挂起，SDK 重编译后的实际状态单独检查，不能以 CLI 输出宣称重载成功。

请在当前 baseline-miniapp 进行：

1. 首页选择 `Fixture dish`（18 元），加入两份购物车，进入结算。确认数量 2、打包 2、配送 6、合计 44，外观与截图一致。
2. 选择或新增合成地址；返回结算确认地址。进入备注保存“少盐”，返回确认。新模拟登录用户可能需要先新增自己的地址，不冒用其他用户的夹具地址。
3. 打开餐具选 3 份，选择配送时间；确认弹层外观、关闭及回显正常。
4. 点击支付，确认进入旧支付页并完成模拟支付到成功页；返回首页确认购物车联动。只验证模拟流程，不使用真实支付。

记录具体失败步骤、现象即可；只有维护者明确反馈全部验收通过才执行必要提交/推送/PR/合并/同步/关闭。管理端可在 http://127.0.0.1:18086 查看合成环境，本票无需改变管理端流程。

## /retro：一次宿主全流程复盘

先核对首次失败、恢复、独立容器的 stopped=true；再读结果、失败/最终日志、候选 diff 和验证记录。当前工具无可调用 `/retro` 命令，因此在宿主按技能要求实施同等的一次复盘，未虚构工具调用。建议与实施状态如下，未修改通用技能或运行时：

| 证据 | 影响 | 建议 | 状态 |
| --- | --- | --- | --- |
| 非登录 shell 找不到 Node | supervisor 前启动失败 | 使用既有 Linux Node 绝对路径 | 本票 launcher 已修正，通用脚本未改 |
| Codex thread not found、exit 1 | 草稿存在但无交付提交 | 保留资源/草稿和失败日志，按明确恢复授权与原剩余预算继续；另查会话持久化原因 | 本票恢复完成，根因未确认，不改认证 |
| 官方截图间歇超时 | 视觉检查延迟 | 提前确认信任/模拟器可见，截图设限并即时落盘，始终如实区分交互与视觉 | 本票主截图成功，弹层待人工 |
| UTC 夹具被旧页当本地时间 | 支付误入超时路径 | 合成订单采用旧接口本地时间格式，路由使用条件等待 | 本票验证夹具已修正，业务无改动 |
| 目标窗口前台操作可能失败 | 窗口截图可能包含无关内容 | 使用 SDK；窗口捕获先检查前台归属，失败拒绝 | 本票已加检查，不保留无关捕获 |

恢复路径：保留 runtime 候选分支、首次草稿、独立快照及本机服务；结束的 AFK 不自动重启。如人工失败，先读具体证据并按剩余范围/技能恢复规则处理，不添加第三轮。不新增 hash、冻结 contract、baseline 或 gate。

## 人工失败：结算 44 元，订单 38 元（2026-10-10）

维护者提供结算及待支付订单详情截图，明确人工验收失败。只读查询隔离 MySQL 最新订单 id=4：商品明细合计 36.00、pack_amount=2、订单 amount=38.00；该次查询状态为已支付，说明金额差异不只是待支付页文本。没有修改订单或访问原库。

复现命令在根目录执行：`python .scratch/issue5-afk-20261010/live/check-order-total.py`，exit 1，报 `Stored order total differs from checkout total`；实际 JSON storedAmount=38、expectedCheckoutTotal=44、passed=false。证据 `live/order-total-repro.json`。这是既有合成订单的只读检查，不新建/重放订单。

核对实际服务构建来源的 `OrdersApplicationServiceImpl.submit`：用购物车商品金额与 packAmount 计算 `total=goodsTotal+packAmount`，不包含配送费，存入订单并返回同一 orderAmount。候选 `checkout.js` 延续旧规则 `商品合计+6+份数`，提交 amount=44/packAmount=2，后端重算为38。详情显示实际订单金额。因此不是 Vue 编译导致数字变更，也不能通过把支付页文本强制写44修复。

issue #5 正文明确“保持既有费用行为直到金额票修复”“不在本票改变收费规则或后端接口”。本地规格与 issue #10《订单计费：服务端可信收费并统一三端新单金额》规定新单为商品合计+每份1元打包费+固定配送费2元，两份18元商品应为40元；旧单不追改。修复不得简单给后端加6元或信任客户端amount。需同时覆盖服务端可信计费、结算展示、提交响应、支付/详情/管理端和落库，并检验金额篡改不能影响收费。

此前官方SDK场景的合成submit替身直接返回客户端amount，证明页面联动但未覆盖真实后端重算；此前报告已限定合成场景，现在明确它不能证明金额一致。先前验收步骤要求44元只适用于本票延续旧结算展示，遗漏与真实订单对照，不能作为全链路验收标准。待金额票修复后，按确认的新规则40元重测。作为本次既有复盘建议补充：未来金额场景应串联真实隔离后端并比较展示、响应、订单与明细，普通集成测试足够，无需新增gate或hash。

当前保留 e6c8af5、隔离服务与全部失败证据，未修改收费业务或启动第三轮。GitHub只读 issue 列表查询因 TLS handshake timeout 失败；#10范围依据本地保存的issue/spec，不宣称本次已重新确认其远端状态。继续金额票需维护者确认新任务预算，不能将原两轮预算静默重置。

## 金额票修复候选准备就绪（2026-10-10）

维护者明确授权 #10 使用新的最多两轮/编码60分钟/独立20分钟预算。#10 在本票 e6c8af5 上生成 d0f18743e4b394aa9304cba20d2528d76a7684ca，一轮32分43秒，精确独立自动检查13分33秒全部通过；该票报告为 `docs/issue#10-executing-report.md`。既有隔离服务与 baseline-miniapp 已切换至新候选，保留数据库和project/private信任配置，旧合成订单4仍38元。这里只记录新的修复状态，不覆盖前面的历史失败证据。

当前人工验收金额按已确认新规则：单份18为21、两份18为40，结算/支付/详情一致；此前44元步骤是修复前旧行为，不能用于新版验收。需重编译并继续地址/备注/餐具/配送/模拟支付/购物车联动复验。尚无维护者全部通过反馈，不关闭本票或金额票、不推进GitHub交付。新候选包含本票源码恢复，后续交付需按两票依赖和授权处理。


## 最新人工验收结论

维护者对精确版本 d0f1874 回复：『以上全部通过，无异常』。确认 baseline-miniapp 已重编译，结算外观、地址/备注/餐具/配送时间正常；单份18新单21元、两份40元，结算→支付→详情一致，旧38元订单仍38元；模拟支付成功、购物车联动正常。此结论替代此前待反馈状态，不抹去历史失败记录。原库974未访问或修改；隔离历史单与代码检查用于验证不重算，未宣称原库写入验收。沿用会话既有提交、推送、PR、合并、同步和关闭授权。
