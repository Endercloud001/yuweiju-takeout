# Issue #16 复盘改进结果（2026-10-14）

四项建议已在本地独立维护工作树实施。起点为 fetch 确认的 main `3a390da1cef3fd724188ec06b3708ce00b8a9e72`；分支 `codex/issue16-retro-improve-20261014`，目录 `E:/Learning Files/yuweiju-takeout/.scratch/issue16-retro-improve-20261014`。[范围与方案](issue16-retro-improvement-plan.md)。初步实施时改动仅保留本地；维护者随后明确授权本任务提交、推送、使用 pr 技能创建 PR 并合并。交付前重新核对远端 main，评审最终差异，并用统一 verifier 对精确提交执行受影响检查。合并状态以远端结果为准，无部署授权。

| 项目 | 实际改动与证据 | 状态及边界 |
| --- | --- | --- |
| chunked/真实额度 | [共享辅助代码](../.sandcastle/environment/model-relay-support.py) 和 [接入示例](../.agents/skills/yuweiju-afk/references/acceptance-examples.md)；本地HTTP服务实际收到 Content-Length/chunked JSON，坏 framing 不进入替身；扩展/trailer、缺失/截断/超限输入检查通过；24个并发预留仅6个成功，重启不重置，写入失败不返回预留成功 | 已实现、普通测试通过；没有启动通用代理或真实请求。单进程共享实例边界明确，后续任务单独授权上游与最大次数 |
| SDK 映射定位 | acceptance 主参考新增分支入口；示例先实查项目/currentPage，再从源码映射定位实际渲染节点并检查按钮与后端数量 | 指引已完善；本轮未连接 SDK。复用 #16 的 wx-dish-card 真按钮证据，不声称新驱动已运行 |
| 截图回退 | 指引明确SDK优先、保存额度错误、原生截图核实项目与模拟器区域、查看关键业务画面、错图标无效并保留 | 指引已完善；本轮没有新增截图，不清理共享存储 |
| UTF-8/紧凑输出 | preflight/verify-task/verify-lifecycle 对本轮涉及宿主文本读写显式指定UTF-8；[证据摘要入口](../.sandcastle/read-verification.py) 固定UTF-8、限制检查/摘要数、保留来源和失败状态 | 已实现；真实 #16 resources/check日志只读验证通过，正确输出156测试/0failure/0error/0skip及独立通过、人工未执行，未把历史独立记录改成人工验收结果 |

已解决跳过：#13 和现有 main 的容器 locale、check.sh UTF-8/LF、preflight、统一 verifier 及原有隔离/认证/停机措施。未选项无；本轮没有受阻项。维护不改变监督器、SDK、业务接口、schema、三端源码或数据库，因此没有重跑业务构建、真实模型与官方页面；准确候选 `69f75c0` 的已有证据仅用于历史业务事实。

## 验证与失败记录

所有命令在上述维护工作树执行，完整结果留本地 `.scratch/maintenance-evidence/checks.json` 和对应 `check-N.log`，此目录不提交。

| 命令 | 退出码与结论 |
| --- | --- |
| `python .sandcastle/test-issue16-retro.py` | 0；7测试通过。本地HTTP替身、framing、额度、UTF-8、坏输入、输出限量以及现有 lifecycle 入口真实源码的失败路径（进程/fixture替身） |
| `python .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . <变更文档>` | 0；acceptance 主参考/新示例、verification、方案和本报告相对链接可达 |
| `python .sandcastle/read-verification.py <#16原始resources.json>` | 0；两项检查通过、stopped=true、156测试摘要，完整正文留原日志 |
| 六个受影响Python文件 `ast.parse` | 0；语法通过，不启动入口 |
| `git diff --check` | 0；无空白错误 |

首轮6测试有1个测试代码错误：本机 Python3.10 的 HTTPConnection 不支持上下文管理协议，退出1。将测试改为 contextlib.closing 后通过；没有掩盖生产异常。首次失败摘要保存在本地 `first-failure.txt`，原始输出保留聊天工具记录。新增 lifecycle 场景实际执行已有入口源码，替身返回退出7及中文失败日志，确认非零状态和 UTF-8 尾日志被保留。没有执行九项真实Docker生命周期全套，不将本地进程替身宣称为该全套通过。

## 指引应用场景审查

| 输入场景 | 采用新指引后的动作与完成判断 |
| --- | --- |
| 源码 dish-card 查不到 SDK 节点 | 实查当前项目、页面及实际映射；定位真实按钮后 tap，并确认受保护购物车数量；未查到或无业务变化仍失败 |
| SDK存在节点、page.data正确但tap未改变购物车 | 保留失败，不以节点/data/驱动exit0认定加购通过；复用成功真实卡片修正检查 |
| saveFile额度满，原生图拍到编辑器 | 保留SDK失败和无效图，核实项目窗口及模拟器区域后重拍并查看；本票页面看不到仍未通过 |
| 共享存储占满 | 不清理他票数据；选择本票捕获方法，用户重新编译/进入页时记录协作后取得有效图 |
| 真实请求超时、代理重启、持久写入失败 | 预留次数不退回、重启使用原ledger；写入失败先阻止转发，不通过删除记录恢复预算 |
| 中文日志/大量JSON/坏编码 | 显式UTF-8、只投影状态和有限数字摘要；坏编码非零，不用忽略错误解码 |

以上为指引逐场景静态应用审查，非新增官方SDK执行证据。文案改动不代表自动化监督器或截图驱动行为已修改。

## 资源、恢复与授权

本轮仅测试本机127.0.0.1临时HTTP替身，测试finally已shutdown/server_close/join，临时目录由测试清理。Docker检查没有运行容器；#16服务、模型额度与保留卷均未触动，没有新增计费、原库访问或AFK轮次。根分支/HEAD和用户已有tracked修改保留；未新增hash、冻结contract、baseline或gate。

恢复实施/审阅直接进入维护工作树，读取本报告与 git diff；新增文件用 git status 查看。回退仅撤销该树本轮列出的文件变更，保留原票证据和卷。维护者后续明确授权本维护任务提交、推送、维护PR及合并；按该授权交付，不继承业务票的其他权限。本轮不启动递归复盘，没有新增后续维护提议。

## 授权交付与可移植验证

最终维护差异共12文件；仅显式暂存本轮文件，项目内 .scratch 证据/临时文件不提交。初步验证摘要见 [可移植检查记录](verification-evidence/2026-10-14/issue16-retro/checks.json)，远端 fresh clone 可直接执行记录中的命令；该记录不依赖本机旧业务日志。统一 verifier 后续从精确本地提交创建新快照，运行本轮7测试和5份文档链接检查，不挂认证、调用模型或连接业务库。最后交付结果保存本维护目录 .scratch/publication/delivery-result.json，不为记录合并结果再创建递归维护提交。
