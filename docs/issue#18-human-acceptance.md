# Issue #18 人工验收

候选：`debe97e46ec2adf3a6ccbc56fa58a1e3c6d2e06e`。独立验证通过：后端171、管理端35、真实隔离探针95项。以下全部是本机隔离合成数据，训练指标不代表实际风险准确率。

登录两边均用合成账号 `sandbox_admin`；口令见本机任务 `.scratch/issue18-afk-20261014/runtime/.sandcastle/environment/fixture.sql` 的合成employee条目，与现有原账号无关。聊天交接提供一次便于直接使用；报告不另保存认证token。

| 必需人工项 | 入口及操作 | 预期 |
| --- | --- | --- |
| 1 成功训练与回放 | [成功环境](http://127.0.0.1:18318/statistics)，滚动到**风控模型版本**（避免上方全局分析控制台），点击手动重训；选择新模型行查看回放 | 明确报告本次训练状态、新版本、181/59左右样本及文件/元数据保存；回放对应选择的版本。若评估拒绝，显示未通过激活评估，不能仍显示激活成功 |
| 2 样本不足 | [样本不足环境](http://127.0.0.1:18319/statistics)，同一风控区域点击手动重训 | 本次训练已跳过：正样本不足；1/1样本，文件/元数据均未保存，模型列表无新增；回放模型不存在 |
| 3 订单说明与回放异常 | [订单管理](http://127.0.0.1:18318/order)用下方完整订单号搜索，悬停风险等级查看说明；回统计页选择i18-empty/i18-missing-file/i18-invalid-metrics | 已有正常/异常评分保持解释；缺明细显示不完整的规则降级；回放分别显示无回放数据/模型文件不存在/回放数据无法读取。反馈、拒单权限与标签/事务已由95项真实API/SQL探针覆盖 |

合成样本（验收库独立复制，ID不代表原库7/316）：

| 样本 | ID | 查询订单号 |
| --- | --- | --- |
| 普通候选（已做反馈） | 1 | `i18-8a50b058-6021-4cb1-bab7-025cbfb837eb` |
| 异常合成样本 | 2 | `i18-f4200c84-916f-44a0-aced-16954d2c21f5` |
| 缺明细合成样本 | 241 | `i18-531c36ef-43a7-496f-8762-933d5cd45e16` |

自动业务证据覆盖真实模型训练保存加载、正负/候选标签区分、未登录和错误作用域拒绝、缺订单不写评分、反馈/拒单与故障事务、正常下单支付及归属。本票未改变小程序源码；未把官方工具/真机交互写成已通过，也不要求本次人工为源码未变的小程序重做全量验收。

验收已完成，本票全部8个容器已停止，卷和证据保留。原服务期限：2026-10-14 19:16:32（北京时间）或你反馈全部通过后停止，保留卷和证据。后台到期 watcher 已启动。提前停止可在项目根调用：

```powershell
wsl -d Ubuntu --exec python3 '/mnt/e/Learning Files/yuweiju-takeout/.scratch/issue18-afk-20261014/acceptance.py' stop
```

可直接回复：`debe97e：1通过，2通过，3通过；异常无。` 有问题记录步骤、提示和操作时间。全部通过后按已有授权提交必要交付文档、推送、PR、合并、同步和关闭；人工验收已收到候选debe97e：1/2/3全部通过、异常无；交付获批准。

截图：[训练成功](verification-evidence/2026-10-14/issue18/acceptance-trained.png)、[跳过](verification-evidence/2026-10-14/issue18/acceptance-skipped.png)、[订单页](verification-evidence/2026-10-14/issue18/acceptance-orders.png)。


用户反馈截图：[新训练成功](verification-evidence/2026-10-14/issue18/human-feedback-1.png)、[样本不足跳过](verification-evidence/2026-10-14/issue18/human-feedback-2.png)、[缺失数据说明](verification-evidence/2026-10-14/issue18/human-feedback-3.png)。
