# Issue #6：历史订单最小源码恢复方案

## 授权与环境

2026-10-11 本轮授权仅恢复 `pages/historyOrder/historyOrder`、`pages/details/index` 及必要局部适配；#2 已关闭，评论为空。起点 `b303dab` 已合并 checkout-source。宿主项目根为 `E:/Learning Files/yuweiju-takeout`，容器根实际 `pwd` 与 Git root 均为 `/home/agent/workspace`；`.git` 指向宿主项目 `.scratch/issue6-afk-20261011/runtime/.git/worktrees/...`，这是 issue6 项目内 runtime，不是只读 `/home/agent/task-input`。首轮截止为 2026-10-11T05:53:40.876Z。首轮独立验证失败后，维护者明确批准最终第二轮，起点 `d030e1d`；本轮宿主截止 2026-10-11T08:58:33.779Z，剩余编码2863054ms及独立验证900000ms，沿用原累计预算、不新增第三轮。不启动新 supervisor或子代理。

遵循 [workflow](../../docs/agents/workflow.md)、[小程序规范](../../docs/standards/miniapp.md) 与 [ADR 0002](../../docs/adr/0002-preserve-historical-order-images.md)。[保存规格](../../docs/yuweiju-restore-spec.md) A3 明确历史图保持、缺失仅展示兜底；本轮授权将图片票确认为 #11，本票不实现该票，也不将规格用户故事序号11当 Issue 编号。#10 已修改详情配送费为“以订单合计为准”，必须保留。

## 调查结果与范围

- 历史页 webpack 124/125/126 为归一化/模板、128 为业务脚本，129/130 为抽出的 CSS；列表直接绑定明细 image、总额 amount。旧刷新会追加，旧失败路径未结束加载；页数先增后请求导致失败后跳页。
- 详情页 webpack 47/48/49 为归一化/模板，业务50/51在 `common/vendor.js`。它复用 store `orderListData`，倒计时 `times` 缺卸载清理；拒绝/空结果可能残留旧详情。
- 保留现有两页 WXML 布局、WXSS、scopeId `5cf07246`/`54d3589c`、组件及静态资源；恢复 Vue 模板表达式与事件。费用来自持久化 amount/packAmount，不补算配送或打包，不换图。
- `checkout-source/build.cjs` 已用 uni-template-compiler 2.0.0-22420190823021、Vue编译器2.6.10、strip-with1.9.1、esbuild0.25.12；本票同版本锁文件，复用既有 loader 的11 normalizer、1 uni、12 store、24 API、29工具模块。checkout源码/编译器不改。

## 实施步骤与三端影响

1. 新增独立 `history-source/src/HistoryOrder.vue`、`Details.vue`、局部 history/details 业务与 legacy bridge，独立 build.cjs 与锁文件，仅写两页各 js/json/wxml/wxss。
2. 修正局部分页成功后更新页码、重复加载保护、tab/刷新回第一页、onShow状态刷新、业务/网络错误提示及加载释放；请求版本与卸载标志阻止迟到响应覆盖当前页面。
3. 详情仅用有效 orderId 请求；失败/空/越权清空本页与对应旧store明细、结束倒计时，页面销毁/卸载清理计时器。保留支付 store 与路由、取消/催单弹层、再来一单先清车、商家电话及跨页返回。
4. 编写运行真实生成页面、旧 Vue/store/API/request 的普通合成测试；只替代微信IO。验证源编辑改变真实输出、重编译确定性，普通字节比较其他11页及common/vendor/components/app未变。不新增hash/contract/baseline/gate。

小程序调用保持 GET `/user/order/historyOrders`、`/user/order/orderDetail/{id}`，取消PUT、催单GET、再来一单POST及原authentication头。后端与管理端无改动，因为公开接口/schema与履约规则不变；管理端更改状态后用户页面onShow或刷新取原接口最新状态。归属由原后端检查，合成拒绝响应只证明页面正确处理，不能替代后端归属验证。

## 可执行验收（仓库根）

```sh
cd yuweiju-weixin-miniapp/history-source && npm ci --no-audit --no-fund && npm run build && npm test
cd yuweiju-weixin-miniapp/checkout-source && npm ci --no-audit --no-fund && npm run build && npm test
node --check yuweiju-weixin-miniapp/pages/historyOrder/historyOrder.js && node --check yuweiju-weixin-miniapp/pages/details/index.js
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . yuweiju-weixin-miniapp/history-source/README.md yuweiju-document/execplans/Issue6-history-source-recovery.md
```

前两行各自从仓库根启动。记录检查批次的目录、退出码、断言和限制至 `.sandcastle/task-progress.md`，日志/临时输出 `.scratch/issue6`。checkout既有测试自带issue5 scratch路径；只为该测试增加可选 `YUWEIJU_TEST_SCRATCH` 环境变量（默认不变），本票从根export到 `.scratch/issue6`。这是遵守本票输出边界所需的测试适配，不改checkout业务源码/编译器/产物。官方微信IDE已由宿主安装登录；宿主须对精确候选重新编译并保存正常列表、详情及图片截图，检查滑动tab/分页/返回/支付/取消/催单/再来一单/电话。Node通过不代表官方编译或视觉验收。

