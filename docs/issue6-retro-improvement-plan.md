# Issue #6 全流程复盘改进方案

2026-10-11，用户要求按执行报告的全流程复盘使用 yuweiju-retro-improve 实施。本轮在项目内 `.scratch/issue6-retro-improve-20261011`、分支 `codex/issue6-retro-improve-20261011` 执行；远端 `git ls-remote origin refs/heads/main` 与 origin/main 均为 `5271d78a97b608e46d5a57336764be28489f6e2d`。根目录仍为用户的 `codex/issue-4-refimg-scripts`，AGENTS、PLANS、local-development 及未跟踪材料保持。本轮未获 GitHub 写入、发布或业务 AFK 重启授权。

## 现状与选择

| 复盘条目 | 当前证据与缺口 | 本轮处理 |
| --- | --- | --- |
| 时间语义、支付按钮 | main 已包含 history-source/tests/template.test.cjs 的真实 SFC 行选择器、未过期/过期及路由测试；#6 已合并 | 业务修复已解决跳过；把调用链断言方法加入 AFK 验证指导 |
| 整页 tab 文案误匹配、底层 formatter 误报 | 上述测试已按订单行检查；runtime.md 尚未说明宿主断言也要走真实模板 | 实施通用指导，保留错误断言证据，不将其误报算业务回归 |
| IDE 登录/auto 与 SDK 就绪不同 | acceptance.md 已有编译、截图超时和人工安全设置；startup.md 无目标页及版本实查要求 | 实施启动前检查步骤，保留现有安全边界 |
| 原生 Page 与 Vue 方法不同、支付页 data 超时 | #6 驱动已修正；通用 acceptance 无事件和导航替代证据说明 | 实施指导，替代检查须覆盖原业务结论 |
| mock-login 固定 openid | startup 已有匿名/正确/错误身份预期；未说明不同 code 不等于不同身份 | 实施合成身份核查与夹具范围指导 |
| Windows GBK 与 Linux LF | runtime.md 已有 UTF-8/LF 写法；尚未规定配置读取显式 UTF-8 | 补读取与序列化指导，已有 LF 措施保留 |
| #13 等已有 runtime 改进 | 当前 main 已含 preflight、统一 verifier、网络、预算与停止记录 | 已解决跳过，不改监督器或重跑其 lifecycle |

## 范围与验证

按表顺序修改 AFK 技能入口和三个按需参考；不新建技能、探针工具或 gate。三端业务源码、接口、schema、认证及数据均无需改变：本轮修正的是宿主准备/验证方法。复用 #6 精确候选与交付验证证据，不重跑业务测试、IDE 或数据库，不启动真实 AFK。当前 Docker 具名 #6 live 容器查询为空；交付报告记载已停止并保留合成卷。

修订前后用相同五个失败场景做只读子代理应用测试（writing-skills 要求），记录文档缺口和修订后动作；随后运行现有相对链接检查、宿主 diff 检查并核对变更范围。应用测试只证明指导可检索并可应用，不证明官方 SDK 或监督器新增自动行为。证据留在该工作树 `.scratch/retro-evidence/`，最终报告记录实际命令、退出和限制。

回退为恢复本轮技能/参考文档，或移除本维护改动；不涉及数据库迁移与服务回退。初次实施完成后保留独立工作树供审查。维护者随后明确授权对应提交、推送、按 pr 技能创建 PR 及合并；交付阶段在该授权内进行，不含部署。
