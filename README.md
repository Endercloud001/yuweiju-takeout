# 余味居外卖系统

包含 Spring Boot 后端、Vue 3 管理端和微信小程序的外卖项目。

## 目录

- `yuweiju-backend/`：后端源码、Maven 配置与验证脚本。
- `yuweiju-web-vue/yuweiju-admin/`：Vue 管理端，含依赖锁文件。
- `yuweiju-weixin-miniapp/`：微信小程序与页面所需组件资源。
- `yuweiju-document/`：接口文档、数据库设计和开发计划。

## 本地配置

仓库不包含真实密钥和本机配置。首次克隆后：

1. 在 `yuweiju-backend/src/main/resources/` 中，将 `application.example.yml` 复制为 `application.yml`，将 `application-dev.example.yml` 复制为 `application-dev.yml`。
2. 将配置中的 `REPLACE_ME` 替换为自己的数据库密码、JWT 密钥及云服务凭据。通过环境变量或后端 `.env` 设置 `DEEPSEEK_API_KEY`、`QWEATHER_PRIVATE_KEY` 等；检查微信支付证书路径和服务地址。
3. 在专用开发数据库中按需执行 `yuweiju-document/db/db_init.example.sql`。这是包含演示数据的初始化脚本，执行前检查其中的建表/删除语句；不要对已有业务数据库直接执行。演示管理员凭据仅供本地开发，使用前修改。
4. 后端使用 Maven 和 `pom.xml` 指定的 Java 版本启动。管理端进入 `yuweiju-web-vue/yuweiju-admin/`，执行 `npm ci`，按项目需要设置本地 `.env`，再执行 `npm run dev`。
5. 使用微信开发者工具导入小程序目录，检查 AppID、请求地址与自己的开发环境配置。

## GitHub 上传范围

纳入三端源码、运行所需静态资源、构建描述与锁文件、协作规则、API/数据库设计及开发计划。
小程序的 `node-modules/` 是页面直接引用的组件目录，因此保留。

排除真实 `.env`、后端原始配置、原始数据库初始化脚本、微信开发者工具私有配置、证书私钥、依赖缓存、编译产物、日志、运行模型、工具目录、压缩备份与 `yuweiju-document/archived/` 历史论文及原始报告。原始配置和 SQL 留在本地，仓库提供脱敏示例。

本次导入仅整理版本管理范围与配置示例，没有修改业务代码；不代表三端业务测试或正式发布验证已经完成。
