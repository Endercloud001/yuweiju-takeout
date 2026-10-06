# 管理端协作入口

- 实际工程 yuweiju-admin，源码 src、脚本 scripts，检查命令从 package.json 读取。
- src/api 与 src/types 管理请求/类型；router、stores 管理导航/状态；views、components、layout 管理界面。
- 原素材 reference_images，应用素材 yuweiju-admin/src/assets/reference_images；字体与样式查 src/assets 和 src/style.css。
- 修改前读 [管理端规范](../docs/standards/admin.md)。请求/路由/store 可用 yuweiju-stack；现有页面和缺图展示可用 yuweiju-ui。
- 接口/数据按 [根入口](../AGENTS.md) 核对消费者、兼容性；保留当前外观和鉴权语义。
