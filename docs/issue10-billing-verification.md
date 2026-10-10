# Issue10 计费验证记录

起点 e6c8af5（issue5 未接受候选），容器根 /home/agent/workspace。监督 iteration 1/2；累计60分钟截止 2026-10-10T13:13:36.007Z，宿主独立20分钟另计，无重启/扩预算/子代理。

[实施方案](../yuweiju-document/execplans/Issue10-trusted-order-charging.md) 在业务编辑前写入。挂载复现仅为历史隔离证据：36+2=38、checkout44；本轮不写原库、不更改974。旧2026-10-05原库材料全项目 hidden/no-ignore 文件名搜索无命中，未重跑原库写入。

初始实际 schema 只读 Java 检查因尚未安装 mysql JDBC 驱动失败（exit1，工作目录应用根），未发生数据库写入；package 完成后复用候选 jar 驱动核查。后续检查结果待填；这不代表已通过独立/官方/人工验收。

检查批次A（agent，非宿主独立）：应用根执行普通修复前 `mvn -B -f yuweiju-backend/pom.xml -Dtest=TrustedOrderChargingTest test`，exit1，明确 expected40.00 vs actual38.00（修复前预期失败），完整日志 .scratch/issue10/regression-red.log。JDBC 路径纠正为实际8.0.33后，只读 SHOW CREATE TABLE exit0；确认 decimal(10,2)、数量 NOT NULL、InnoDB、主键id、无真实外键，不打印连接凭据。原始 schema 证据 .scratch/issue10/schema-read.txt。

检查批次B：cwd=/home/agent/workspace/yuweiju-weixin-miniapp/checkout-source，精确 npm ci/cache + build + test 命令 exit0，13 tests passed、0 skipped；仅源码构建生成 checkout四文件，其他页面/runtime/vendor保持构建前字节。登录、地址/备注/餐具/失败/重试/防重复保留；新可信读价、价格变更再次确认、金额整数分、空/坏/失败cart阻止提交通过。此 synthetic Node harness 不证明官方微信编译或真实后端。

检查批次C：checkout同一精确命令重跑，cwd同上，exit0、13/13通过、0 skipped，日志 .scratch/issue10/checkout-batch2.log。原因是新增可信价未成功读取时隐藏缓存商品和金额的显示保护；不是新监督轮次。

生产/共享 mock-login=false 未修改。探针使用 @Primary WechatService 仅允许本轮唯一 mock code，返回两个独立 synthetic openid；不用 dev mock-login=true（实际该设置会让两次登录共用mock_openid_dev），避免误判双用户。地图 @Primary 固定7分钟，只接受合成地址；不调用真实微信/地图/OSS/模型。

检查批次D：应用根精确 Maven package exit1，74项中73通过、1 error、0 assertion failures/skip。新测试在已经 thenThrow 的 mock 上使用 when 再次设值，导致 Mockito 设值调用自身抛异常（未进入业务）。已改 doReturn 并保持有效 owned address，保留 package-batch1.log；第二完整 package 正在运行。进一步在 service 检查实际地址归属，以独立防御直接入口。后续 Java 临时目录通过 JAVA_TOOL_OPTIONS 放在项目 .scratch/issue10/java-tmp，原有临时测试自动清理；不改变测试范围。

检查批次E：应用根精确完整 Maven package（与D相同命令，添加项目内 JVM temp 环境变量），测试阶段74/74通过、0 skip；Boot package 于2026-10-10T12:36:10Z完成（总05:20），tool process确认exit0后才执行probe提取BOOT-INF/libs。日志 .scratch/issue10/package-batch2.log。

管理端独立检查组（agent）：cwd=/home/agent/workspace/yuweiju-web-vue/yuweiju-admin，精确 npm ci/cache、lint、typecheck、test、build 均 exit0。Vitest 2文件3测试通过；build保留既有超过500kB chunk警告，不修改拆包/构建配置。构建日志 .scratch/issue10-admin-build.log，其他步骤退出由本轮工具会话记录。无管理端源码diff。

检查批次F：checkout精确 ci/build/test 同目录 exit0，14/14、0 skip。补充实际 vendor 详情 handlePay 传递历史 amount19给支付store，未按新费用重算（synthetic974标签，不访问原库974）。日志 .scratch/issue10/checkout-batch3.log。

文档链接检查：应用根指定 python3 check-doc-links 命令 exit0，无缺链；最终报告填齐后再核对。历史材料单独只读搜索 `rg --files --hidden --no-ignore -g 'miniapp-checkout.json' -g 'miniapp-submit.json' -g 'created-order.json' .`，根=/home/agent/workspace、覆盖hidden/ignored目录，stdout/stderr空、exit1，确认为未挂载这些旧原库材料。挂载新的issue5隔离复现可用。

检查批次G（agent real）：应用根 `bash .sandcastle/environment/issue10-check.sh` exit0，2026-10-10T12:36:35Z–12:37:43Z，businessPassed=true、cleanupPassed=true。使用完成Boot jar的BOOT-INF/libs，无额外Maven类路径插件；真实MySQL+Redis+Spring代理+实际HTTP，地图及登录用探针替代。场景结果与完整清理断言保留 .scratch/issue10/scenario-results.json（后续批次会自动归档前次），摘要 .scratch/issue10/probe-batch1.log。

