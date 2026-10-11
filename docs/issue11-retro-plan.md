# Issue #11 retro 改进方案

2026-10-12。授权：维护者要求按本地 `docs/issue#11-executing-report.md` 的 `/retro` 使用 yuweiju-retro-improve 实施；按表中顺序核对全部六项。在当前聊天实施，随后明确授权对应提交、推送、使用 pr 技能创建 PR 与合并；不包含部署或 Release。

起点：远端只读 `git ls-remote origin refs/heads/main` 与本地 main/origin/main 均为 `893f3097b29209ac9c65205e43018f38ec8aac3c`。项目内工作树 `.scratch/issue11-retro-20261012/worktree`，分支 `codex/issue11-retro-20261012`，初始干净。原工作区仍为 `codex/issue-4-refimg-scripts` / `ecd2029`，AGENTS 修改、PLANS 删除、local-development 修改及未跟踪材料保留。runtime 来源为此 main；旧主工作区技能不是当前能力依据。

| 项目 | 现状、缺口与本轮范围 |
| --- | --- |
| 失败恢复 | startup 已要求明确授权、原预算扣减及不自动重启；common 保留 result/resources。补 result 为空时的轮数来源、无提交草稿的 WIP 保存和实际算例；不实现自动重启。 |
| 巨大搜索 | runtime 已要求大日志留文件及窄输出，startup 已有精确文件名全项目寻证。补普通业务搜索与任务顶层发现范围，保留完整精确寻证入口。 |
| Java/JDBC/JSON 类型 | #11 的业务和探针已纠正，跳过源码修复；startup 仅覆盖 schema/classpath，补 Mapper/VO/JDBC 实际类型、先 compile 后 package 和时间比较。 |
| 身份/墙时间 | startup 已有身份映射要求；live prepare 已用显式 +08。补 dev mock 优先级、初始化后的身份/存储时间核对和时区使用条件。 |
| SDK 导航 | acceptance 已有 WXML 事件与等价导航证据；补旧连接超时后的同项目重连，以及重发有副作用 tap 前的持久状态核对。 |
| 收尾后 review | runtime 已区分快照和工作树；补 result 精确候选、新 snapshot、iteration/files 与 Git 对象恢复，不依赖已删除路径。 |

#13 已交付的 Node 路径、检查批次、网络接入、Git 防护和停机 lifecycle 均复用；没有未选项。原 #11 第一轮/续跑 resources 均 stopped=true，交付停止记录列服务空、端口无监听；本轮 docker ps 为空。不重启已结束服务、监督器或业务票。

实施仅修改 AFK 参考说明，按 writing-for-agents 保持入口与参考职责。三端源码、API、schema、金额和数据不变，管理端、小程序及后端无需同步修改。没有新增 hash、contract、baseline、gate 或依赖；保留认证和已有安全措施。

验证：修改前应用场景找具体缺口；逐个参考说明修改后用同场景复核。运行现有文档链接检查与 git diff --check，核对本轮无运行时/业务源码变更。文档变更不重跑已通过且未变的 #11 三端/真实隔离数据库测试，也不冒充 SDK 实机或监督器行为验证。结果写 [实施记录](issue11-retro-report.md)。回退为撤销本工作树选定文档改动，原用户文件和历史证据不受影响。
