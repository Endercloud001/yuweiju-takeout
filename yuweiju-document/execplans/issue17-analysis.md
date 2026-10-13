# Issue #17 实施方案（2026-10-13，startCommit 71b548d）

授权仅本地隔离实现、检查及普通候选提交；不发布、不写原库、不启动人工服务。实际根目录 /home/agent/workspace，独立快照 /workspace；产物在各自 .scratch 内。截止 19:01:53.309Z，至多两次 supervisor iteration，检查批次不计为新增 iteration。

## 范围及兼容

后端 AdminAnalysisController、AnalysisTask → AnalysisApplicationService → 具名 Mapper；模型文件独立 IO、纯分群计算独立。保留路径、字段类型、业务 code、WeeklyTrainingResult 及 executed=false 不预测分支。新增阶段结果包含状态、原因、日期窗口、数量、真实版本；同步调用只报告返回时结果，不称队列或后台触发。缺模型的既有基线预测保留，但明确 PARTIAL/NO_MODEL、baseline 版本，绝不称新训练成功。阈值/算法/schema 不调整。

管理端唯一直接训练消费者 src/api/modules/analysis.ts → views/statistics/index.vue，目前只取 message；扩展可选明细、保留 token/VITE_API_BASE/code1。HTTP helper 对 code0 抛错，需要保留响应明细供 skipped/failed 展示。统计页展示阶段与离线阈值，旧查询/模型不代表本次效果。小程序通过推荐 Service 的候选 ID/Redis/聚类和热度结果读取，无调用管理训练接口；保留推荐字段、cache key/排序/TTL，做真实 SQL/Redis 回归，无小程序源恢复。API 文档为历史三端业务文档，当前 analysis API 以代码为事实；数据库设计的四张分析表保持复合主键与唯一约束。

## 实施步骤

1. 用 Service 同步用例统一入口结果及失败/部分成功处理；Controller/task 委派。每阶段捕获保留 cause 的领域失败，对外只友好原因；SQL事务经实际代理/TransactionTemplate，文件和缓存分别记录不可回滚副作用。
2. 将全部条件查询/删除/统计移入具名 Mapper，聚合 Join 使用参数绑定 XML；BaseMapper 标准 insert/selectById 保留。提取原有纯分群算法及模型 save/load/metadata IO，不优化算法或改阈值。关闭 Booster/DMatrix。
3. 增加前端结果与指标展示，普通 Service/Controller 测试、实际 Vue 模板测试覆盖 complete/skipped/partial/failed，code1 不表示效果通过。
4. 新增 .sandcastle/environment/Issue17Probe.java 和 issue17-check.sh。脚本 package 成功后提取 jar 依赖，绝不猜 JDBC。每次新 scratch/模型目录、新互不相交日期窗口；先核对 mysql:3306/sandcastle_fixture、redis:6379、DDL及 driver 后写合成数据。用真实 Spring/MyBatis/XGBoost/Smile/Redis/模型文件验证新训练、save/load/预测、统计与推荐兼容；隔离注入缺快照、无模型、无样本、依赖/写入失败、无权限及非法日期范围。复用 afk 配置替换个人配置、关闭任务及 dev 天气夹具，不运行风控训练或真实外部调用。
5. 检查批次：mvn compile → mvn -B package（全部测试）；npm lint/typecheck/test/build；bash .sandcastle/environment/issue17-check.sh ROOT ENV。日志记录命令/cwd/exit/脱敏业务结果，失败证据保留。通过后读挂载 Ponytail 对完整 startCommit diff 收尾，显式停止，实质改动重验，再显式 stage 与普通 commit。

## 回退与验收

代码回退仅撤销本票提交，不代表 SQL/Redis/文件回滚。所有自动数据在隔离 fixture 的新窗口和任务目录，清理只限本次 ID/日期/key；模型证据保留。不得重建库、flush Redis、覆盖历史模型。成功需新模型文件/版本/样本数、SQL快照/分群/预测数量一致；合成指标只证明机器链路，不证明业务效果，阈值失败如实显示。独立验证、Windows/浏览器人工验收、人工确认 ALL passed 与后续交付全部待维护者完成。
