# Issue #5：结算页可编辑源码与隔离重建

## 授权、起点与范围

2026-10-10，草稿在 `codex/afk-issue-5-20261010` 开发，前次 supervisor 因 Codex session-record thread-not-found 失败（stopped=true，无任务提交/完成信号）。经明确恢复授权，在 `/home/agent/workspace` 的 `codex/afk-issue-5-20261010-resume1` 恢复该草稿；起点 `612c637` 已含 #8 登录与 #9 购物车/地址准备。Windows 原工作目录有用户修改且陈旧，本任务不写入它。#2 CLOSED；issue 评论为空。原授权最多两轮、总编码 60 分钟，前次已耗 858021ms；本次只使用剩余额度，宿主给定共享截止 2026-10-10T11:19:42.143Z，不重启 supervisor、不增加时间/迭代、不创建子代理、不改变模型、认证设置或 Git guard。仅结算源码、局部构建入口、测试、文档与四个结算输出；不恢复其他十二页，不改 vendor/runtime、收费、认证、后端、schema 或管理端。

依据 [小程序规范](../../docs/standards/miniapp.md)、[workflow](../../docs/agents/workflow.md)、[术语](../../GLOSSARY.md)、[ADR0001](../../docs/adr/0001-retain-conventional-backend-layering.md)、[ADR0002](../../docs/adr/0002-preserve-historical-order-images.md)。历史截图已清理，不以旧报告代替本次验收。

## 真实依赖与技术选择

现有 `pages/order/index.js` 的入口 34、模板 37 和样式 44 依赖 `common/runtime`/`common/vendor`；业务脚本 39 实际在 vendor。它使用 store 12、Vuex 13、API 24、request 25、env 26、时间/标签工具 29；Vue 4、uni 微信 adapter 1 与 normalizer 11 支持页面注册。`common/main.js` 已设置 `wx.__webpack_require_UNI_MP_PLUGIN__`。复用该入口取得同一 store/API/uni，避免独立 Vuex 产生不同购物车/地址/备注，避免新编译器覆盖旧全局运行时。

地址页 `choseAddress` 提交 setAddress 后 redirect 回结算；备注页提交 setRemark 并 redirect；首页 initdishListMut 写购物车；支付页读取 setOrderData，调用现有 paymentOrder 后直接进入 success，真实 requestPayment 仍注释。API 路径分别为 `/user/addressBook/list`、`/user/addressBook/default`、`/user/order/estimatedDeliveryTime`、`/user/order/submit`；request 使用 state.token 的 authentication、JSON header，code 1/200 resolve，其他 code reject。复用这些模块保留 #8 存储 token/正常登录和 #9 服务端准备行为，不新增接口。

选择 uni-app/Vue 2 `.vue`，不采用原生 Page：近原产物年代的 `@dcloudio/uni-template-compiler` 能独立编译，生成 __e/__l、$mp.data、vue-id/ref/slot 与原 adapter 一致。只调用模板编译器和旧 createPage/normalizer；不运行完整 uni CLI、不生成 App 或 vendor。组件懒加载继续使用旧 webpack chunk 和 module 编号（navbar 145、list 152、list-item 173、popup 166、pikers 180），现有组件 JSON/WXML/JS/WXSS 不动。这些是当前产物的互操作依赖，不是假称完整源工程已恢复。

工具固定：Node v22.23.3，npm 10.9.9；`@dcloudio/uni-template-compiler` 2.0.0-22420190823021、`vue-template-compiler` 2.6.10、`vue-template-es2015-compiler` 1.9.1、esbuild 0.25.12。package-lock 固定间接依赖。旧 stylesheet 恢复为 .vue 的可编辑 CSS，保留原 scope 标记 data-v-0ca91b30（沿用已有标记，不新增 hash/baseline/gate）；模板编译使用同一 scopeId。

## 编译探针与命令绑定

以下探针来自恢复进度的历史记录，本次按授权不重复已完成调查；部分原探针文件未保留，不作为 fresh 验证。真实最小输入（文本金额运算 + uni-popup/ref/tap）通过编译器生成 WXML/render，无 errors，exit 0，日志 `.scratch/issue5/probe/minimal.log`。安装 exit 0（install.log）。最初版本查询误用不存在版本，exit 1 E404，随后从真实 published versions 修正；这是查询失败，不声称编译成功。探针 Node cwd 根目录：`node .scratch/issue5/probe/minimal.cjs`。接下来验证 ES2015 render 转换与 esbuild 包装，全部实际日志保存在 `.scratch/issue5/`。

已恢复实现入口 `yuweiju-weixin-miniapp/checkout-source/`，在该工作目录执行 `npm ci --cache ../../.scratch/issue5/npm-cache --no-audit --no-fund`、`npm run build`、`npm test`。本次已实际完整执行，结果见下文。构建只写 `../pages/order/index.{js,wxml,wxss,json}`；可指定临时输出目录验证源码编辑影响与两次构建，绝不写其他页面。node --check 仅语法检查，不称编译。

## 实施与三端影响

