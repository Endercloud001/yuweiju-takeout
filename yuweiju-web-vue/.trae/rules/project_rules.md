## 项目概况

余味居管理后台，技术栈：Vite 7 + Vue 3.5 + TypeScript 5.x（严格模式）+ Pinia 3 + Vue Router 4 + Element Plus + npm。迁移已完成，当前为正常迭代阶段。

## 目录结构

```
src/
  assets/       静态资源
  components/   通用组件
  views/        页面（与路由对应）
  layout/       布局骨架（侧边栏/导航）
  router/       路由定义与守卫
  stores/       Pinia stores（按业务拆分）
  api/          http.ts + modules/各模块
  types/        全局类型（ApiResponse、业务实体）
  utils/        工具函数
  composables/  可复用逻辑（useXxx.ts）
  plugins/      插件注册
```

## 代码规范

- SFC：`<script setup lang="ts">` + `<template>` + `<style scoped>` 顺序固定
- 组件名：PascalCase；文件名/目录：kebab-case
- 变量/函数：camelCase；Store 导出：`useXxxStore`；事件：kebab-case
- 禁止 `any`，必要时用 `unknown` + 类型守卫
- 禁止内联样式；新增依赖须有明确落地用途

## 接口与鉴权约束（不可变）

- Base URL：`import.meta.env.VITE_API_BASE`（默认 `/api`）
- Token 请求头字段名固定：`token`
- 响应体结构：`{ code, msg, data }`，`code === 1` 为成功
- 路由 meta 字段：`title / hidden / icon / notNeedAuth / affix`
- 守卫逻辑：有 token 放行；无 token 且 `meta.notNeedAuth !== true` 跳转登录

## 质量门禁

`npm run lint` + `npm run typecheck` 必须通过；存在时执行 `npm run test`

当需要执行 shell 命令时，请优先使用 rtk 作为代理：
- git status → rtk git status
- git log → rtk git log  
- git diff → rtk git diff
- ls → rtk ls .
- npm/pnpm list → rtk npm list

## 设计规范（中式轻设计，强约束）

**配色**
- 主色（枣红）：`#6E1D20`（顶栏/侧边栏/标题/强调）
- 背景/卡片：`#F0EBDE` / `#EEE6DC` / `#ECE8DC`（米白奶油）
- 正文：`#0E101B`（近黑）
- 次级/边框：`#2E343A`（深灰）
- 信息态/链接：`#4A8FA3` / `#5FA8B8` / `#8ED3E0`（青蓝）
- 危险/告警：`#D94A2B`（橙红，少量点缀）
- 分区底：`#F9F9F9` / `#FAFAFA`（浅灰白）

**字体**：中文用喜鹊招牌体/汉仪尚巍手书/小米兰亭 Pro DemiBold；英文/数字用 Franklin Gothic Demi

**禁止清单**
- 纯平背景色（必须有噪点或极淡渐变）
- 紫色/靛蓝渐变
- Hero + 三卡片模板化布局
- 滥用完美居中对齐
- Emoji 作为功能图标
- `ease-in-out` 线性动画（优先自然 easing 或分段过渡）

**设计元素**：抽象厨房元素（灶火/炊烟/圆盘/饭碗/筷子）；中式纹样（回纹/火焰纹）极低强度用于边框/暗纹

## 本地素材（优先使用）

图片/插画：`reference_images/`（登录/工作台/空状态等优先从此取）

## 参考资源

UI 灵感：https://uiverse.io/ | https://21st.dev/home | https://ui.aceternity.com/ | https://magicui.cn/ | https://ui.mantine.dev/
图标：https://iconify.design/ | 插画：https://undraw.co/
图片：https://www.pexels.com/zh-cn/ | https://picsum.photos/
文档检索：https://context7.com/