通过场景：匿名/管理员错误scope拒绝用户读/提交/历史/模拟支付，用户scope拒绝管理端列表/详情；正常双synthetic用户；客户端userId/amount/packAmount不掌握收费或身份；两份18=40、单份18=21、菜品/套餐各一=40、两菜三套餐=97；缓存负金额和目录变价后的可信读取/提交/明细分别18.37→21.37、19.99→22.99；空车/非正数量、缺/双/非法商品、停售/负目录价、缺/外人地址、金额溢出及总包装int溢出拒绝且无新订单；99999999.99精确上限成功、合法零目录价仍收费3。无法SQL落库的null数量/价格及超过2位舍入由普通测试覆盖，不改schema。

真实事务失败：分别创建唯一自有 orders/order_detail INSERT触发器；direct Spring proxy异常保留cause，实际HTTP返回业务拒绝，订单与明细无部分写入、cart不清除；移除触发器后重试21成功。历史合成19订单与历史图片在用户详情/历史/管理端列表/详情均不重算、不写回；新单mock payment不改变amount，外人支付/详情拒绝。全部原有fixture表与Redis值/key set最终相等，另一用户cart/address保留；自有SQL行、唯一触发器、Rediskey、模型/Tomcat/JVM运行目录finally清理。未flush、清表、重建ID或回退自增。

检查批次H：最后在assertOrder明确断言HTTP未知userId声明不影响订单owner后，重跑完整同一probe exit0；2026-10-10T12:39:29Z–12:40:31Z，businessPassed=true、cleanupPassed=true，无失败/跳过。日志 .scratch/issue10/probe-batch2.log，前次probe.log/scenario-results/check-result/owned-resources 自动归档 .scratch/issue10/history。没有增加监督轮次或预算。

## 候选范围与交接限制

仅issue10：后端新单可信计费及共享cart读价、具名订单/明细查询、移除历史详情读时图片写入；focused tests + 原风险测试的具名查询调用适配；checkout源代码/测试与真实build生成的order JS/WXML，必要details静态WXML文案；新真实probe/checkshell；方案与本报告。源码链 e6c8af5 保留，构建未修改其他12页/runtime/vendor/components/app。订单查询只使用stored amount；管理员既有渲染无需源码改动，但四项检查全部实际通过。

旧details是webpack产物而非本轮恢复的Vue源，仅WXML静态配送行由“￥6”变为“以订单合计为准”，未声称可重编译该页；样式类与stored packAmount/amount绑定保持。官方微信编译及外观截图仍须宿主验证。

本地任务commit的SHA在提交后写 .scratch/issue10/candidate.json 与 .sandcastle/task-progress.md，这些进度/缓存/认证/运行日志不stage。本报告未把宿主历史或未来检查计为agent通过。独立fresh snapshot + separate empty DB的同一精确命令，20分钟预算，仍由host运行；官方微信synthetic+REAL隔离HTTP金额/截图、issue5及issue10人工接受、后续发布/集成均未完成。未push/PR/merge/sync/close，不运行原库974写入。原库974实际行未读取，本轮旧fixture完整不变证明代码没有历史重算，而不声称重新验证原库974。

回退：普通反向任务提交恢复代码行为，不追改已存在订单；不得以Git回退冒充业务数据回滚。没有schema或费用字段迁移。Git guard/auth/model及max2/cumulative60min/independent20min均保持；未出现SANDCASTLE_GIT_BLOCKED，若宿主发布需继续按维护者授权与人工验收执行。

最终检查矩阵（均为本轮agent，而非独立/官方/人工）：

| 检查 | 实际cwd | 退出与结果 |
| --- | --- | --- |
| Maven完整package批次E | /home/agent/workspace | 0，74 tests，0 skip，Boot repackage完成 |
| bash issue10-check批次H | /home/agent/workspace | 0，businessPassed/cleanupPassed均true |
| Checkout精确ci/build/test批次F | /home/agent/workspace/yuweiju-weixin-miniapp/checkout-source | 0，14 tests，0 skip，可重建输出 |
| Admin精确ci/lint/typecheck/test/build | /home/agent/workspace/yuweiju-web-vue/yuweiju-admin | 0，四项通过、3 tests |
| Python3指定文档link检查 | /home/agent/workspace | 0，无缺失目标 |
| git diff --check、scope inspect | /home/agent/workspace | 0，task source/tests/docs/probe/generated checkout，进度不stage |

代码范围检查：没有auth/config/schema/vendor/admin源码改动，不包括缓存、auth、日志、临时产物或task progress。新探针和shell UTF8/LF通过。仅本地任务提交，根用户变更初始为空且保持；剩余进度文件为本任务有意保留的未提交证据。最终链接及Git范围在提交前再次实际检查，候选commit成功与SHA见项目内候选manifest。
