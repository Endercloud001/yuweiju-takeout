# Issue21 人工验收：44a40d3

候选 `44a40d336caa00dd4a293679c5528517ca1e78fe`。六项人工验收已由维护者针对同一候选明确反馈全部通过；服务已按约定停止并保留数据/证据。以下步骤为验收时清单。原数据库未访问，本页中的商品/订单/训练数据均为隔离合成；微信身份与1次模型请求为真实。

- 管理端：[独立本机登录入口](http://issue21-20261014.localhost:18321/login)，隔离账号 `sandbox_admin`，隔离密码仅在本机会话提供。
- 官方微信项目：`E:\Learning Files\yuweiju-takeout\.scratch\issue21-afk-20261014\miniapp`；自动化9431，后台18221。你已确认44a40d3重新“编译完成”。
- 截止：2026-10-15 22:37:54（上海时间）；全部通过时立即停止，保留卷、日志、截图和模型。停止命令见下方。

旧的127.0.0.1入口可能读取其他本机任务残留的失效token Cookie，出现自动进入工作台后401。已复现；服务/代理正常，改用本票独立域名隔离会话。正常密码登录后工作台、订单、统计、客服22个API响应无401，见 `isolated-entry-recheck.json` 与 `service-recheck-http.json`。候选、数据、服务期限不变。

## 必需项一次反馈

| 编号 | 操作与预期 | 当前自动证据 / 人工待做 |
| --- | --- | --- |
| 1 身份与权限 | 管理端错密码拒绝、正确密码登录；微信同账号身份稳定。换**第二真实微信账号**走实际登录，确认ID不同、看不到第一账号订单、第一账号订单详情拒绝；匿名保护请求拒绝 | 同账号严格微信交换及匿名实时401通过；第二真实账号自动检查未执行；维护者已反馈本项通过，合成探针不替代人工真实验收 |
| 2 新旧金额/履约 | 管理订单号 `1791990659278` 已完成21元；小程序同账号历史/详情21元；主页2元，结算18+1+2=21。隔离历史 `issue21-synthetic-history974` 保持19元 | 全部真实页面/SQL通过；请对照界面与截图，确认人工通过 |
| 3 正常/缺图/失败视觉 | 对照管理列表、详情弹窗/独立详情，小程序编译历史/详情及AI/客服；正常素材正常加载，历史空图/404有兜底、不改变19/21或历史图原字段，布局/风格保持 | 当前截图可查看，读前后SQL相同；六份10/5原素材缺失，974是新合成替代，需明确接受此限制 |
| 4 AI/客服/故障隔离 | 对照AI合法卡片、503“当前较繁忙”且无新增卡片、真实模型推荐；客服同账号双向消息。核对归属/关闭及附加失败不阻断核心、核心SQL失败回滚的探针结果 | 实际双端WS/卡片/失败页面通过；真实请求累计1/4。当前模型为本地valid夹具，错误/正常证据已保存，不需要追加真实模型调用 |
| 5 分析/风险状态 | 核对本次XGBoost/Smile/LR/RF新模型/快照/预测/激活、样本/窗口/阈值；旧active、SKIPPED/无数据/FAILED/PARTIAL不冒充新训练。接受合成指标不等于营业效果 | 独立17/18已训练通过、真实新产物可查；窗口/指标见报告与下方证据 |
| 6 职责/报表/安全与限制 | 对照十组Controller/Service/Mapper表及A1–A5；统计/工作台/店铺和上传等代表回归；确认未访问原库、无真实OSS上传，复用检查来源与未覆盖项明确 | 三端构建/普通测试及具名探针通过；源码一致检查复用边界见报告 |

## 查看证据

- [管理端完成21元](verification-evidence/2026-10-14/issue21/admin-same-user-completed21.png)、[管理端历史19元兜底](verification-evidence/2026-10-14/issue21/admin-synthetic97419-failed-image.png)、[独立历史详情](verification-evidence/2026-10-14/issue21/admin-detail-synthetic97419.png)。
- [官方结算21元](verification-evidence/2026-10-14/issue21/miniapp-chain-first-failure.png)（8e07cc2首个驱动失败时成功补拍；结算源码/产物与44a40d3相同，实际新编译21元已另验证）、[官方历史21元](verification-evidence/2026-10-14/issue21/miniapp-history-completed21.png)、[官方新单详情缺图](verification-evidence/2026-10-14/issue21/miniapp-detail-completed21-fallback.png)、[官方历史19元失败图](verification-evidence/2026-10-14/issue21/miniapp-detail-synthetic97419-failed-image.png)。
- [官方真实AI及失败提示](verification-evidence/2026-10-14/issue21/miniapp-official-ai-final.png)、[管理客服](verification-evidence/2026-10-14/issue21/admin-customer-two-way.png)、[官方客服](verification-evidence/2026-10-14/issue21/miniapp-customer-two-way.png)。
- [完整执行报告](issue%2321-executing-report.md)、[最后复核](verification-evidence/2026-10-14/issue21/final-review.json)、[本轮训练复制来源/指标](verification-evidence/2026-10-14/issue21/acceptance-fixture.json)。原始失败、资源记录、模型用量和逐场景结果在同一个task目录。

可用一条回复：`44a40d3：1通过（第二真实账号已测）/2通过/3通过（接受缺失历史原材料限制）/4通过/5通过（接受合成效果限制）/6通过；全部验收通过；异常无。`

交接前主页补拍再次遇到SDK超时，当前主页2元文字的官方元素读取已通过，未取得新的主页截图；请人工确认实际主页为2元。该补拍没有下单、支付或清存储。历史/详情/AI/客服使用上面的成功官方截图。

有失败或未执行时写对应编号、步骤、页面提示与上海时间；不要把未执行写成通过。反馈全项通过后按既有授权提交/推送/PR/合并/同步/关闭，停止服务并保留数据。

```powershell
wsl.exe -d Ubuntu --exec python3 '/mnt/e/Learning Files/yuweiju-takeout/.scratch/issue21-afk-20261014/acceptance.py' stop
```


维护者最终反馈：`44a40d3：1通过/2通过/3通过/4通过/5通过/6通过；全部验收通过。` [保存记录](verification-evidence/2026-10-14/issue21/human-acceptance.json)。服务已停止，原验收URL不再提供服务。
