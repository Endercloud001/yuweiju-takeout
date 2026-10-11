# Issue #6：历史订单最小源码恢复方案

## 授权与环境

2026-10-11 本轮授权仅恢复 `pages/historyOrder/historyOrder`、`pages/details/index` 及必要局部适配；#2 已关闭，评论为空。起点 `b303dab` 已合并 checkout-source。宿主项目根为 `E:/Learning Files/yuweiju-takeout`，容器根实际 `pwd` 与 Git root 均为 `/home/agent/workspace`；`.git` 指向宿主项目 `.scratch/issue6-afk-20261011/runtime/.git/worktrees/...`，这是 issue6 项目内 runtime，不是只读 `/home/agent/task-input`。不启动新 supervisor，不改两轮/累计60分钟及截止 2026-10-11T05:53:40.876Z；独立验证20分钟归宿主。

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

## 本轮候选结果

容器检查批次4全部通过：history-source `npm ci/build/test` exit0，23测试无失败/跳过；checkout-source `npm ci/build/test` exit0，14测试无失败/跳过；两页 `node --check` 和上述文档链接命令exit0。源编辑改变真实输出，两次构建确定性与其他11页/共享文件字节比较通过；额外对起点Git文件比较确认两页WXSS、checkout产物、代表共享文件原样，依赖锁中所有非根包条目与checkout一致。仅checkout测试临时输出环境变量适配有一行差异。记录在项目 `.scratch/issue6/check-results.json` 与进度；正式微信编译/截图、精确候选独立验证及用户接受仍待宿主，不以容器检查代替。
