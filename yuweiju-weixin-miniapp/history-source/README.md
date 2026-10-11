# 历史订单与详情页可编辑源码

Issue #6 的独立最小 Vue 源工程，仅恢复 `pages/historyOrder/historyOrder`、`pages/details/index`，不恢复全13页。其他页面、app、common/vendor、组件继续使用已有工程。后端/管理端、接口/schema均不改，管理端履约变更通过用户页面重新显示或刷新原接口获得。

## 编辑与构建

编辑 [HistoryOrder.vue](src/HistoryOrder.vue) / [Details.vue](src/Details.vue) 的模板和样式，以及 [history.js](src/history.js) / [details.js](src/details.js) 的页面行为。SFC样式保留旧WXSS原文及既有scope选择器，使用WXSS/CSS语法；不依赖Sass。scopeId与normalizer保持原值；新增样式应沿用相应页面类及原scope选择器。不要直接改生成JS/WXML作为业务来源。

Node 22（本轮22.23.3），npm10；依赖使用已合并checkout-source的版本与锁定依赖：uni-template-compiler `2.0.0-22420190823021`、vue-template-compiler `2.6.10`、vue-template-es2015-compiler `1.9.1`、esbuild `0.25.12`。不依赖不可取得的HBuilderX或完整uni CLI。两个npm工程分别安装，不复用跨平台node_modules。

从仓库根运行以下四组命令；前两组各自从根启动：

```sh
cd yuweiju-weixin-miniapp/history-source && npm ci --no-audit --no-fund && npm run build && npm test
cd yuweiju-weixin-miniapp/checkout-source && npm ci --no-audit --no-fund && npm run build && npm test
node --check yuweiju-weixin-miniapp/pages/historyOrder/historyOrder.js && node --check yuweiju-weixin-miniapp/pages/details/index.js
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . yuweiju-weixin-miniapp/history-source/README.md yuweiju-document/execplans/Issue6-history-source-recovery.md
```

[构建器](build.cjs) 每次只写两页各JS/WXML/WXSS/JSON共8个文件。模板由真实uni编译器处理，业务经esbuild打包，Vue render去with后交给旧normalizer。测试以普通字节比较验证重复构建一致及其他11页/共享运行时/组件不变；在项目 `.scratch/issue6` 内修改临时SFC并执行编译后的页面，验证源编辑改变真实模板及行为。没有hash、冻结contract或额外gate。

checkout既有编辑探针默认写 `.scratch/issue5`；本票检查时可先从仓库根执行 `export YUWEIJU_TEST_SCRATCH="$PWD/.scratch/issue6"`，再运行上述命令，使checkout探针也写本票临时目录。该可选环境变量只控制测试临时目录，不改变checkout源码/编译产物。

## 兼容性与交互

[局部桥接](src/legacy.js) 复用 [checkout runtime bridge](../checkout-source/src/legacy.js)，从 `wx.__webpack_require_UNI_MP_PLUGIN__` 取得原uni、store、API、组件和工具，不复制/改写vendor。运行必须使用当前小程序app/main/common启动链及已有组件/static，不能把两页拷入空微信工程独立启动。

公开页面路由、`orderId`、API字段/类型保持。请求继续走原API24/request25，带原store的 `authentication`；页面严格判断 `code === 1`，业务拒绝或网络失败如实提示。订单归属仍由后端确认，页面不以路由参数代替认证。列表分页成功后才推进页码，失败可重试同页；tab使用全部 `''`、待付 `1`、已取消 `6`，刷新/onShow重新加载第一页，防重复追加/旧响应覆盖。空列表用已有Empty组件。

详情加载失败、空响应或拒绝时清空当前详情及原共享明细，隐藏旧金额和操作；有效详情将明细写入旧store保持跨页兼容，但本页展示使用自己的明细，避免购物车改动影响历史详情。保留原支付store与路由、取消/催单弹层、商家电话、先清车再来一单及返回路径。详情倒计时维持15分钟与旧取消接口，刷新只保留一个计时器，hide/unload/destroy清理；迟到请求不得恢复隐藏页计时器或已销毁页面数据。

