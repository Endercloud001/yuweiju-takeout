# Issue #17 执行报告

状态：实现与代理验证进行中；独立复核、人工验收、交付均待完成。起点 `71b548d`，本轮 supervisor iteration 1/2，原截止 `2026-10-13T19:01:53.309Z`；没有增加时间、迭代或发布授权。没有调用原库、真实外部服务、风控训练，也没有启动宿主人工作业服务。

## 实现与兼容

Controller/task 委派 AnalysisApplicationService 同步用例。新增 `executionMode/status/executed/stages`，阶段含 `stage/status/reason/windowStart/windowEnd/outputs`；保留原 public URL、ApiResult 字段/code、snapshotDate/windowStart/userSnapshotCount/clusterCount/clusterModelVersion/heatModelTrained。`executed=false` 继续 code0，并不进入预测。COMPLETE 仅说明阶段执行，不说明效果通过。缺用户/菜品快照、无模型基线、无观测样本、依赖失败及部分提交产物分别可见；代码0也带阶段明细，管理端 ApiError 保存响应。

快照两表、分群和预测的目标窗口替换各自用 TransactionTemplate 真实 SQL 事务，缓存写在提交后独立报告，SQL 回滚不等于缓存或文件回退。全部条件/统计走具名 Mapper；RFM/时段/价格/品类与菜品销量/衰减、候选统计用参数 XML。原分群计算及热度特征/标签/权重/基线方程提取为纯计算；HeatModelStore 管文件、元数据、真实版本及资源。新训练保存独立版本文件，元数据在完整文件后原子发布，旧模型保存失败时仍可读取。Booster/DMatrix 都关闭；阶段内部保留异常 cause，JSON 不序列化 cause。分析 Controller 参数/依赖错误不返回技术堆栈；本票路由增加管理员 subject 校验，签名及黑名单措施保留。

管理端 statistics 显示执行状态/窗口/数量/版本/原因/本次新训练与 RMSE、MAE、轮廓系数及阈值。旧指标显示其来源版本和日期，并提示不代表本次成功；阈值失败保持可见。只增加既有页面内容、未改视觉系统。管理端直接消费者 `src/api/modules/analysis.ts`，小程序读取推荐 ID/缓存/分析结果，不消费训练响应，无小程序源码改动或恢复。推荐返回字段、排序、cache key/TTL 和当前用户接口保持兼容。数据库/schema 未改，复合键按实际 fixture DDL 检查。

## 检查与证据

检查按批次记录在 `.sandcastle/task-progress.md` 与 `.scratch/issue17/`。首轮 compile 因 POM release17 不支持 List.getFirst/getLast 失败；第二批 TS 因 erasableSyntaxOnly 不支持参数属性失败；均保留日志并在同一授权迭代修正。后续 compile、管理端 lint/typecheck/test/build 有通过记录；完整 package 已通过161项测试；更新响应兼容后的探针脚本 package 通过162项。探针批次6已真实新训练/保存/重载/预测/数量/版本/推荐/第一项SQL回滚，但静态测试Jackson未注册JavaTime模块导致后续安全序列化断言失败（非业务模型失败），全批次未通过。失败保留 `.scratch/issue17/run-Cb1q3vAd`，target=2040-06-29；修正后批次7用全新日期、Redis DB与模型目录重跑，最终结果待填。历史原始验证日志不存在，不重建或写原库补证。

独立入口（先在该快照完成构建；脚本也会核实成功 package 后提取实际 jar 依赖）：

```sh
mvn -B -f yuweiju-backend/pom.xml compile
mvn -B -f yuweiju-backend/pom.xml package
cd yuweiju-web-vue/yuweiju-admin
npm ci --no-audit --no-fund
npm run lint
npm run typecheck
npm run test
npm run build
# 回到快照根目录，ROOT/ENV 使用该快照绝对路径：
bash .sandcastle/environment/issue17-check.sh /workspace /workspace/.sandcastle/environment
```

探针每次创建独立 `.scratch/issue17/run-*`、模型/缓存目录，选择新的空内部 Redis DB（保留 run 标识，不 flush）；从已保留 SQL 日期之后推进到互不相交新训练窗口，先校验 `mysql:3306/sandcastle_fixture`、fixture 及 driver/DDL，再写120日合成订单。通过后保留合成 SQL、模型与 JSON/counts/version 证据；失败仅修改自身日期的临时触发器，并 finally 移除，不停宿主 MySQL/Redis。真实 Spring Service/Mapper proxy、SQL聚合/参数绑定、事务失败回滚、XGBoost train/save/load/predict、Smile native、Redis正常/故障及推荐兼容都由该 probe 验证。合成效果指标只能证明链路，不能证明当前业务有效性。

## 人工验收与回退

待宿主独立验证精确候选提交通过后，由宿主启动已授权的本地隔离人工服务（代理不启动），记录端口、PID/容器、候选及24小时到期。管理员正常登录 statistics，选择本次新窗口，核对训练返回每阶段与 SQL、文件/元数据版本、预测条数、指标源版本；执行完成但阈值未过应明确可见。核对缺快照/缺模型/部分失败/非法日期与权限拒绝的友好展示，并在小程序/开发者工具核对正常推荐候选兼容。这些自动探针或模板测试不代表人工浏览器/开发者工具验收已通过。

人类确认 ALL 人工验收通过前，不 push、PR、merge、同步、关闭 issue 或发布。代码回退只撤销本票提交；SQL/Redis/模型文件副作用需对应授权和具名保留/恢复方案，不能以代码回退或 SQL 回滚宣称全部撤销。证据与合成数据留在隔离资源，不清表、不重建库。

## Ponytail

待初始完整检查通过后，对起点以来全部 task diff/调用方/配置/测试 full closeout，记录采纳/拒绝原因，显式 stop ponytail，再保存最终普通 task commit。尚未完成。