1. 从模块 39 和 WXML 恢复 Vue 页面，复用模块 API/store/uni/标签/星期工具；保持购物车数量、展开、地址、备注、费用、配送时间、餐具与弹层行为。将无界业务 console 输出从恢复源码去掉。针对失败允许提示和解除提交忙碌，防止失败后无法重试，不改变公开字段/code。
2. 恢复 template/script/style 真正编译入口，锁依赖与 README。生成结算四文件，旧产物通过起点提交保留。
3. 用合成数据和 test doubles 验证数量/费用兼容、地址备注返回、authentication/请求失败/提交字段/支付交接。额外运行旧模块与新页面互操作测试，不把 VM 当微信模拟器。
4. 两次编译与源码修改试验、结构/语法/文档检查，普通 Git diff 检查并显式暂存任务文件后普通本地 commit。

小程序消费者为首页、地址、备注、支付；这四页产物不修改。后端与管理端 API/schema 不变，无新增请求，因此其源码无需修改。收费保持 `sum(number * amount) + 6 + quantity`，单项显示 amount.toFixed(2)，打包费显示数量；金额票另行处理。

## 验收与回退

本地：合成购物车多件与四种商品、超过三件展开；空/默认/选中地址；备注保存与返回；配送今天/明天、立即派送、时间选择；餐具无需/指定数量/依据餐量、确认/取消；缺地址、重复点击、业务拒绝/网络失败；提交成功写订单/清备注/更新预计时间并 redirect 支付；旧 pay 发模拟 paymentOrder 后进入 success。所有 mocks 与原库、Redis、模型/OSS/地图调用隔离。

宿主仍必须在独立 exact commit snapshot 安装锁定依赖并执行真实命令、官方微信编译，验证正常登录与 authentication、首页到结算、地址/备注往返、弹层/配送/餐具/模拟支付；对相同合成场景取得起点/候选截图，比较布局、字体、颜色、图片尺寸、费用区和按钮。本地 Node/VM 不能证明该验收，Windows CLI 在沙箱不可用，不执行上传/预览/发布或原库写入。

回退由维护者在批准的集成流程用普通反向提交还原本票四个结算产物至起点 `612c637`，并撤销源码入口；其他页面和共享依赖从未改变。可从该 start commit 读取旧四文件建立独立对照快照，本任务不使用强制切换、reset、clean 或覆盖修改文件。页面回退不撤销已经创建的订单。push/PR/merge/sync/issue 写入等待明确人工全部通过。

## 执行结果（随验证更新）

恢复时用普通 `git apply` 应用 tracked.diff（exit 0），Python 仅复制 manifest 中 11 个未跟踪源码/文档文件，每个 resolved destination 验证在实际 worktree 内；旧证据存 `.scratch/issue5/recovered/`，不作为任务代码。原失败工作树未改动。前次 11/11 测试为历史证据，本次重新执行如下最终检查。

| 检查批次 | 实际命令 | 工作目录 | 结果/日志 |
| --- | --- | --- | --- |
| 5 | `npm ci --cache ../../.scratch/issue5/npm-cache --no-audit --no-fund` | `yuweiju-weixin-miniapp/checkout-source` | exit 0，安装 25 packages；`.scratch/issue5/final-batch5-install.log` |
| 5 | `npm run build` | 同上 | exit 0，真实 Vue 模板/render/JS 编译与四文件输出；`final-batch5-build.log` |
| 5 | `npm test` | 同上 | exit 0，11/11；`final-batch5-tests.log` |
| 6 | `npm run build` | 同上 | exit 0，保留 scope ID 的最终四文件；`final-batch6-build.log` |
| 6 | `npm test` | 同上 | exit 0，11/11；`final-batch6-tests.log` |
| 7 | `node --check`（build、业务/legacy、三个测试/harness、生成页面，共 7 文件） | 仓库根 | 全部 exit 0；`.scratch/issue5/final-batch7-static.log` |
| 7 | `python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . yuweiju-weixin-miniapp/checkout-source/README.md yuweiju-document/execplans/Issue5-checkout-source-recovery.md` | 仓库根 | exit 0，failures 为空；同上 |
| 7 | `git diff --check` | 仓库根 | exit 0；同上 |

最终审查把旧 normalizer 的 `scopeId = 0ca91b30` 补回生成 adapter，保持 Vue options 的原作用域元数据，并加入注册测试断言；WXML/CSS 已保留同一标记。因此执行检查批次 6 再构建/测试，结果记录在本节及进度。结算 CSS 与起点逐字节相同；构建测试确认另外十二页、App/common/components/uni_modules/node-modules 未改动，组件四文件、静态引用、事件/refs 均可解析。lock 与 package.json 的直接依赖一致。测试使用现有 vendor 的 `createPage → Component(parsePage(...))`，并验证旧 `__e` 事件入口、新 render、共享 store/API、正常登录、旧地址/备注/模拟支付处理器；没有将 VM 当官方模拟器或真实 API 验收。

宿主报告官方微信模拟器已连接隔离的旧快照并取得真实 checkout 数据，但同场景截图 API 超时；此为旧快照能力/证据，不证明候选。候选官方编译、真实交互、同场景前后截图、人工外观验收仍待独立宿主检查；exact commit 的无认证/模型挂载独立安装、构建和测试也尚未执行。当前只完成开发候选，issue 不关闭、不发布。

完整命令时间、退出状态、检查批次、候选提交及阻碍记入 `.sandcastle/task-progress.md` 和 `.scratch/issue5/`，不暂存这些运行证据。
