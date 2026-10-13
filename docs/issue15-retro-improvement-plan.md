# Issue15 Loop 维护方案

2026-10-13。授权来源：用户要求按执行报告 retro 环节使用 yuweiju-retro-improve 实施改进，随后明确授权本维护任务提交、推送、使用 pr 技能创建 PR 并合并。部署不在本轮范围。

工作树：项目 `.scratch/issue15-retro-improve-20261013`，分支 `codex/issue15-retro-improve-20261013`。起点和远端 main 已确认是 `5d90ea32723d92e4b1f56230828e77b8742f83c7`（gh REST GET，exit0）。根工作区 HEAD ecd2029 和全部用户修改保留。实际旧 Ponytail runtime HEAD fcf5d28、status 为空；它是依赖来源，不覆盖它。docker ps 当前无运行容器；#15 停止证据见原执行报告。

| 顺序 | 现状和范围 | 本轮处理 |
| --- | --- | --- |
| 1 宿主故障 | launch 继承输出，仅返回退出码；资源采样已有 | 持久保存脱敏 stdout/stderr、exit/signal、最后资源快照和来源；未知保持 unknown，不宣称停机 |
| 2 完成指令 | common 有 Ponytail 导航，prepare-input 没有收尾片段 | 自动生成 closeout-prompt.md，要求实际检查、提交、最终独占 promise；用 check:input 离线测试 |
| 3 几何检查 | 四宽度32消息测试已有，只绑定业务任务 | 接入既有 environment/runtime-check.sh；截图失败读官方 size/offset，人工视觉仍未执行 |
| 4 命令环境 | per-command 身份、ASCII、可写 preview 是本票修正 | 将核实和选择步骤写入现有 startup；不改 global Git 或正式配置 |
| 5 探针导航 | startup 已有 doReturn 与 JDBC 时间规则 | 复用，仅在生成摘要链接到既有段落；不复制规则，不改已验证探针 |
| 6 技能发现/输出 | 本轮可用技能已显式包含 retro-improve；根用户已规定输出经济 | 复用已解决部分；补显式命名文件的发现分支与技能应用场景检查 |
| 7 公共检查 | 已有 build/runtime/verifier，零 workflows | 复用既有普通检查入口；CI、pre-commit 和新 gate 未选 |

三端：只改维护脚本、技能参考和普通检查接线。后端/管理端/小程序产品源码、接口、schema、认证和原数据均不改，因此不需修改消费者。保留 Git 防护、预算、资源清理和既有验证；不新增 hash/contract/baseline/gate。

验证：先让新增离线输入/宿主异常夹具在旧实现失败，再运行修正版本；复用 check:types、check:input、check:lifecycle；布局用现有镜像无认证/模型的独立进程验证和负向回归。记录工作目录、命令、退出、资源和未执行项。公共完整 runtime 会启动业务服务，本轮只验证新增接线及布局，不重跑未变 Maven/admin/SQL。

回退：可普通 revert 本维护提交；不回滚数据、不移除旧证据或卷。旧 runtime 未自动安装本轮变更，后续使用须从本工作树明确准备。
