# 结算页源码与独立重建（Issue #5）

编辑 [src/Checkout.vue](src/Checkout.vue) 的模板/样式和 [src/checkout.js](src/checkout.js) 的业务逻辑，然后重新构建。此入口只恢复结算页，其他十二页、全局 App、vendor/runtime 与组件仍是现有产物。

已实测 Node **22.23.3**、npm **10.9.9**。构建依赖精确固定在 package.json/package-lock.json：uni-template-compiler **2.0.0-22420190823021**、vue-template-compiler **2.6.10**、vue-template-es2015-compiler **1.9.1**、esbuild **0.25.12**。无需 HBuilderX、完整 uni CLI 或新 Vue runtime。

从仓库根进入 `yuweiju-weixin-miniapp/checkout-source/`，执行：

```sh
npm ci --cache ../../.scratch/issue5/npm-cache --no-audit --no-fund
npm run build
npm test
```

`npm run build` 真实解析 Vue SFC、编译微信模板/WXML 与 render、移除 render 的 with、以 esbuild 打包页面 JS，并输出 `../pages/order/index.js`、`index.wxml`、`index.wxss`、`index.json`。不输出新的 runtime/vendor，也不触碰其他页面。CSS 从原产物恢复，可编辑现有选择器；其 `data-v-0ca91b30` 为原作用域标记，模板编译沿用它。组件和 static 路径保持原样；仅移除未在模板使用的 `popup` 组件声明。

[src/legacy.js](src/legacy.js) 通过已有 `wx.__webpack_require_UNI_MP_PLUGIN__` 复用当前 main 的模块：

| 依赖 | 现有模块/产物 | 目的 |
| --- | --- | --- |
| Vuex | 12 | 同一 token、购物车、选中地址、备注与订单 |
| API/request | 24/25 | 原公开路由/字段/code 与 authentication |
| uni/Vue/normalizer | 1/4/11 | 继续使用旧页面生命周期与组件关系 |
| 标签/星期 | 29 | 原地址标签和日期显示 |
| navbar/list/list-item/popup/pikers | 145/152/173/166/180 | 复用原组件视觉、slot、ref 与事件 |

首次正常启动应仍使用小程序原 `app.js`，不能单独运行结算页 JS，也不能删除 common/main.js。这些编号属于当前仓库的互操作依赖；未来共享运行时有意重建时需要重新调查适配，不能自行替换 vendor。旧业务模块 39 仍保留在 vendor 供起点回退使用，新页面不再执行它。

Issue #10 已统一新单费用为可信商品金额合计 + 商品总份数 × 1 元打包费 + 2 元配送费，单份18元为21元、两份为40元。结算在进入及提交前重新读取服务端购物车报价；报价不可用时禁止提交，金额变化时要求再次确认。服务端忽略客户端 amount/packAmount 并再次计算；历史订单显示已保存金额。单项仍显示单价，打包费显示份数。提交字段、预计时间处理、餐具与配送选择兼容旧行为。新源码补充失败提示、提交忙碌解除和重复点击保护，使拒绝/网络失败后能重试，不变更认证或服务端规则。旧支付页继续模拟支付，不调用真实 requestPayment。

`npm test` 使用 Node 测试及合成微信 IO；执行当前仓库的旧 Vue、Vuex、request/API、正常登录、地址/备注/支付模块和新生成结算。覆盖数量/费用、展开、地址/备注返回、配送/餐具弹层、authentication、提交拒绝/网络失败/重试/重复点击、模拟支付交接。构建测试还实际编译两次并逐文件检查其他页面与共享产物未变，修改 `.scratch/issue5/` 内的源码副本证明模板和业务编辑会改变生成输出。临时副本保留在项目 scratch 供查看，不写原源码。

这些本地测试不能证明官方微信编译、真实 API 或视觉验收。宿主应在独立候选快照执行上述锁定安装/构建/测试，再用已登录的微信开发者工具官方编译，检查正常登录、首页→结算、地址/备注往返、配送/餐具弹层和模拟支付；在隔离合成服务下对起点与候选做同场景截图。缺图、失败提示、布局、字体、颜色、图片尺寸、费用区和按钮仍需查看截图及人工确认。沙箱没有 Windows CLI；不调用原 MySQL/Redis、外部地图/模型/OSS、不上传/预览/发布。

完整调查、验收与起点回退见 [实施方案](../../yuweiju-document/execplans/Issue5-checkout-source-recovery.md)。起点 `612c637` 保留全部旧结算产物，维护者可在获准的集成流程用普通反向提交回退本票；回退页面不撤销已经提交的订单。后端及管理端接口/schema 无变化。日志与未完成宿主项记录在项目 `.scratch/issue5/` 和 `.sandcastle/task-progress.md`，不作为产品源码提交。

2026-10-10 恢复草稿后的检查批次 5 已实际执行上述干净安装、构建、测试，均 exit 0，11/11 Node/VM 测试通过；旧日志仅为参考。最终适配审查补回原 normalizer scope ID 后，检查批次 6 构建 exit 0、测试 exit 0（11/11）。批次 7 的 7 文件 JS 语法、两份文档链接及 Git diff 检查也全部 exit 0。宿主在隔离旧快照已连接官方模拟器并取得 checkout 数据，同场景截图 API 超时；候选提交的官方编译、实际交互、前后截图和人工外观确认，以及对最终提交的独立无认证/模型挂载检查，仍待宿主完成。开发候选不等于 issue 全部验收通过。

以上批次记录为 #5 开发阶段历史。最终联合候选 d0f1874 的独立验证构建及14/14小程序测试通过，后端74测试、管理端检查及真实隔离计费/回滚探针通过。维护者已重编译并确认外观、地址/备注/餐具/配送、21/40新单与旧38订单、模拟支付和购物车联动全部通过，无异常。详见 [计费验证](../../docs/issue10-billing-verification.md) 与 [#5执行报告](../../docs/issue%235-executing-report.md)、[#10执行报告](../../docs/issue%2310-executing-report.md)。
