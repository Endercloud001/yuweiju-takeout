# 后端协作入口

- Java 源码 `src/main/java/com/codeying/`：controller 为 HTTP 入口，service 与 service/impl 为业务服务，mapper 为持久化，dto/vo/entity 为请求/响应/数据库对象。
- 配置及 SQL 资源 `src/main/resources/`，测试 `src/test/`，工具脚本 `scripts/`；依赖以 pom.xml 为准。
- 修改 Java、配置映射、查询或测试前读 [后端规范](../docs/standards/backend.md)。职责恢复/查询定位可用项目 yuweiju-java-backend 技能。
- 接口/数据调整按 [根入口](../AGENTS.md) 读取资料并记录两前端调用点；业务/架构按共享 domain 文档和 ADR 核对。
- 本机启动读 [启动说明](../docs/agents/local-development.md)；验证脚本先查原库、Redis、模型文件和 HTTP 副作用。
