# Issue #16 复盘维护方案

授权：2026-10-14 用户要求按执行报告「Ponytail与一次Loop复盘」实施改进，四项按表格顺序实施。初步实施仅本地维护；维护者随后明确授权本维护任务提交、推送、使用 pr 技能创建 PR 并合并。不继承业务票其他权限，无部署授权。

来源：项目根 `docs/issue#16-executing-report.md`；最新远端 main 经 fetch 确认是 `3a390da1cef3fd724188ec06b3708ce00b8a9e72`。根工作区仍在 `codex/issue-4-refimg-scripts` / `ecd2029`，已有 AGENTS 修改、PLANS 删除、local-development 修改与未跟踪材料全部保留。实施位置为项目 `.scratch/issue16-retro-improve-20261014`，分支 `codex/issue16-retro-improve-20261014`。运行时以此 main 为准，旧 #13/#15 工作树仅作历史证据。

| 选定项 | 现状与缺口 | 本轮实施、验证 |
| --- | --- | --- |
| chunked 与持久调用上限 | #16 task-local model-proxy 已成功；共享 fixture-server 的 PUT 支持 chunked，但 POST 和后续真实代理准备没有可复用示例 | 提供独立的 body 解码与单进程调用额度辅助模块、接入示例；本地 HTTP 替身验证两种 framing、坏输入、重启、并发、失败仍计数 |
| SDK 实际映射 | #16 wx-dish-card 的真实按钮证据通过；共享 acceptance 未说明源码标签与 SDK 节点差异 | 在 acceptance 分支参考中说明实际节点定位、tap 与后端业务结果对应；按无节点、存在节点但未加购两个场景审查 |
| 截图回退 | #16 曾误捕编辑器，后续 SDK 真截图通过；共享参考缺少窗口/模拟器核实 | 明确 SDK 优先、存储额度失败保留、原生回退核实及无效图处置；审查错窗口及共享存储满场景 |
| UTF-8 与精简 JSON | #13 及现有 verifier 已固定容器 locale、配置与脚本文本编码；宿主 lifecycle 失败尾日志仍默认解码，独立证据尚无共享摘要入口 | 修正宿主文本读取/写入；增加 verifier 证据摘要入口，仅输出状态、来源与 Maven 数字，完整日志保留本地；UTF-8/GBK、坏 JSON、超量输出场景验证 |

已解决跳过：容器 C.UTF-8、check.sh UTF-8/LF、既有 #13 preflight/精确候选 verifier、安全边界与停机流程。没有未选项；实际 SDK/真实模型重跑本轮不执行，复用 #16 已验证事实，维护测试只用本地替身。#16 delivery-result 已记 servicesStopped=true，当前 Docker 无运行容器；本轮不恢复业务服务。

三端影响：后端、管理端、小程序均无业务源码、接口、schema 或数据变化，故三端构建和业务回归复用已有准确候选证据。本轮不接原库、天气、OSS 或真实模型，不启 AFK。辅助模块不会自动启动代理；真实转发仍必须由后续任务单独授权。

验证：辅助模块的普通 unittest、既有文档链接检查、Python 语法及 git diff --check；源码改变后只重跑相关检查。文档按应用场景逐条审查，不把文案改动声称为 SDK/监督器行为变化。未修改生命周期行为，不机械重跑九个 Docker 生命周期场景。

回退：撤销本轮独立工作树中选定文件差异；保留历史业务证据、费用记录、卷及根用户文件。无新 hash、冻结 contract、baseline 或 gate。最终报告记录命令、工作目录、退出码、资源状态与未执行项。
