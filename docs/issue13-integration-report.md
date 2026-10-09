# Issue #13 主线整合验证

日期：2026-10-09（Asia/Shanghai）。维护者在确认人工验收符合正常情况后，授权创建 PR、提交、推送、retro 改进，以及两项完成后的合并和关闭 #13。本文记录业务 PR 的独立集成验证；retro 在对应后续 PR 中实现和验证。

## 范围与主线

- 基于最新 `origin/main`：`bb900f93f4d1a7537bc69413c1632a70c14094e6`。
- 项目内独立 worktree：`.scratch/issue13-pr-20261009`；分支 `codex/issue13-integration-20261009`。
- 仅 cherry-pick AFK 业务候选 `b7d35c9f7ff93b0dd9a776c985035221b40a2465`，产生 `389cb49`。共享订单服务自动合并，无冲突，未带入旧 Sandcastle 准备历史。
- 提交 AFK 执行报告和维护者人工截图；含可见密码的工作台截图不入库。
- 保留原工作区所有未提交改动，不切换、stash 或覆盖用户工作区。

后端保持接口/schema/金额兼容，推荐统计故障隔离和核心事务边界按 `docs/standards/backend.md` 验证。管理端和小程序无需业务源码改动，继续消费既有下单/购物车/查看订单接口；管理端按 `docs/standards/admin.md` 四项检查，原生小程序正常下单证据见 [人工验收报告](issue%2313-executing-report.md)。微信 dev 模拟身份和模拟支付不代表生产身份交换和真实支付。

## 独立验证环境

新建两个无认证挂载/无模型调用的验证容器，镜像 `sandcastle:yuweiju-dev-git-safe`，UID/GID 1000，非登录 `bash -c`。首次 Maven/npm 使用新缓存。挂载集成 worktree，而非历史候选构建产物。

仅接入已授权的 `yuweiju-issue13-live-isolated` 合成 MySQL/Redis；下载使用 `yuweiju-issue13-live-host`。未访问原库。43 场景探针按自身用户/model 清理数据与 keys，SQL 故障 trigger 仅针对自身合成用户，不清维护者订单。既有 live 服务保持运行。

首次 Maven package：37 tests，1 failure，exit 1。容器默认非 UTF-8 locale 导致中文资源路径失真，报表测试找不到 `template/运营数据报表模板.xlsx`；不是 #13 业务故障。保留 `.scratch/issue13-pr-verification-20261009/backend.log` 与 `backend-exit.json`。随后仅修正验证容器 `LANG=C.UTF-8`、`LC_ALL=C.UTF-8`，执行 clean package；不修改业务、不跳测试。正确 locale 后中文模板实际复制到 target/classes，报表测试通过。

UTF-8 重跑的 Maven clean package 为 BUILD SUCCESS，37 tests，0 failures/errors/skips。后续 shell 脚本首次启动因 Windows `core.autocrlf=true` 工作树 CRLF 返回 exit 2（`pipefail\r`），尚未执行数据库探针；仅恢复本脚本的工作树 LF，Git blob 仍为原 LF，不修改全局 Git 设置或引入格式 gate。

| 工作目录 | 命令 | 最新集成结果 |
| --- | --- | --- |
| /workspace | `mvn -B -Dmaven.repo.local=/workspace/.scratch/backend/m2 -f yuweiju-backend/pom.xml clean package` | BUILD SUCCESS；37 tests，0 failures/errors/skips（backend-utf8.log） |
| /workspace | `bash .sandcastle/environment/issue13-check.sh` | exit 0；43 个场景全部 PASS，ISSUE13 ALL_SCENARIOS_PASS（probe-utf8.log/probe-utf8-exit.json） |
| /workspace/yuweiju-web-vue/yuweiju-admin | `npm ci && npm run lint && npm run typecheck && npm run test && npm run build` | 整组 exit 0；2 文件、3 tests 通过，build 成功（admin.log/admin-exit.json） |
| 集成 worktree | `git diff --check bb900f9..HEAD` | exit 0 |

43 个真实 Spring/MyBatis/MySQL/Redis 场景覆盖推荐读写故障降级、核心订单/明细/清车故障完整回滚、鉴权/地址归属/不可售、附加评分 Mapper 故障，以及用户/管理员 HTTP roundtrip。真实副作用逐场景 JSON 位于集成 worktree 的 `.scratch/issue13-check/probe-results-954597003.json`；驱动及原始命令/退出状态保存在项目根 `.scratch/issue13-pr-verification-20261009/`。本轮新验证不以 AFK 历史测试替代。

probe 完成后对具名隔离 MySQL 只读复核 exit 0：维护者订单 ID 40 / 订单号 1791543317096 / 用户 900003 / amount 19.00 保留，用户购物车为 0；issue13 triggers=0，issue13 synthetic users=0。验证容器自动退出且已删除，五个 live 服务保持运行。既有 npm 审计和大 chunk 提示保留，不宣称已解决依赖审计风险。

## 已知局限与回退

Redis 部分写入、TTL 残留、并发与业务重试的统计遗漏/重复仍如 [失败边界](issue13-failure-boundaries.md) 所述；SQL rollback 不涵盖 Redis/外部调用或自增分配。代码可正常 revert，无 schema 或数据迁移；revert 不撤销已创建订单或缓存副作用。金额展示 25 元而订单 19 元属于本票明确排除的既有收费规则，本轮维护者确认不阻塞 #13。
