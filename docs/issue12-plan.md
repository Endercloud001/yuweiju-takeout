# Issue #12 历史订单图片实施方案

## 范围与依据

遵循 [workflow](agents/workflow.md)、[后端规范](standards/backend.md)、[管理端规范](standards/admin.md)、[小程序规范](standards/miniapp.md)、[ADR 0001](adr/0001-retain-conventional-backend-layering.md)、[ADR 0002](adr/0002-preserve-historical-order-images.md) 和 [词汇表](../GLOSSARY.md)。任务挂载输入确认 #2/#6 已关闭，旧证据缺失由新合成隔离样本解决。本轮根目录与 Git root 均为 `/home/agent/workspace`；所有产物仅写此工作树，临时证据在 `.scratch/issue12/`。宿主两轮/共享60分钟限制及原截止时间不变。

实际代码：UserOrderController/AdminOrderController → OrdersApplicationServiceImpl → OrdersMapper/OrderDetailService/OrderDetailMapper.findByOrder → OrderAssembler。已有合并实现直接读取明细快照，不刷新商品图片、不写明细；保留后端代码，除非公开HTTP隔离验收发现真实不足。四个路径受 JwtAuthInterceptor 保护，用户详情还经 findOwned 校验归属；分别验证匿名拒绝、本人成功、他人详情拒绝。

## 受影响文件与三端效果

- 管理端 `src/views/order/index.vue` 的详情弹窗及 `detail.vue` 独立详情：既有菜名区域内增加32px缩略图，表格列、卡片、数量和价格列不变。复用小组件和已有中性缺图SVG。`src/types/order.ts`补足已有返回字段的可空image类型，不新增API字段。
- 小程序 `history-source/src/HistoryOrder.vue`、`Details.vue`、页面逻辑及小型共享展示逻辑：正常图原样显示；null/空/空白/加载错误显示同一本地PNG；失败状态按明细对象隔离，刷新/换行/换源恢复。由保存源构建两页JS/WXML/WXSS/JSON，图片框与其他布局保持。PNG复用管理端缺图SVG形状，文件受Git跟踪。同步history-source README与普通事件/渲染测试。
- 后端接口、schema、存储图片及金额均不变；增加 `.sandcastle/environment/Issue12Probe.java` 与 `issue12-check.sh` 验收探针。无当前商品图替换、原数据读写、历史恢复/回填、OSS删除或费用重算。

## 实施步骤

1. 保存本方案与进度；读实际源码、素材、隔离schema和Issue14探针。
2. 实现前端展示兜底，保持API/持久化对象不变；用实际Vue渲染与error事件覆盖缺图、历史A、失败、不循环、换源/换行与行隔离。
3. 探针从实际jar提取classpath；只用literal application-afk.yml、dev,afk profiles、随机localhost端口和内部mysql/redis。先核对DATABASE()与实际SHOW CREATE TABLE，再检查专用ID/触发器未被占用，构造菜品/套餐A vs B、null、空、空白、坏URL、缺商品与19元订单。建立仅保护本任务行的UPDATE/INSERT拒写触发器，四API后逐行SQL全字段/HEX(image)比对，验证认证、归属、分页/空页。finally删除任务触发器及行并核查共享夹具不变、关闭Spring上下文。
4. 执行以下检查批次，保留cwd/命令/退出与业务断言；审查范围diff，显式暂存任务文件并正常本地提交。每步检查 `/afk-evidence/closing.json`，原deadline前保存候选/剩余/阻碍，不push或发布。

## 可执行检查

从根目录分别运行（npm各命令在对应目录）：

```sh
mvn -B -f yuweiju-backend/pom.xml package
cd yuweiju-web-vue/yuweiju-admin && npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm run test && npm run build
cd yuweiju-weixin-miniapp/history-source && npm ci --no-audit --no-fund && npm run build && npm test
cd yuweiju-weixin-miniapp/checkout-source && npm ci --no-audit --no-fund && npm run build && npm test
python3 -c "from pathlib import Path; exec(Path('.sandcastle/environment/extract-classpath.py').read_text().replace('/workspace', str(Path.cwd())))" && bash .sandcastle/environment/issue12-check.sh /home/agent/workspace /home/agent/workspace/.sandcastle/environment
node --check yuweiju-weixin-miniapp/pages/historyOrder/historyOrder.js && node --check yuweiju-weixin-miniapp/pages/details/index.js
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . docs/issue12-plan.md yuweiju-weixin-miniapp/history-source/README.md
```

独立快照由宿主使用原 `/workspace` classpath命令及 `bash .sandcastle/environment/issue12-check.sh /workspace /workspace/.sandcastle/environment`。checkout测试用已有 `YUWEIJU_TEST_SCRATCH` 指向项目 `.scratch/issue12`；不改依赖、公共runtime/vendor/app或其他页面。

## 限制与回退

新合成样本只证明候选的公开行为，不能确定过去覆盖范围或恢复已覆盖图片。Node/Vue测试不证明官方微信编译/真实截图，宿主负责精确候选的官方编译、模拟器和浏览器前后证据、独立验证及人工接受。本地候选完成不表示issue正式验收或发布。兜底素材本地可加载；兜底自身error保持同一路径，避免重试循环。

回退使用经授权的普通revert撤销本票源码与生成产物、组件/素材/探针；无schema迁移与原数据回滚。保持已合并只读后端，退回旧写回实现仍有风险；回退无法撤销既往历史图片覆盖。

## 编码候选验证结果

本轮在 `/home/agent/workspace` 完成上述七组命令。后端package74项、管理端lint/typecheck/test11项/build、历史源build/test37项、结算源build/test14项、实际jar classpath与公开HTTP/MySQL探针、两页Node语法及文档链接全部通过，未跳过测试。历史源新增测试通过保存SFC和生成WXML真实旧事件分发器验证兜底及旧行事件隔离；管理端真实Element Plus详情弹窗/独立详情均保留历史A、菜名/数量/价格和19元。

真实隔离探针四接口与权限/归属/分页均通过；查询前后全部SQL行和HEX(image)相同，任务专用UPDATE/INSERT审计触发器证明零写入尝试（自身no-op UPDATE/INSERT校验后清空审计）。finally清理任务行/触发器/审计表并核对共享行不变，关闭Spring上下文。后端已有只读实现无生产diff。

检查重复记录为批次而非supervisor轮次；首批PATH/测试适配问题和真实迟到图片error回归均保留原日志，修正后受影响检查通过。完整命令、cwd、exit、具名业务断言与限制保留在项目 `.scratch/issue12/`，進度在 `.sandcastle/task-progress.md`。Vite既有大chunk提示不影响exit0，不扩大本票处理。候选仅本地提交，官方微信/浏览器前后截图、独立验证与人工接受仍待宿主；无发布动作。
