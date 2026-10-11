# #10 宿主复盘改进方案

用户授权：在当前聊天按最终交付报告所关联的 `/retro：一次宿主复盘` 使用 yuweiju-retro-improve 实施改进。该六项表位于 `docs/issue#10-executing-report.md`，不是交付摘要正文。维护授权不继承业务交付的 GitHub 写入权限。

起点：本机 main/origin/main 和 Ubuntu `git ls-remote origin refs/heads/main` 均为 af04c520666f692a2a355a7bfca5658061162d4d。根工作区仍为 ecd2029 的 codex/issue-4-refimg-scripts，已有 AGENTS 修改、PLANS 删除、local-development 修改和未跟踪材料保留。独立维护树为项目 `.scratch/issue10-retro-improve-20261010`，分支 codex/issue10-retro-improve-20261010。工具来源为此 main，不沿用根工作区旧 runtime。

| 建议 | 当前证据与处理 |
| --- | --- |
| 真实金额链路 | 已解决跳过业务修改：#10 独立 d0f1874 的五组检查 exit 0，真实 probe businessPassed/cleanupPassed=true；最终人工确认21/40及旧38保持。启动参考补齐未来金额场景的证据要求 |
| task-input 定位 | 本轮实施：实际 mount 为 /home/agent/task-input，common 的轮次提示外还会包裹 Git safety；在最外层 git-safe-agent 提示首行直接说明 mount/worktree，保留完整 safety，并要求准备 prompt 指向真实存在的 summary |
| JDBC/schema | 本轮实施准备指导：issue10-check.sh 已从完成的 jar 提取实际库，startup 已要求真实 schema；补齐安装后、写探针前的驱动/只读核查顺序，不增加 schema 迁移 |
| Mockito 重复设桩 | 业务已解决：74测试通过；本轮补齐针对旧异常设桩的 doReturn 指导及完整受影响集合重跑要求 |
| 官方/人工与截图 | 业务已解决：最终人工已确认编译及验收；本轮补齐受信任项目重编译确认、截图有限超时和三类状态分开记录。仅改技能指导，不声称新增官方驱动 |
| 独立 admin 缓存 | 本轮实施可选 verifier npm 下载缓存：不默认启用；Python/PowerShell 参数共享，具名目录绑定与来源记录。每次仍 npm ci 和完整检查，保留安装失败行为 |

#13 已合入的 Node 路径、UTF-8/非登录 shell、网络、脱敏日志以及 #7 的 preflight/轮次缓存/证据和预算保持。独立 verifier 当前没有跨检查下载缓存参数，编码轮次的 .npm 缓存不能冒充独立缓存。

比较方案：只补文档无法消除监督器首行和 verifier 缓存缺口；自动持久共享缓存会改变默认行为/资源归属。本轮选择首行定位及显式可选缓存，配合针对性技能参考更新。

三端影响：后端、管理端、小程序业务源码、接口/schema 和数据不改；仅运行时提示、独立依赖下载方式和准备/验收指导变化。复用已验收的计费业务证据。无新 hash、contract、baseline 或 gate，既有防护保持。

验证：普通 diff/link/PowerShell语法及 TypeScript 检查；用无模型场景捕获编码 prompt 首行并检查只读映射；修改后的统一 verifier 分别验证未指定缓存和指定缓存，新快照重新安装管理端依赖并完整检查，保留 exit/stopped/authMounted/modelCalled。另用新快照的失败场景确认缓存不能掩盖 npm ci 失败，不重复已通过的同源码完整检查。新项目产物仅在项目 scratch；不启动 AFK/人工服务、不访问原库。

回退：普通 Git diff/revert 可恢复运行时和技能；不传缓存参数恢复原安装方式。保留缓存和证据，不删除合成卷、历史工作树或用户文件。实施阶段未授权提交、推送、PR、合并或部署；维护者随后明确授权对应提交、推送、使用 pr 技能创建 PR 及合并，部署仍不在范围内。
