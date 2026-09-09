# 微信小程序协作规则（yuweiju-weixin-miniapp/AGENTS.md）

## 目录与职责
- `pages/`：小程序页面入口，按功能划分 page 目录，保持目录结构清晰、文件命名统一。
- `components/`：可复用组件，命名使用 PascalCase，避免在页面中重复书写 UI。
- `common/`：封装网络请求、数据转换、常量与状态管理。
- `static/`：图片/字体现状资源，跟随 `reference_images` 设计（如果可用）维护风格一致。

## 编码与风格
- 继续使用微信官方小程序规范：`.wxml` 对应 `.wxss`、避免重复嵌套组件/样式；`app.js`/`project.config.json` 中保持基础配置同步。
- 尽量用 `Promise`/`async` 封装网络请求，错误捕获后以 `wx.showToast` 或 `wx.showModal` 反馈。
- 控制台日志不要打印敏感信息（token、密码等），上线前可用 `if (process.env.NODE_ENV !== 'production')` 包裹。
- 所有 TODO 注释需带上模块与目的，还有关联 ExecPlan/Issue。
- 遇到涉及后端接口的字段命名与类型，参考 `yuweiju-backend/.trae/rules/rules.md` 与接口文档 `yuweiju-document/api`，确保字段一致（如 `snake_case` 到 camelCase 映射）。

## COBOL 现代化相关
- 每个交互说明需写明背后的 COBOL 业务流程是什么（例如订单同步、支付对账），并描述展示/提交的数据与传统系统如何对齐。
- 当展示 COBOL 数据（例如 legacy order_id）时，在页面/组件注释中标注其来源与验证方式。
- 涉及搜索、筛选、导出逻辑的页面需在 ExecPlan 中列出对 COBOL 表或接口的依赖与验证。

## 验证流程
- 编译/类型检查后再提交（若有 `npm run build` 或 `npm run lint` 相关脚本，请在 plan 中注明并执行）。
- 接口变化需同步通知管理端和后端，并在 To-do List/ExecPlan 中列出依赖点。
- 便携式组件/页面尽量使用 `components/`，并确保样式兼容 `app.wxss` 的主题。

## 参考文档
- `yuweiju-document/api`、`yuweiju-document/db`、`yuweiju-document/execplans/余味居 外卖项目 To-do List.md`
- `yuweiju-backend/.trae/rules/rules.md`（接口、一致性、命名约定）
- 根目录 `PLANS.md` 与本仓库的 AGENT（含 COBOL 指南）

## 流程提示
1. 阅读 plan / To-do List，确认当前功能所依赖的接口/数据。
2. 如果该接口还在管理端/后端被引用，请在 plan 中列出端点、字段与 COBOL 依赖。
3. 编码后运行测试或小程序编译，记录验证结果并更新 ExecPlan 的 Surprises & Decision。
4. 发起 PR 前，说明如何满足 `.trae`/AGENT 中的要点（例如命名、字段对齐、log 处理）。
