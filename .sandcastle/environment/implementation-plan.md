# Sandcastle 环境补齐方案（2026-10-07）
范围：独立 codex/sandcastle-environment-20261007 分支；.sandcastle 编排/环境/验证脚本及 docs/sandcastle-afk-complement-report.md。从 Windows ecd2029 建立新 Linux worktree，复用旧编排源码，不动 #4 分支、worktree、证据、认证。
1. 配置分支、启动提交、prompt、输入、镜像及检查命令；保持监督/超时/取消/清理/缺提交机制；本地无模型生命周期测试。
2. Linux Java21/Maven/浏览器镜像；内部 Docker 网络 MySQL/Redis；从示例提取纯建表、添加人工合成夹具；独立配置替换 .env/dev；实际 Maven package、Spring代理事务与连接测试。
3. Linux 浏览器截图与正常隔离账号登录；Windows 微信工具隔离原生夹具编译、连接与截图，调查生成页面依赖，不恢复业务源码。
4. 实际 XGBoost JNI/Smile 加载及合成算法训练；业务训练窗口/标签调查，不以算法库样例替代业务训练。
5. 当前规范与 Issue/评论供给；缺失历史证据标注；交付命令、退出状态、恢复和未验。
三端：仅环境测试，不改变业务 Java/Vue/小程序、API 或原 schema；新隔离数据库复用现有 schema。小程序业务源码恢复属 #5/#6，当前不实施。
回退：Compose down（不加 -v）、停止本轮具名容器/进程；保留镜像、数据卷、新分支、snapshot和日志；不 reset/clean/prune 或清理旧试点。
