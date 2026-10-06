# 本机启动说明

命令在仓库根目录运行，依赖先查后端 POM、环境配置及示例，不复制本机凭据。旧 run-backend.cmd 已退役；有效信息是 Java 21、dev profile 和后端 Maven 工程，不依赖个人 JDK 路径或固定日志重定向。

```powershell
mvn -f yuweiju-backend/pom.xml spring-boot:run '-Dspring-boot.run.profiles=dev'
npm --prefix yuweiju-web-vue/yuweiju-admin run dev -- --host 127.0.0.1
```

MySQL、Redis 和本任务涉及的外部服务按实际配置准备。上述命令会启动应用，本轮未执行。前台进程 Ctrl+C 停止，只停止本次应用，不停止用户既有 MySQL/Redis 或开发者工具。

2026-10-05 默认 jar 启动曾出现 Windows JDK 管道临时路径错误；当时如下参数可启动，属本机排障证据，不承诺其他机器复现：

```powershell
# 工作目录：yuweiju-backend，需已有对应构建产物。
java '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp' -jar target/proj-boot-1.0-SNAPSHOT.jar
```

历史结论见 [最小验证报告](../yuweiju-minimal-verification.md)。当前启动复现仍由 issue #3 推进，此说明不代替该票验收。
