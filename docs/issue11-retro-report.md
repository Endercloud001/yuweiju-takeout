# Issue #11 retro 改进实施记录

2026-10-12，当前聊天使用 yuweiju-retro-improve，按维护者指定执行报告 `/retro` 六项顺序实施。方案见 [改进方案](issue11-retro-plan.md)。成果位于项目内 `.scratch/issue11-retro-20261012/worktree` / `codex/issue11-retro-20261012`，起点是只读核对远端后的 main `893f3097b29209ac9c65205e43018f38ec8aac3c`。维护者随后授权对应提交、推送、使用 pr 技能创建 PR 与合并；交付状态由实际 Git/PR 记录确认，不包含部署或 Release。

## 逐项结果

| 项目 | 现状核对与本轮改动 |
| --- | --- |
| thread-not-found 恢复 | main 已有显式恢复授权、剩余额度与保留输入要求，复用。startup 补 result=null 时轮数/耗时来源，显式任务文件 WIP 或补丁承接、恢复来源和候选区别。没有实现或启用自动重启。 |
| 巨大 .scratch 搜索 | main 已有窄输出和历史文件精确寻证，复用。runtime 补业务目录 rg、具名任务顶层发现，再按已知证据路径读取；保留全项目精确文件名寻证，窄搜索不作为缺失证明。 |
| Long/DATETIME/JSON | OrdersMapper.countByStatus 返回 Long；Orders.checkoutTime 为 Date，Issue11Probe 已处理 JDBC LocalDateTime 和两种 JSON 时间格式，跳过源码修复。startup 补实际类型链路、compile 与 tests/package 分开，以及按既有时区语义比较 instant。 |
| mock 身份与墙时间 | WechatServiceDevImpl 的 mock-login=true 先返回固定身份，mock code 分支在后；历史 prepare-live 已改显式 +08，跳过旧启动脚本修复。startup 补初始化后真实登录主键/归属核对、stored/API/客户端倒计时核对和显式时区使用条件。 |
| SDK 作用域与跳转 | main 已有 WXML 事件与等价导航证据，复用。acceptance 补同项目/端口有限重连、重取 Page、读实际页面与持久状态；核对写入状态后才决定下一步，防止重复支付/取消点击。没有实现通用 SDK 工具。 |
| worktree 收尾后的 review | runtime 补 result.commits + diff 选精确 SHA、确认 Git 对象、统一 verifier 新 snapshot；保留 iteration/files，缺对象只能恢复 draft 后新验证，WIP 不算验收候选。 |

没有未选项或本轮受阻项。#13 已交付的 Node 路径、检查批次、双网络、Git 防护与 lifecycle 停机保留；现行 main 后续的预检、恢复预算和 SDK 指引均保留。共享说明是本轮修正范围，不能把说明更新称为监督器或 SDK 行为修改。

## 事实与证据

原第一轮 `coding/codex-afk-issue-11-20261011-1791723676056-{result,resources}.json`：result=null，resources 1轮且 stopped=true；宿主 started 13:01:16.063Z / ended 13:10:14.828Z，耗时 538765ms。原 3600000ms−538765ms=3061235ms，2−1=1轮，与 continuation 原配置相同。续跑宿主耗时 1452821ms、1轮，resources stopped=true；两轮合计 1991586ms，即33.19分钟。不能用 resource 的容器开始时间漏扣前置准备。

原候选 `5e94e10ca2858086fea145a733386b268267653f` 仍是可用 Git commit，成功轮的 `iteration-1/files` 保留；原独立验证快照及交付记录已确认精确版本，不需要依赖收尾后的 agent worktree 路径。历史 live `finish-native-pay.cjs` 实际用断开/重连后重新 currentPage 的路径。只读取这些源文件/记录，没有重跑原模型或业务写入。

本轮精简资源证据保留在宿主项目 `.scratch/issue11-retro-20261012/evidence/historical-resources.json`，未随 Git 提交，fresh clone 不包含本机日志。原停止证据位于项目 `.scratch/issue11-afk-20261011/delivery/services-stopped.json`，记录服务空、验收端口无监听、卷保留。本轮实施时 docker ps 为空；未启动模型、业务服务或微信项目。后续授权交付仅运行无模型文档 verifier，其资源停止单独记录。

## 验证与限制

应用场景验证由 writing-skills 指定的子智能体完成，仅阅读和回答；修改前对 startup/acceptance 找到草稿承接、实际类型/时间和失联重连缺口。runtime 首次读取与编辑重叠，随后明确用 `git show main:<path>` 补旧版本评估，未把该首次读取冒充修改前测试。六场景覆盖恢复、搜索、类型、身份/时间、SDK 与 snapshot，全部复测通过；普通 diff 遗漏未跟踪文件的提醒已补说明并复核。具体结果保留在宿主项目 `.scratch/issue11-retro-20261012/evidence/scenarios.md`。

实际工作目录为上述 worktree。现有 `E:/Miniconda3/python.exe .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . <选定文档>` 检查六份入口/参考/方案/记录，exit0、failures为空；`git diff --check` 为exit0。完整输出与退出状态保留在宿主项目 `.scratch/issue11-retro-20261012/evidence/{verification.json,links.log}`。实施结束 docker ps exit0、运行容器0。运行时脚本和三端源码未变，因此没有重新执行 Maven、前端构建、366 SQL/HTTP 断言、模型或 SDK 实机流程；旧业务测试只作为相应未变版本的历史证明。授权交付另用统一 verifier 检查最终提交快照中的文档链接，结果由宿主交付记录留存。

无 API/schema/金额/权限/事务/数据变更，三端无需修改。未新增 hash、冻结 contract、baseline、gate、依赖或强制检查；认证、Git 防护、原始失败和用户修改保留。真实微信、真机、真实支付退款、正式部署均未验证。用户主工作区仍是原分支和原脏状态；本次按维护授权独立交付，业务 #11 不重开、不重新等待交付。

恢复入口：本工作树三个 AFK reference 文件与本方案/记录；原始资料在项目 `docs/issue#11-executing-report.md`、`docs/issue11-delivery-report.md` 和具名 task 证据目录。回退仅撤销本轮选定文档修改，无数据库或外部副作用回退。本轮未发现需要另起维护的具体新问题。
