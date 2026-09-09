# 管理端协作规则（yuweiju-web-vue/AGENTS.md）

## 目录与资源
- `src/`：UI/逻辑目录，里面包含 `assets/`（图片/样式）、`components/`、`views/`、`layout/`、`router/`、`stores/`、`api/`、`types/`、`utils/`、`composables/`、`plugins/` 等。
- `reference_images/`：设计/素材参考，仅查看不修改除非明确需求。

## 编码规范（摘录自 `.trae/rules/project_rules.md`）
- 每个文件使用 SFC 模式：`<script setup lang="ts">` + `<template>` + `<style scoped>`。
- 目录/文件结构采用 kebab-case，组件/变量命名采用 PascalCase/ camelCase；store 以 `useXxxStore` 命名。
- 禁止 `any`，有疑问使用 `unknown` 并补充类型重载。
- 使用 composition API，重复逻辑封装进 composables。
- 避免重复 CSS/样式，维护清晰的 design token。动画原则为 `ease-in-out`。
- API 返回模板 `{ code, msg, data }`，`code === 1` 表示成功；token 字段固定名 `token`。
- 路由 meta 需要包含 `title`、`hidden`、`icon`、`notNeedAuth`、`affix` 等字段。
- 组件/页面需要用 Element Plus 约定的 UI 组件。
- 所有 shell 命令/脚本运行推荐写在 `.trae/skills` 提供的 `rtk` 快捷里。

## COBOL 现代化要求
- 每个页面改动前确认正在替换或展示哪条 COBOL 业务流程，并在 ExecPlan/PR 里说明验证步骤。
- 如需调用后台接口，确认接口文档来自 `yuweiju-document/api`，并说明 COBOL 对应字段（如 legacy order_id）如何映射。
- 表单或导出功能涉及 COBOL 数据时，要明确定义同步方式与落地路径。

## 验证与守则
- 运行 `npm run lint`、`npm run typecheck`，确保 UI 修改通过。
- 增加新 route/store 需补充在 `router`、`stores`、`api/modules` 中的统一结构，并更新 `types/apiResponse`。
- 新增图标/图像要继承 `reference_images` 目录的视觉指导，并标明色值（如 #6E1D20 等）。
- TODO 注释应附带模块、目的、计划级别。

## 参考文件
- `yuweiju-web-vue/.trae/rules/project_rules.md`
- `yuweiju-document/api` + `yuweiju-document/db`
- `yuweiju-document/execplans/余味居 外卖项目 To-do List.md`
- 后端 `yuweiju-backend/.trae/rules/rules.md`（涉及接口/数据对齐时必读）

## 交付流程
1. 先查 ExecPlan 和 `To-do List`，确认当前功能/视觉目标。
2. 编写 plan （若变更接口需列明 API、数据、权限折线）并遵循 `PLANS.md` 格式，记录 Surprises/Decisions。
3. 代码完成后跑 lint/typecheck，再在 PR 描述中引用 `project_rules` 的约束（例如命名/结构差异）。
4. 若为 COBOL 迁移页面，附带定位表/API 的描述与验证 log。
