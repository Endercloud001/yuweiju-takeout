---
name: yuweiju-ui
description: 调整余味居管理端现有页面、Element Plus 交互或缺图展示时使用，保持现有视觉风格。
---

# 现有界面维护

读 [管理端规范](../../../docs/standards/admin.md)，定位 yuweiju-web-vue/yuweiju-admin/src 实际路由、邻近组件、外观和加载/空/错状态。

颜色、字体、间距和动效来自现有样式；素材从 yuweiju-web-vue/reference_images 或 yuweiju-admin/src/assets/reference_images 选择，复用缺图补位图。按当前 issue 范围实施，不引入远程设计审查或页面重设计。

Element Plus 交互沿实际 API/v-model 核对，避免重复报错。检查受影响页面状态和原图片保留，报告视觉验证与构建真实结果。
