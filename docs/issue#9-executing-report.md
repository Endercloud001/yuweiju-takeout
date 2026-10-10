# Issue #9 执行报告

日期：2026-10-10。状态：编码完成、精确候选独立验证通过、维护者四组人工验收全部通过；授权交付进行中，最终远端状态以交付报告为准。

## 授权、起点与实际环境

维护者指定最多两轮、编码共享 60 分钟（含准备 worktree、依赖、检查、收尾），独立验证另 20 分钟；允许验收前本机隔离分支候选提交，独立通过后自动启动服务并保留到用户反馈。全部人工验收通过后才执行必要交付。没有真实数据写入/真实支付/正式发布授权。

[issue #9](https://github.com/Endercloud001/yuweiju-takeout/issues/9) OPEN、无评论；原生 blocker #2 CLOSED。正文、评论、原生依赖及抓取记录在 `.scratch/issue9-afk-20261010/input/`。GitHub 间歇 TLS handshake timeout：正文/评论使用 REST 成功；依赖重读失败后复用本轮准备最初成功响应的相关字段，来源与失败保留，未把失败当作成功抓取。历史两份 cart-add/checkout JSON 在整个项目含 hidden/ignored/保留快照搜索无命中，exit 1；记录缺失并新建隔离验证，未对原库重跑驱动。

main/origin/main 起点 `1baefd302c7c96a79dad0233ecc2fe0fb69c4550`，含已合并 #7/#8 和最新 AFK 工具。原工作区仍在用户分支，已有 AGENTS、PLANS 删除、local-development 及未跟踪资料均保留。旧 Git-safe runtime 落后，未覆盖它：本票在项目内新 local clone，移除 clone 的 origin；复用既有 WSL Linux node_modules symlink，精确本地 exclude，检查来源/cleanliness；不复制认证。

runtime：`.scratch/issue9-afk-20261010/runtime`；容器应用根 `/home/agent/workspace`，对应 runtime 原任务 worktree。输入目录不是应用根。Sandcastle 0.12.0、`gpt-6.1-sol / medium`；idle 600 秒、completion grace 60 秒、总预算内 closing 180 秒。Java 21.0.9、Maven、Node、Python3、Git 防护，镜像 `sandcastle:yuweiju-dev-git-safe`。专用登录只读检查 exit 0；同配置 preflight 工具/Git/网络/认证/本票 MySQL/Redis 全通过，preflight stopped=true，未把 HTTPS 可达冒充模型请求成功。运行前 npm 类型检查 exit 0。

编码/独立检查各用本票新 internal 数据网络和具名出口网络、新 MySQL 卷/Redis、无数据库宿主发布端口。数据目标 `mysql:3306/sandcastle_fixture`、`redis:6379/0`；独立验证没有认证挂载或模型调用。项目产物都在根目录内。

## 候选、范围与三端影响

分支 `codex/afk-issue-9-20261010`。业务提交 `4dc2e0c36f91416e17e2443c1afd2547f8364482`；记录提交 `c211467e6b90e277e39495a02d03a514b5c14813`，后者为精确验证/人工验收候选。与起点比较 13 文件、1001 insertions/305 deletions，`git diff --check` exit 0；每次显式暂存任务文件，逐轮最终 status 空。

变更：

- UserAddressBookController 移出规则/Wrapper/事务，调用 AddressBookService；Service/Impl 承担创建、编辑、详情、删除/默认替代及切换，校验归属并经 Spring 代理事务执行。
- AddressBookMapper 增加具名用户条件查询/计数/更新/删除/默认操作；简单 Wrapper 在 Mapper 内参数绑定，BaseMapper 主键 CRUD 保留。
- ShoppingCartMapper/ShoppingCartServiceImpl 的数量修改和删除增加 owner/ID 条件；保留 main 已有可售/商品口味快照/统计隔离/增减规则，不重复搬层。
- OrdersApplicationServiceImpl 的 submit/estimate 地址归属与 repetition 购物车清理复用具名用例；不扩展计费或历史查询。
- CartAddressRulesTest、Issue9Probe.java、issue9-check.sh、实施方案、业务验证文档与任务进度。

方案与详细职责链在候选 `yuweiju-document/execplans/Issue9 - 购物车与默认地址职责恢复.md` 和 `docs/issue9-cart-address-verification.md`。适用 backend 规范职责/具名 Mapper/事务/兼容/异常条款、ADR0001，以及两前端 API/认证/外观规范。

后端局部改动，API/schema/字段/认证和原数据/ID 不变。小程序首页购物车、地址列表/编辑/默认与结算沿用现有协议；管理端没有直接购物车/地址消费者，订单只消费已存快照，两前端源码无需修改。保留第一地址默认、普通编辑忽略 isDefault、删除默认选最大剩余 ID、空默认 null、默认优先/ID降序以及原负数量遗留行为；无新增可调数量 API/schema 或业务定义。

## 编码结果及失败批次

监督器 exit 0、有 COMPLETE、有两项真实提交、result 完整；只用了 iteration 1。2026-10-10T08:01:07.613Z 至 08:29:20.999Z，累计 1693.386 秒（28 分 13 秒），含全部编码环节，未重启或追加预算/轮次。主 resources 及每轮 stopped=true，agent exit 0，最终 HEAD c211467、status 空。编码容器已停止，原 worktree 被正常运行时清理；分支提交与逐轮文本证据保留，不声称原工作树仍可恢复。

所有编码命令 cwd `/home/agent/workspace`：

| 批次 | 退出/结果 |
| --- | --- |
| 1 package / probe | package 0，62 tests；probe 1，测试错误统一按 id 排序，风险表实际主键 order_id，发生业务写入前，cleanupPassed=true |
| 2 package / probe | package 0，62 tests；探针在 Maven 重打包完成前启动，BOOT-INF/lib 缺失，编译1、未运行Java/SQL；修正调用顺序 |
| 3 probe | exit 1，已覆盖认证/cart后，NULL 数量夹具被既有 NOT NULL 拒绝；保留 schema，防御 null 分支改用普通测试，cleanupPassed=true |
| 4 最终 package / probe | 两项 exit 0；63 tests，0 failures/errors/skips；206 业务/清理断言，businessPassed/cleanupPassed=true |

失败批次与最终 logs/JSON 在 `coding/codex-afk-issue-9-20261010-1791619267606-iterations/iteration-1/files/.scratch/issue9/`，逐轮 `iteration.json` 保存 HEAD/status/agent exit/完成信号。class/jar 依照文本证据规则跳过，不声称已归档二进制。中间检查不代替最后结果。

## 精确提交独立验证

使用起点 main 的最新 `.sandcastle/verify-task.py`，`--commit c211467e6b90e277e39495a02d03a514b5c14813`。外层 timeout 1200 秒覆盖快照准备与检查；配置 verificationMs=1200000。2026-10-10 16:29–16:34（Asia/Shanghai），约4分钟内完成，exit 0，未超时。精确 snapshot：`.scratch/issue9-afk-20261010/independent/task-review-1791620984633149277/snapshot`，容器 `/workspace`，新数据库/Redis、独立无共享编码缓存、authMounted=false/modelCalled=false。

| 命令（cwd /workspace） | 独立结果 |
| --- | --- |
| `export PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH; mvn -B -f yuweiju-backend/pom.xml package` | exit 0；63 tests，0 failure/error/skip；BUILD SUCCESS，2:56 |
| `bash .sandcastle/environment/issue9-check.sh` | exit 0；206断言，businessPassed=true、cleanupPassed=true |

resources.json 两步 passed、independentVerification=passed、stopped=true；exit-status.tsv 两项0，check-0/check-1.log、snapshot `.scratch/issue9/scenario-results.json` 为新鲜证据。未改前端源码，不以无关 npm 全套重建作为本票完成要求。

真实 MyBatis/MySQL 和 Spring proxy（AopUtils 断言），覆盖匿名/错误scope/双用户、直接复用用例归属、购物车可售/口味身份/快照/清理/空值、地址CRUD/排序/默认和替代。三个 task-owned MySQL触发器要求前一语句的作用已在同事务可见后再报错：切换清旧默认后目标失败、插入新默认后清旧失败、删除旧默认后替代失败；全量行/ID/default 恢复且另一用户不变。Cart SQL插入/更新错误因果链传播，行/数量/快照不变；已存在推荐Redis读取故障降级不吞核心失败。HTTP结算存订单/明细/地址快照，仅清自己的购物车；没有新增计费/真实支付。地图为独立探针Bean替身，其他核心组件真实执行。

finally 删除仅本轮自有行/键/触发器，比较原员工/用户/地址/cart/catalog/order/risk全表夹具与原Redis键/值保持。SQL回滚不撤销Redis、文件、HTTP或自增，未重置序列。以上独立检查不证明官方小程序编译或人机交互已通过。

## 隔离人工服务与待办

独立通过后按授权启动 `yuweiju-issue9-live-20261010`，目录 `.scratch/issue9-live-20261010/`。新 synthetic MySQL卷/Redis；后端业务class和依赖来自上述精确独立快照。宿主局部 LiveAcceptanceLauncher 只注入固定7分钟地图替身，不修改候选业务类、不调用外部地图。dev mock-login=true；不冒称真实微信/原库验收。小程序副本只改本机baseUrl。管理端用同一候选源码，独立Linux npm ci exit0仅作启动依赖准备，不转述为前端业务测试。

五个容器 backend/gateway/admin/mysql/redis 已记录在 containers.json；只发布127.0.0.1:18080和18088，MySQL/Redis仅internal网络，无Codex认证/模型调用。启动日志 start.log；prepared.json记候选/资源/模型与地图限制/保留至用户反馈。2026-10-10 16:36:44 本机无代理 readiness exit0：HTML/Vite资源、后端code1、直接和前端代理的cart/address三组匿名401/code0及模拟用户认证读取全部通过。该就绪登录仅创建本票合成用户，无cart/address写入。

完整四组步骤见 [人工验收](issue9-human-acceptance.md)。现有小程序/管理端页面验收待用户反馈。服务按授权保留，停止入口 `.scratch/issue9-live-20261010/stop.ps1` 已经PowerShell解析器检查通过，停止只针对本票Compose项目并保留卷。代码回退可正常revert；不能声称恢复已提交业务数据。没有静默到期清卷。

编码及独立Compose资源已down，具名出口网络已移除，查询这两个项目容器为空；code-stop.log/review-stop.log保留。编码和独立检查容器 stopped=true，live五个容器故意保持运行，其他任务未操作。必要GitHub交付仍等待全部必需人工验收。

## 一次全流程复盘

本次在宿主进行一次全Loop复盘：先检查编码/独立资源停止及live归属，再读result、逐轮证据、失败/最终日志、候选diff和独立结果。以下为建议，未自动修改技能或运行时，业务交付不等待环境维护。

1. **探针按实际主键取快照。** 批次1用id排序风险表失败；order_id事实在schema已有。建议编写新探针前核对实际表键，或复用已有按键快照助手；不新增hash/baseline/gate。效果：减少检查自身误报。状态：本票探针已改且独立通过，通用提示未改。
2. **完成package后再提取依赖。** 批次2在重打包中读普通jar导致缺BOOT-INF/lib。建议长构建与依赖提取保留顺序，复用现有退出码，不加新门禁。效果：避免重复编译和不必要check batch。本票已顺序化并独立通过，运行时未改。
3. **SQL边界夹具遵守当前schema。** 批次3写NULL number被NOT NULL拒绝。建议设计探针先区分可落库边界与仅代码防御分支；后者普通测试足够。效果：不为不可构造状态迁移schema。本票已采用，通用技能未改。
4. **成功抓取即时落盘。** 早期依赖读取成功仅在工具上下文，后续保存阶段遇TLS失败；本票保存了同轮成功响应的相关字段及来源，不能称后来读成功。建议首次读取即保留项目内原响应和命令时间/退出，再复用。效果：少一次网络请求、证据更完整；当前prepare.py逐项成功缓存和失败记录已用于本票，但初次响应只有相关字段，无完整原响应文件/精确时刻，明确证据限制。未扩展网络认证配置或自动恢复预算。

恢复：候选分支在本票runtime可读取；新鲜独立快照/编译产物和逐轮文本证据均保留。成功运行不授权任意重新启动AFK；若人工失败，先读具体步骤/时间/日志，按技能的结束运行恢复边界处理。等待人工反馈时不提前关闭issue。

## 人工反馈与合成口味编码修复（2026-10-10）

维护者反馈第2地址操作、第3删除、第4结算全部正常；第1购物车菜品/套餐增减、数量、金额、状态与不同口味分行正常，但口味弹窗乱码，提供截图。记录为第1显示失败，其余通过，尚未全部验收。不重启已结束的AFK，不新增编码轮次；宿主仅修正本票授权合成验收夹具。

使用diagnosing-bugs定位：截图静态中文正常，动态口味乱码。实际HTTP检查 `python -X utf8 .scratch/issue9-live-20261010/check-flavor.py before` exit1，UTF8解析后的JSON名称为U+00E8/U+00BE/U+00A3/U+00E5/U+00BA/U+00A6。MySQL查询 `HEX(name)=C3A8C2BEC2A3C3A5C2BAC2A6`，乱码已经存储，排除仅页面字体或HTTP解码原因。正确夹具文本为“辣度”，初始化SQL未设会话字符集，mysql以latin1读UTF8字节后写入utf8mb4造成二次编码。

已给本票 `prepare-live.py` 和生成的 `environment/fixture.sql` 加 `SET NAMES utf8mb4`；这是本机验收准备文件，未修改候选Java/页面、未扩大到通用运行时维护。针对id900010/dish900001、旧名称和值HEX条件一致才UPDATE，显式mysql utf8mb4会话、一次更新1行，`repair-flavor.py` exit0。没有重放整套fixture、重建卷、清理用户cart/address/order或修改其他行。

`check-flavor.py after` exit0，实际接口名称“辣度”、选项“不辣/微辣”完全正确。`check-import-encoding.py` exit0：同一真实mysql latin1初始会话和实际中文INSERT在session临时表中复现旧HEX乱码，再使用生成fixture首行SET NAMES后得到正确HEX E8BEA3E5BAA6；临时表随会话关闭，不写永久业务表。两项针对性检查证明修复存储和后续导入，无业务源码差异，因此无需重跑原候选整套构建或回滚测试。服务readiness再检查exit0、全项通过，2026-10-10 17:33:23（Asia/Shanghai）；服务继续保留。

失败/成功JSON、修复SQL及1行更新记录、临时表导入检查与用户截图保存在 `.scratch/issue9-live-20261010/flavor-*`。没有声称原生弹窗修复后已经由人验证；仅请维护者刷新/重新编译后复查口味显示，其他已通过反馈保留。当前仍待显示复验；未执行push/PR/merge/sync/close。原候选精确验证继续适用，本机合成数据修正单独记录，不冒称属于c211467提交或真实数据编码迁移。

## 维护者最终验收（2026-10-10）

维护者确认口味弹窗显示“辣度 / 不辣 / 微辣”正常，提供修复后截图并调用yuweiju-deliver。结合前一条逐项反馈，四组必需人工验收全部通过。修复前/后截图已脱敏检查并保存在 `docs/verification-evidence/2026-10-10/issue9-flavor-before.png` 与 [修复后](verification-evidence/2026-10-10/issue9-flavor-after.png)。业务候选仍为c211467，口味修复仅本票合成夹具，不需要追加业务代码或重启AFK。交付按会话已授权提交/推送/PR/合并/同步/关闭推进；历史“等待显示复验”段落描述当时状态，不代表最终状态。