## 限制与回退

历史JSON/截图已清理（挂载material-status与全项目含隐藏忽略目录文件名检索无命中），不重跑原库driver。本票只用合成订单974 amount19，不声称原974核验、图片下载解码或真实后端归属/交易验证。缺图兜底及历史图片语义修复归#11；既有图片URL原样展示。无真实服务调用、SQL/Redis/外部业务写入。官方编译、视觉、独立验收与用户接受待宿主完成。

回退由维护者对本票提交普通revert恢复两页产物并删除独立源目录，无schema迁移；不能撤销已有历史图片覆盖。只显式暂存任务文件，普通本地commit，不push/PR/merge。若Git guard拒绝，保留工作树与拒绝分类/原因/下一步，不绕过。

## 首轮候选检查及失败

首轮容器检查批次4全部通过（并未证明页面全部条件正确）：history-source `npm ci/build/test` exit0，23测试无失败/跳过；checkout-source `npm ci/build/test` exit0，14测试无失败/跳过；两页 `node --check` 和上述文档链接命令exit0。源编辑改变真实输出，两次构建确定性与其他11页/共享文件字节比较通过；额外对起点Git文件比较确认两页WXSS、checkout产物、代表共享文件原样，依赖锁中所有非根包条目与checkout一致。仅checkout测试临时输出环境变量适配有一行差异。记录在项目 `.scratch/issue6/check-results.json` 与进度；正式微信编译/截图、精确候选独立验证及用户接受仍待宿主，不以容器检查代替。


## 最终第二轮方案与结果

维护者在2026-10-11明确“微信开发者工具已就绪，批准继续第二轮”。保留首轮证据与候选历史；只修漏掉的同票回归、普通测试、构建与文档。正式发布仍等待全部人工验收。

宿主对精确首轮候选独立检查exit1：历史模板给 `statusWord` 传时间字符串，旧工具需要剩余毫秒，待付和过期订单行标签undefined；支付条件取反，未过期按钮缺失。首版宿主整页文本含tab标签不足以证明行文案，细化失败证据以 `/home/agent/task-input/refined-pending-check.sh/log` 和首轮报告为准。首轮37项通过的自动测试遗漏这两个条件，不能将首轮完成标记当业务通过。

本轮先加入真实SFC+旧运行时行为测试并记录红测，再在HistoryOrder.vue恢复 `statusWord(item.status,getOvertime(item.orderTime))` 与 `item.status===1&&getOvertime(item.orderTime)>0`。工具签名、API、状态、时间口径、金额、图片和跨页store不变。新增测试按实际订单行类定位，覆盖两种pending文案及支付可见性、详情/支付路由和参数、详情超时取消、取消条件优先级、催单、onShow刷新、明细展开和持久化19元/图。测试使用普通VNode观察，不断言私有生成变量，不新增快照/hash/gate。

对照 `b303dab` 两页WXML/render和旧vendor模块29/51核对条件与事件：Details的 `!timeout&&status===1||status===2||status===3||status===4` 保留原优先级；handleCancel传center和完整订单，handlePay/reminder传id；图片和金额原样绑定；先清车再来一单、返回路由及支付store语义保留。首轮loaded失败防护、计时清理和onShow刷新均保留；旧paymentTime未定义所以留空，无需补造字段。没有另外发现直接有证据的转写缺陷。

红测批次1中两个详情断言因SFC格式空白失败，修正断言空白后批次2为5/8通过，剩余3项就是已知业务回归；修复后新增onShow渲染检查，批次3为9/9通过。首次本轮进度追加误在npm工程cwd执行路径断言失败，未写任何文件；回到项目根修正。早期失败日志保留在 `.scratch/issue6`，未删改首轮宿主证据。

本轮规定四组检查批次4全部exit0：history-source `npm ci --no-audit --no-fund && npm run build && npm test`，32/32通过；checkout-source同命令，14/14通过；两页 `node --check`；README/ExecPlan链接检查。均无skip/cancel。checkout首次未设scratch变量，普通测试产物保留在项目 `.scratch/issue5`；补跑批次5仅 `YUWEIJU_TEST_SCRATCH=/home/agent/workspace/.scratch/issue6 npm test`，14/14通过，后续夹具归本票目录。日志及目录/退出码见 `.scratch/issue6` 和 `.sandcastle/task-progress.md`。两页由源码重建，其他页与共享vendor无变更。官方IDE编译/实际截图、真实隔离后端归属及宿主最终精确提交独立验证仍由宿主执行，本容器测试不替代它们。

最终范围检查以 `git diff --name-only d030e1d` 核对：只有HistoryOrder.vue、其生成JS/WXML、本测试与README/本方案需要暂存；Details八文件重建中其四文件字节不变，其他页面、vendor、checkout产物、后端和管理端无差异。没有依赖新增或锁文件变更。候选采用普通本地任务提交；进度、依赖和临时证据不暂存。
