# Issue #17 分析执行与状态（同步）

消费者：管理端 `src/api/modules/analysis.ts` / `views/statistics/index.vue`；小程序仅经推荐 Service 读取分析产物，不消费本页管理 API。既有路径和 `code/success/message/msg/data`、已有 data 字段类型不变，schema 不变。请求需管理员 `token`，有效签名、未注销且 subject=admin；匿名、用户密钥或错误 subject 不允许分析操作。

`POST /admin/analysis/snapshot/run`、`training/run`、`prediction/run` 仍接受可选 ISO date。Service 同步执行，不是队列/后台触发。默认业务时区 Asia/Shanghai；非法日期、超界日期及回填 days 非1..365 友好拒绝。`POST /backfill` 是有副作用的历史快照回填，自动失败验证仅用隔离库；遇到失败停止而非称全部完成。

在旧 data 上新增 `executionMode: "SYNCHRONOUS"`、`executed: boolean`、`status`、`stages`。阶段含：

| 字段 | 语义 |
| --- | --- |
| stage | snapshot / clustering / clusterCache / heatTraining / validation / prediction / predictionCache |
| status | COMPLETE（实际执行）、SKIPPED、PARTIAL、FAILED |
| reason | 空或稳定原因代码，例 NO_USER_SNAPSHOTS / NO_DISH_SNAPSHOTS / INSUFFICIENT_SAMPLES / NO_MODEL_BASELINE / DEPENDENCY_FAILURE |
| windowStart/windowEnd | 对应快照历史、训练抽样或预测的日期窗口，均含该日；底层订单 SQL 仍半开区间 |
| outputs | 实际已提交的快照/分群/预测数量、新训练 train/eval 样本数、模型/特征版本、效果与原阈值 |

训练保留 `snapshotDate/userSnapshotCount/clusterCount/clusterModelVersion/heatModelTrained`。`executed=false` 仍 code0 且不预测，data 新增阶段原因供 UI；失败 code0，训练未执行仍 code0；快照/预测接受请求但缺数据时保留原 code1 并在阶段标记 SKIPPED。已有阶段成功但其他失败为 PARTIAL。code1 不保证实际执行或热度新训练，也不保证效果阈值通过。缺模型保留基线预测和候选兼容，明确 PARTIAL/NO_MODEL_BASELINE，版本 `baseline-{featureVersion}`；不临时训练或伪造 XGBoost 版本。加载损坏模型、推理坏输出或依赖失败如实失败，不替换旧 SQL。

`GET /heat`、`clusters` 排序/limit/字段保留；`metrics/offline` 在原字段上增加实际 modelVersion 与 evalWindowStart/evalWindowEnd。空指标仍空 map。某日缓存可来自此前训练，UI 必须展示源版本日期，不能归为本次效果；阈值配置未改。`metrics/online` 保留现有观察字段，线上观察不等于离线效果或当前执行成功。

回退：新增响应字段可被旧客户端忽略，旧客户端训练 code0 抛错仍成立；新管理端 ApiError 可读取 data。新模型目录保留已有元数据/legacy `dish-heat-xgboost.json` 读取方式；新写版本文件并原子发布元数据，代码回退时需要获授权选择旧 legacy 文件或保留当前版本。SQL事务不撤销文件与Redis，不能以回退代码宣称撤销产物。
