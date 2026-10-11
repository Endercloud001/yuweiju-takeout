# Issue #6 全流程复盘改进结果

2026-10-11，按用户指定[执行报告](issue%236-executing-report.md)的两处全流程复盘落实 AFK 指导改进。业务票 #6 已交付，不重开，也不开展第三轮编码。范围与现状见[方案](issue6-retro-improvement-plan.md)。

本轮从已核实远端 main `5271d78a97b608e46d5a57336764be28489f6e2d` 建项目内 `.scratch/issue6-retro-improve-20261011` 工作树，分支 `codex/issue6-retro-improve-20261011`。根目录用户分支及既有修改保留。本报告记录实施及提交前验证；维护者随后授权提交、推送、按 pr 技能创建 PR 及合并，最终合并状态以对应 GitHub PR 为准。

## 已实施

| 复盘失败场景 | 改动位置与行为 |
| --- | --- |
| 模板剩余时间语义错误、tab 误匹配、底层 formatter 误报 | [AFK 入口](../.agents/skills/yuweiju-afk/SKILL.md)指向 [runtime 行为检查](../.agents/skills/yuweiju-afk/references/runtime.md#页面源码恢复的行为检查)，要求实际保存 SFC/旧运行时、订单行选择器、事件/路由断言；先验证旧候选失败，区分错误宿主断言与业务回归 |
| 用户声明就绪但 SDK 无页/端口关闭 | [startup](../.agents/skills/yuweiju-afk/references/startup.md#官方小程序能力实查)要求 launch 前核目标项目、有限超时读取有效版本和实际页，保留人工安全设置与已有预算/授权 |
| 原生 Page/Vue 方法不同、支付页 data 超时 | [acceptance](../.agents/skills/yuweiju-afk/references/acceptance.md)指导触发实际 WXML 事件，核分页请求/记录；等价路由/ID/store 证据仅证明导航交接，保留原超时及未验证项 |
| 固定 openid 使不同 code 仍为同用户 | startup 要求查身份映射和实际主键，独立合成用户与现有签发助手，保持真实中间件签名/作用域/归属检查，凭据不落日志 |
| Windows 默认 GBK 读取中文配置 | runtime 增加显式 utf-8-sig 读取、utf-8/中文 JSON 写入及失败保留规则，复用已有 LF 写法 |

跳过已完成业务修复、history 模板回归测试、#13 preflight/统一 verifier/网络/预算/资源停止实现和已有截图超时要求。三端业务源码、API/schema、数据与监督器未变；没有新增 hash、冻结 contract、baseline 或 gate。这是技能指导改进，不声称监督器已自动执行官方 SDK 检查或已自动修复所有历史准备脚本的编码。

## 验证与证据

工作目录均为本维护工作树。`python .scratch/retro-evidence/verify.py` 记录以下实际命令的退出状态到 `.scratch/retro-evidence/verification.json`，完整输出留在同目录日志：

| 检查 | 结果 |
| --- | --- |
| 同一只读子代理修订前/后五场景应用测试 | 修订前3项不足、2项部分足够；修订后5/5通过，见[可随仓库读取的场景证据](verification-evidence/issue6-retro-scenarios.md)；原记录留在 scenarios-before.md / scenarios-after.md |
| 现有 check-doc-links.py 检查技能、三个参考、方案与本报告 | exit 0，无失效本地链接；新增中文标题锚点另经人工核对 |
| 宿主 git diff --check | exit 0 |
| git diff --name-only -- 三端目录及 .sandcastle | exit 0，输出为空 |
| git status --short | exit 0，四个技能/参考文件、本方案、报告、场景证据及本轮 .scratch 证据目录；仅前七个维护文件进入提交 |

场景测试同时检查非小程序任务不会被 SDK 条件阻塞、路由/store 不冒称支付渲染通过、不同 token 不冒称不同身份、隔离夹具不授权真实库或额外预算。该测试只证明指导可应用，没有重跑官方工具或业务服务。

复用 #6 原精确候选与交付验证，不重复业务测试：history32/32、checkout14/14、SDK12项、真实隔离API8项及人工通过见原执行/交付报告。主工作区 `.scratch/issue6-afk-20261011` 下首轮/第二轮 coding、第二轮两个 independent、refined-pending 及最终 delivery-verification 资源记录的 stopped 均已核对为 true；具名 live Docker 查询当前为空，命令退出0，摘要保存 `.scratch/retro-evidence/resource-check.json`。历史失败记录保持，合成卷保留。本轮未启动服务、未访问数据库，未新增需清理资源。

没有选定条目受阻。恢复入口为保留的维护工作树和本方案；本维护任务的提交/推送/PR/合并已获后续明确授权，未授权部署。回退仅恢复四个技能/参考文件，不涉及数据迁移。