## 最终第二轮回归修复（2026-10-11）

首轮候选 `d030e1d` 的23项history测试和14项checkout测试通过，但宿主独立验证失败：历史SFC把 `orderTime` 字符串直接交给只接受剩余毫秒数的旧 `statusWord`，订单行文案为空；把剩余时间判断写成取反，新订单缺少“去支付”。已恢复旧模板的 `statusWord(item.status,getOvertime(item.orderTime))` 和 `getOvertime(item.orderTime)>0`，旧工具、时间口径与路由不改。

新增 [模板行为测试](tests/template.test.cjs) 编译实际保存的两个SFC，用真实旧Vue/store/API/tools运行VNode模板。订单行单独断言待付款/已取消，不把顶部tab标签当成行文案；覆盖未过期/过期支付按钮、点订单和去支付的详情路由、详情立即支付路由与持久化支付数据、超时取消、取消条件优先级和催单参数、onShow履约刷新、19元与历史图绑定、明细展开。它不依赖生成变量名、快照或hash。修复前有效红测8项中3项失败，修复后扩展为9项全部通过。最终规定四组检查批次全部exit0：history构建测试32/32、checkout构建测试14/14、两页语法和本地文档链接；无skip/cancel。首次checkout测试漏设临时目录变量，保留项目内默认issue5探针后，仅补跑设定本票scratch的checkout测试14/14通过。

逐项核对恢复的Details/History模板与 `b303dab` 的旧WXML/render及vendor行为；保留首轮明确的loaded失败防护、onShow刷新与持久化19元金额修复。未发现需要另外修改的同票语义转写错误。原模板的 `paymentTime` 无定义，恢复源留空，不新增字段。官方工具就绪是维护者反馈；本容器没有官方微信编译、截图或宿主最终候选独立复核证据，这些由宿主分别执行。

## 原金额与图片、证据边界

列表显示持久化 `amount`，详情显示持久化 `amount` 与已有 `packAmount`；配送费继续#10的“以订单合计为准”。不套新单费用公式，不合成缺失费用。合成旧订单974总额19仍显示19；这不是对原库974的验证。订单明细 `image` URL原样绑定，不更新历史价格/图片，不加入图片fallback或修复下载失败。保存规格A3及ADR0002的图片语义/缺图兜底归授权中的#11。

普通测试执行真实生成页、旧Vue/store/API/request，微信IO/定时调度使用隔离合成替身，覆盖分页/tab/空/失败/刷新、身份请求头、业务code拒绝、详情路由/他人拒绝响应/清空、19元与URL保留、销毁及既有导航操作。没有连接原MySQL/Redis、原库driver或外部业务服务；拒绝响应替身不证明真实后端归属，模拟支付也不证明真实收付款。

旧历史JSON与截图已清理，不重跑原库驱动补证。本轮Node构建/测试/语法检查 **不是官方微信编译或视觉证明**。宿主已安装并登录官方微信IDE，必须对精确候选在整个已有小程序中重新编译，保留编译日志及正常图的历史列表/详情截图，实际验分页/tab、返回、支付、取消、催单、再来一单与联系商家。独立验证、官方编译/截图、用户接受均待宿主；本票不宣称图片fallback验收完成。

完整影响、命令和回退见 [实施方案](../../yuweiju-document/execplans/Issue6-history-source-recovery.md)。维护者可普通revert本票，恢复两页产物和删除本独立源目录；无schema迁移，也不能撤销过去的历史图片覆盖。只产生本地候选commit，不执行push/PR/merge/发布。

## 宿主验收与交付状态（2026-10-11）

上文“待宿主验证/待人工接受”描述容器交接时的状态。宿主随后完成官方微信编译、12项SDK交互、8项真实隔离API检查及前后截图核对，维护者明确反馈全部人工验收通过。历史32项与结算14项普通测试全部通过。精确候选与交付源码一致；缺图兜底及真实支付不在本票验收范围。验收服务现已停止，合成卷保留，详见[执行报告](../../docs/issue%236-executing-report.md)。
