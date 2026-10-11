# Issue #11 验证记录

依据 [实施方案](issue11-plan.md)、[后端规范](standards/backend.md)、[管理端规范](standards/admin.md)、[小程序规范](standards/miniapp.md)。本轮仅新隔离合成订单，历史969/974及六份缺失材料未经本次验证；不得以这些历史结果代替本轮证据。

## 环境和命令

应用根 `/home/agent/workspace`，Git root一致，起点86e866a；非login bash确认 Java `/opt/java/openjdk/bin/java`、Maven `/usr/share/maven/bin/mvn`。读输入与 prior evidence 后，先保存 plan。数据库仅 mysql:3306/sandcastle_fixture，Redis仅 redis:6379，使用替代个人配置的 application-afk.yml、dev,afk、随机127.0.0.1 HTTP端口；评分/训练禁用，未访问真实地图/AI/OSS/微信。probe真实检查DATABASE与InnoDB/空闲任务ID后才写。合成订单911001、911002、911010、911080、911081，用户911100/911101，任务明细911021/22、原购物车911031/32。自动产生的新购物车也只清理这两个已确认空闲owner，不碰其他样本。

日志位于项目 `.scratch/issue11/batchN`；每组保留 cwd、命令、exit与业务断言。首轮构建日志未保留，恢复提交2c6c8fb仅为未经验证草稿。本次续跑从14:48UTC开始重新运行检查，最终 Maven、管理员全检查、小程序消费者回归及真实SQL/HTTP探针均通过。当前截止15:37:46.933UTC，第二且最后编码轮，未扩展预算。完整命令/退出/日志索引为 `.scratch/issue11/checks.md`。

```sh
# cwd: application root, non-login shell
mvn -B -f yuweiju-backend/pom.xml package
# cwd: yuweiju-web-vue/yuweiju-admin
npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm run test && npm run build
# cwd: application root; coding equivalent of host /workspace extraction
python3 -c "from pathlib import Path; exec(Path('.sandcastle/environment/extract-classpath.py').read_text().replace('/workspace', str(Path.cwd())))" && bash .sandcastle/environment/issue11-check.sh /home/agent/workspace /home/agent/workspace/.sandcastle/environment
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . docs/issue11-plan.md docs/issue11-verification.md
```

Host从fresh exact-candidate snapshot执行原/workspace命令，不使用coding根路径；script接收root/environment两个参数，LF。classpath取自实际packaged jar，未改依赖。

## 业务矩阵

下列矩阵全部在最终检查批次3探针通过：实际366条命名ASSERT PASS，包含清理和Spring关闭，最后打印ISSUE11 ACCEPTANCE PASS assertions=366。完整日志 `.scratch/issue11/batch3/probe.log`；HTTP200/driver exit0未代替业务断言。普通 `OrderFulfillmentRulesTest` 验证usable ID、reason、markPaid bypass/不一致pay、可选评分降级、行数冲突、归属与限流前检查；SQL事务证据来自真正Spring代理与MyBatis写入时的transaction active记录。

| 组 | 合法状态与断言 | 拒绝/重复/失败断言 |
| --- | --- | --- |
| paymentMock | 1→2，PAID、checkoutTime；mock既有字段 | 2/3/4/5已支付重试成功但完整行不变，6拒绝；缺单、他人、匿名；核心SQL故障完整行不变 |
| markPaid复用 | 自校验1→2；已支付2/3/4/5无写重试 | 缺单、null/0/负ID、6及未支付3拒绝；真实代理SQL故障回滚 |
| confirm | 2→3仅status变化 | 1/3/4/5/6、缺单、重复拒绝；匿名/user-token管理员拒绝；SQL故障 |
| reject | 2→6，trim拒绝原因/取消时间，pay不变 | 1/3/4/5/6、缺单、重复、空白reason拒绝；权限与SQL故障 |
| cancelByUser | 1/2→6，用户原因/时间，pay不变 | 3/4/5/6、缺单、重复、他人、匿名拒绝；SQL故障 |
| cancelByAdmin | 1/2/3→6，trim原因/时间，pay不变 | 4/5/6、缺单、重复、空白reason拒绝；权限与SQL故障 |
| deliver | 3→4仅status变化 | 1/2/4/5/6、缺单、重复、权限与SQL故障 |
| complete | 4→5及deliveryTime | 1/2/3/5/6、缺单、重复、权限与SQL故障 |
| 完整HTTP链 | payment→confirm→deliver→complete，两端详情与SQL status/pay/time一致（用户上海字符串、管理员默认日期格式保持，归一化到同一时刻后与SQL比较），19金额/7.5明细金额/存储图片A保持 | 每步后payment retry不改后续状态/时间/金额/图片；details全部行保持 |
| 并发冲突 | confirm vs reject同读2；payment vs userCancel同读1，真实MyBatis屏障 | 恰好一个HTTP业务成功，持久化winner且后续付款重试不回退 |
| reminder复用 | 仅自己的2，Redis60秒限流 | 1/3/4/5/6、缺单/他人/匿名及复用越权拒绝，无SQL写；重复限流 |
| repetition复用 | 任何自己的已有状态，历史字段复制与清空自己的cart；重复不累加，空明细语义保留 | 缺单/他人/匿名不写；真实代理HTTP第二insert trigger故障，AFTER DELETE+first INSERT证据，旧cart完整恢复/无部分新cart/他人cart不变 |
| admin statistics | 三种status数量与实际SQL count一致 | 命名Mapper countByStatus复用，无其他读取变更 |
| 风险hook | OrderRiskTask训练委派/scoreOrder现有disabled分支，无风险/订单写 | 普通测试验证可选评分抛异常不回滚核心付款，未改算法/未声称隔离禁用时完成训练 |
| WebSocket | onMessage真实现有hook无法变更订单 | 只通知传输；当前payment/reminder没有发送hook，不声称已收到通知 |
| cleanup | 触发器/证据表/任务行/限流key清理，全共享表逐行相等，Spring关闭 | finally运行，即失败也清理本任务资源，cleanup失败不打印ACCEPTANCE PASS |

