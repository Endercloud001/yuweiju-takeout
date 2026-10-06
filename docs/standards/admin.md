# 管理端技术规范

实际工程 `yuweiju-web-vue/yuweiju-admin/`，依赖和命令以 package.json、锁文件及配置为准。沿用 Vue Composition API、严格 TypeScript、Pinia、Vue Router、Element Plus；保留 [首期规格](../yuweiju-restore-spec.md) 的外观和兼容要求。

## 请求、路由与状态

请求在 `src/api/http.ts`、`api/modules/`，业务类型在 `src/types/`。base URL 来自 VITE_API_BASE，http.ts 没有硬编码 `/api` 默认值，使用时核对环境及代理。Cookie token 经 `token` 请求头发送，成功业务 code 为 1。响应还含 success/message，不能用旧三字段模板删字段。request 返回响应体、失败抛 Error；下载用 requestBlob，不按旧模板重建拦截器或统一弹错。

路由/守卫以 `src/router/` 为准，按页面需要填既有 meta，不要求每条填满所有字段。store 沿用 useXxxStore，登录/退出保持 Cookie 与内存一致；导航不代替后端认证/归属校验。

## 组件与视觉

新增/修改组件沿用邻近 SFC 的 script setup lang=ts、template、scoped 样式组织；类型不清先用 unknown 与守卫，避免新增 any。只抽取有实际复用价值的组件/composable，沿用目录命名，不批量改名。

Element Plus 表单、Dialog v-model、上传、分页及确认交互按现有用法核对。API 抛错、页面展示时避免重复提示。保留色值、字体、图标、布局和动效，以 src/style.css 与邻近组件为依据，不恢复强制噪点、字体安装或矛盾 easing 禁令。

原素材 `yuweiju-web-vue/reference_images/`，应用素材 `yuweiju-admin/src/assets/reference_images/`，复用缺图补位图。复制脚本含本机绝对路径，使用前检查，不作为页面修改前置。[历史订单图片](../adr/0002-preserve-historical-order-images.md) 保留已有图片，缺失兜底，不自动重设计。

## 验证

工作目录 `yuweiju-web-vue/yuweiju-admin/`，按改动运行现有 lint/typecheck/test/build。完整 lint 待 issue #4 修复，失败记录实际原因与影响，不排除目录伪称通过。界面改动检查实际路由、正常登录、空/错/加载状态及三端一致性。文档迁移只验证文档/路径，不宣称业务 UI 验收。
