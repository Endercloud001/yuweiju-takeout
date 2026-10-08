# #5/#6 编译材料调查（2026-10-07）
维护者本轮确认：只有 yuweiju-web-vue 与 yuweiju-weixin-miniapp 中现有代码，没有可取得的额外 uni-app 源工程。管理端 Vue 工程不能充当小程序源码。
当前 app.js 按序加载 common/runtime.js、common/vendor.js、common/main.js；目标三页 JS 将模块推入 global.webpackJsonp，通过 @dcloudio/uni-mp-weixin createPage、Vue 模块 4、uni-pages 模块 5、componentNormalizer 模块 11 创建页面。checkout 的模块 35/36/38 记录已消失的 .vue 与样式源路径，历史与详情也依赖共享 Vue/uni runtime。原产物中的 HBuilderX 2.2.2 路径是历史生成注释，不能证明当前需安装它。
当前未找到小程序 .vue、manifest.json 或 package.json/build 入口。components、uni_modules、node-modules 是现有页面依赖，不能把它们当已锁定、可重建的 npm 工程，也不能用管理端 Vue3/Vite 工具重编译旧小程序 Vue/uni bundle。
#5 需调查购物车、地址、备注、导航、费用区与 uni-nav-bar/list/popup/pikers 适配；#6 独立调查历史分页、详情、图片、状态刷新与 uni-nav-bar/popup/list-item 适配。不得预设两票相互阻塞。
环境已提供：Windows 微信开发者工具 Stable 2.01.2510280；SDK 3.17.2；独立 touristappid 原生 Page/App 项目，从源保存后编译与容器/WSL截图成功。Linux miniprogram-automator 0.12.1 独立安装，不复用 Windows node_modules。该夹具没有业务请求，不证明业务三页已恢复。
后续最小方案可先调查三页原生可编辑适配与保留既有产物/共享运行时的兼容边界；若无法局部隔离，需列出具体共享源依赖和最小扩大范围，交由维护者决定。本轮未恢复页面、未重建全小程序、未安装 HBuilderX 或猜测 uni-app 编译依赖。
单项限制：page.data() 对此 SDK/工具组合曾超时；currentPage、官方 screenshot 和保存重编译截图已通过，不把页面 JSON 当视觉证据。截图只关闭连接，不退出整个开发者工具；停止时只关闭本轮隔离项目。
