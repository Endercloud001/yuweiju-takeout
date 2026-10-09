---
name: yuweiju-deliver
description: Use when delivering a Yuweiju issue after human acceptance, preparing an authorized candidate or draft PR, or resuming delivery interrupted by GitHub failure.
---

# 验收后交付

输入：issue、候选分支/工作树、精确版本验证记录、人工反馈、已授权动作及发布目标。先读会话与仓库补齐事实；只有实际缺项才询问。技能提供流程，权限来自用户授权。

1. 阅读根 AGENTS、[workflow](../../../docs/agents/workflow.md)、[issue-tracker](../../../docs/agents/issue-tracker.md)，核对实际 checkout/HEAD、用户修改、issue最新状态/依赖、候选与验收项。逐项确认必需人工验收、反馈版本和操作授权。完成：交付范围、限制、授权及待办可指出；隔离反馈不等于真实微信通过。“候选交付”可输出本地差异/报告，推送或草稿PR仍需对应授权，合并/关闭等待其验收前提。
2. 读取最新 main 并在项目内独立工作树集成，保留用户当前分支及未提交修改；解决冲突、评审最终差异，复用现有评审和 pr 技能。用 [统一 verifier](../../../.sandcastle/verification.md) 运行受影响检查，未改源码的检查引用准确版本和原证据，不机械重复全量。完成：验证绑定最终候选；改动影响人工验收行为时先补相关验收，不能继承旧版本“通过”。
3. 只提交本任务文件，按授权推送并创建/更新唯一匹配 head/base/repo 的 PR。使用结构化工具参数或项目 `.scratch/` 内真实多行正文文件；正文描述最终范围、验收、验证与限制。读取远端核对 head.sha、PR范围与状态。写入超时先读远端，读不清就保留未确认状态停止重试。完成：推送提交和唯一PR均经远端确认，或具体失败及续接入口已记录。
4. 获合并授权且所有前提满足时，检查最终候选、PR状态、实际平台检查/评审要求，按已授权方式合并；平台拒绝就记录原因。再次读取 merged/head/merge_commit_sha，安全同步本地引用，不切换或覆盖用户分支/修改。完成：远端合并提交明确；未授权的合并保持等待。Release、部署分别需要明确版本/目标与权限，不能从“发布”自行发明标签或生产目标。
5. 全部必需验收满足且获关闭授权时，记录最终结论并关闭issue，远端再查关闭状态；已关闭对象不重复评论/关闭。按 [人工验收约定](../yuweiju-afk/references/acceptance.md) 停止本票临时服务或记录保留至期，保留卷。完成：交付报告列候选、最终提交、PR/issue链接、逐项状态、服务状态、未完成项及恢复方式。

仅授权草稿时创建并保持 draft；转为 ready 需对应授权。已有授权持续有效；用户说“人工验收通过”不是新增合并/部署授权。失败从已确认完成的步骤接续，不重建重复PR，不重复业务写入。交付只做本票，不把复盘维护变成关闭业务issue的前提。

示例：`使用 yuweiju-deliver 交付 #N：候选…，人工必需项全部通过，允许必要提交、推送、PR、合并、同步和关闭；发布范围为 GitHub main。` AFK启动时已给上述授权且反馈全通过，也可自动进入本技能。
