---
name: yuweiju-ui
description: 余味居前端的 UI 实现规范。涉及页面/组件的视觉实现、Element Plus 用法、样式约束、动效、布局时使用此 skill。与 project_rules.md 的设计禁止清单配合使用。
---

## 核心设计方向

中式抽象轻设计：以厨房/餐饮元素（灶火、炊烟、圆盘、饭碗、筷子）抽象化为装饰；风格克制，排版留白充裕，避免模板感。

## 配色变量（必须以 CSS 变量统一管理）

```css
:root {
  --color-primary: #6E1D20;       /* 枣红：顶栏/侧边栏/强调 */
  --color-bg-cream: #F0EBDE;      /* 米白：大面积背景 */
  --color-bg-card: #EEE6DC;       /* 奶油：卡片底 */
  --color-text-main: #0E101B;     /* 近黑：正文 */
  --color-text-sub: #2E343A;      /* 深灰：次级文字/边框 */
  --color-info: #4A8FA3;          /* 青蓝：信息态/链接 */
  --color-danger: #D94A2B;        /* 橙红：危险/强CTA（少量） */
  --color-bg-section: #F9F9F9;    /* 浅灰白：分区底 */
}
```

## Element Plus 使用要点

- 表单校验：使用 `el-form` 的 `rules` prop + `ref.validate()`
- 弹窗确认：使用 `ElMessageBox.confirm()`（组合式）
- 消息提示：`ElMessage.success/error/warning()`（不在 api 层调用）
- 分页：`el-pagination` 的 `current-change` + `size-change`
- Dialog：`v-model:visible` → Vue 3 用 `v-model` 绑定 `modelValue`
- Upload：`el-upload` 配合 `action` 或手动 `http-request`

## 样式约束

- 禁止内联样式；组件样式默认 `scoped`
- 背景必须有噪点纹理或极淡渐变（推荐 CSS `background-image: url(noise.svg)` 或渐变叠加）
- 图标：仅使用 Iconify SVG 或 Element Plus 图标，禁止 Emoji 作为功能图标
- 中式纹样（回纹/火焰纹）：仅用于边框/分割线/背景暗纹，透明度 ≤ 8%

## 动效规范

- 禁止 `ease-in-out`（线性感强）；优先 `cubic-bezier(0.4, 0, 0.2, 1)` 或分段过渡
- 动画集中在少量高价值时机（页面进入、状态切换、卡片展开），避免散点微动画
- 过渡时长：交互反馈 ≤ 200ms；页面级过渡 ≤ 400ms

## 布局原则

- 避免 Hero + 三卡片模板结构
- 避免所有元素完美居中对齐；偏向轻度不对称与编辑排版感
- 卡片与区块间保持充足呼吸感（padding 不低于 20px）

## 本地素材（优先）

需要插画/装饰图时，先从 `reference_documents/reference_images/` 目录选取；不满足时再考虑外部：
- 插画：https://undraw.co/ | 图标：https://iconify.design/
- 图片：https://www.pexels.com/zh-cn/ | https://picsum.photos/

## SFC 结构要求

```vue
<script setup lang="ts">
// 逻辑区
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped>
/* 样式 */
</style>
```
