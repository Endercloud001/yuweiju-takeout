# Issue #19 复盘运行时独立交付

## 范围与依赖

远端 main 已合并业务 PR #24，但不含 `.sandcastle/` 和根目录运行时 package 文件。因此本次同时纳入已使用的运行入口、隔离环境、普通夹具及 package 锁文件；仅提取这些路径，不合并原环境分支历史。复盘源提交为 bcbaa03。

改进涵盖 UTF-8 locale、候选快照 environment 挂载、Java/locale/实际命令记录、同次 run 两轮隔离依赖缓存和分轮容器停止记录。配置夹具入口改用当前 HEAD，避免只验证历史提交。独立复验仍使用干净容器，不挂认证、不调用模型，不新增 gate、baseline 或冻结 contract。

三端业务、接口、schema、认证实现不变，无需消费者迁移。运行与缓存材料保存在本地 evidence 下；代码回退不删除已有证据或恢复外部状态。历史环境说明保留为来源，旧业务结果不作为本次业务复验。

## 验证

本次独立 PR 工作区 `/home/endercloud/projects/yuweiju-retro-pr`。验证结果在完成后追加。本次不启动 Compose、不访问原库、不重跑业务 AFK，也不上传本机认证目录。

当前候选已完成：`npm ci`、`npm run check:types`、三项 Python py_compile、`git diff --check` 均退出 0。`run-config-fixture.py` 当前 HEAD 无模型两轮夹具退出 0，独立五条命令全通过，合成候选 environment 挂载在 network none 下通过；`npm run check:lifecycle` 九项全通过；`result-summary.check.mts` 通过。共 12 份资源记录 stopped=true，12 个具名容器均核对不存在。

证据目录 `/home/endercloud/projects/yuweiju-retro-pr-config-evidence`、`/home/endercloud/projects/yuweiju-retro-pr-lifecycle-evidence`；日志 `/home/endercloud/projects/yuweiju-retro-pr-{config,lifecycle,summary}.log`。没有挂认证或调用模型。镜像未重建，locale 本次由启动参数实测保证；未重新执行完整三端业务、训练或微信交互验收。新代码仅改变无模型夹具使用当前 HEAD，其他运行时代码与已验证复盘源一致。