## 任务、通知与消费者审计

全仓搜索确认原始 markPaid 仅 paymentMock内部调用；最终两公开入口各自带事务并使用共享私有支付步骤，生产代码无其他markPaid调用者，所有其他履约入口为两HTTP控制器；重复下单通过真正Service代理事务。现有AI只提供催单/取消文字指引。当前无未付款超时/配送自动完成任务，两个任务组为 absent/not applicable，未实现也未宣称scheduler passed。小程序仍显示旧15分钟超时说明，界面文案不能证明服务端任务存在。

支付仅调用可选scoreOrder；其真实实现开关禁用时返回、启用时catch评分失败并记录日志。本轮继续可选降级，不改风险算法。当前payment/reminder不调用WebSocket，WebSocket onMessage为空且广播只sendText并吞传输异常；SQL回滚不能撤回已发通知或Redis。原代码无实际通知hook，因此不新增测试虚构通知接收。reminder新增归属/状态Service检查，与history/details只在状态2显示按钮一致。

小程序两个页面原有再来一单先发送独立DELETE购物车请求再POST repetition的顺序保留。已验证的回滚边界是单次POST/Service事务中的clear与insert；SQL回滚不撤销该客户端更早的独立DELETE请求，也不声称跨HTTP原子性。

管理端 `src/api/modules/order.ts` 与订单页面继续发送既有id/reason和token，code===1才认为成功；小程序 `history-source/src/details.js`、`HistoryOrder.vue`、`Details.vue` 与现有common API继续payment/cancel/reminder/repetition、authentication和mock字段。协议/schema/字段/code及amount/images/risk读取保持，前端无需源码变化。管理员指定npm全检查照常；原生微信编译/真机、浏览器截图及human acceptance留给host，不伪造。

## 当前结论与限制

编码必需检查已全部通过；最终候选为本轮普通task commit的HEAD（准确SHA与状态在 `.scratch/issue11/candidate.json`，进度在 `.sandcastle/task-progress.md`）。候选包含恢复草稿的履约实现、经验证的统计编译修正与探针修正；恢复WIP提交本身不标为验收完成。

| 检查批次 | 工作目录 | 退出与实际结果 | 完整日志 |
| --- | --- | --- | --- |
| Maven 1 | 应用根 | exit1，草稿Long→int编译错误，失败保留；修正为intValue保持整数字段 | `.scratch/issue11/batch1/maven.log` |
| Maven 2 | 应用根 | exit0，exact package BUILD SUCCESS，85/85，0失败/错误/跳过，5:02，15:00:08UTC完成 | `.scratch/issue11/batch2/maven.log` |
| 管理端 1 | 管理端实际工程 | exact npm ci/lint/typecheck/test/build exit0，340包、11/11测试，Vite1m8s；原有chunk warning | `.scratch/issue11/batch1/admin.log` |
| 小程序消费者 1 | 应用根 | node --test history-source/tests/pages.test.cjs checkout-source/tests/checkout.test.cjs exit0，31/31，0跳过；实际旧请求/认证/页面行为 | `.scratch/issue11/batch1/miniapp-consumers.log` |
| 探针 1 | 应用根→backend | exit1，182断言及finally清理通过；草稿把两端不同日期JSON格式直接比较 | `.scratch/issue11/batch1/probe.log` |
| 探针 2 | 同上 | exit1，182断言及清理通过；JDBC DATETIME为LocalDateTime，修正草稿Date强转；未改VO/API格式 | `.scratch/issue11/batch2/probe.log` |
| 探针 3 | 同上 | exact coding extraction+script exit0，138实际jar库；366断言、全矩阵、事务/并发、finally清理和Spring关闭通过 | `.scratch/issue11/batch3/probe.log` |
| 文档链接/范围 1、最终 | 应用根 | 两文档链接exit0；LF/bash -n/git diff --check通过，最终范围审查不含前端/依赖/schema/认证/风险算法变更 | `.scratch/issue11/batch1/doc-links.log`、`.scratch/issue11/final-review.log` |

三次探针均先确认DATABASE=sandcastle_fixture与空闲task IDs，再写新合成样本；失败批次未打印ACCEPTANCE PASS，清理断言验证共享数据未变。普通11项履约测试只证明规则；真实代理/SQL事务来自最终探针，既有含IntegrationTest名称的Mockito不作事务证明。自动增量序列不在SQL回滚/样本清理还原范围。

独立host20分钟及human acceptance未开始；原生微信编译/浏览器/人工验收仍待host，历史969/974未验证。发布未经授权，本地候选只表示等待独立/人工验收。Git safety未修改，未发生SANDCASTLE_GIT_BLOCKED，不push/PR/merge/close；deadline/轮数保持本次设定。
