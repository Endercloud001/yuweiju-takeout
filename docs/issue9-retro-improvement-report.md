# Issue #9 复盘改进实施报告

2026-10-10（Asia/Shanghai）。来源：[一次全流程复盘](issue%239-executing-report.md#一次全流程复盘)。按四项顺序落实。只有准备参考与宿主抓取助手改变；监督器、统一 verifier、认证与原安全措施保持。

## 起点与授权

`git ls-remote origin refs/heads/main` 和 `git fetch origin main` exit 0，确认 main `42eac72fb1024f3f9b094669c399ed3b7781409b`。维护工作树 `E:/Learning Files/yuweiju-takeout/.scratch/issue9-retro-20261010`，分支 `codex/issue9-retro-20261010`。根工作区仍在用户分支 `codex/issue-4-refimg-scripts` / ecd2029，已有修改与 PLANS 删除保留。方案为维护工作树 `.scratch/maintenance-plan.md`。

读取最新 main 的 startup/runtime/prepare-input.py 与 #13 验证入口，未依据旧 runtime 推断当前能力。#9 的运行时来源、coding/review stop、独立结果和最终交付停止记录均已核对。业务票已交付关闭，无需重开。

三端源码、API、schema、业务数据没有变动；改动只涉及下一次 AFK 准备，所以后端、管理端、小程序均无需修改。维护实施阶段没有提交、推送、PR、合并或部署，也没有运行模型、数据库探针或恢复服务。实施完成后，用户另行调用 pr 技能并明确授权本维护任务提交、推送、PR、合并及同步；后续交付按此授权执行，不继承业务票权限。

## 逐项结果

| 顺序 | 现状与实施 |
| --- | --- |
| 1 实际主键快照 | 本票已解决：Issue9Probe 全表快照按 risk 表的 order_id/model_version 或 feature_version 排序。通用 startup 缺少提示，本轮补入按实际 schema、复合键和排序列核对，复用已有助手；标识符固定、业务值参数绑定。没有新增 hash/baseline/gate。 |
| 2 package 完成后取依赖 | 本票已顺序化，探针已有缺 BOOT-INF/lib 的明确错误；通用 runtime 补入等待构建进程结束、exit 0、同候选 checkout 后再提取依赖，保留独立命令并行能力和原预算。监督器按配置顺序执行的行为未改。 |
| 3 schema 合法夹具 | 本票已解决：schema 的 number NOT NULL 保留，探针明确 null 分支仅普通测试。startup 同一准备段补入默认值/约束与可落库边界核对，数据库场景使用隔离探针，仅代码防御分支使用普通测试，避免为探针迁移 schema。 |
| 4 成功响应即时保存 | main 的 runtime 已要求保存/复用响应，跳过重复规则改造；实际 prepare-input.py 只有解析后的 issue.json，缺原响应和抓取元信息。本轮在解析和复制材料前写每次独立 fetches/issue-* 的 stdout.raw、stderr.log、fetch.json（argv/cwd/UTC开始结束/exit）；非零退出、解析失败和入口不可用均保留对应证据。显式重读保留以前尝试，不覆盖旧成功 issue.json。runtime 更新为完整即时保存并说明助手证据位置。 |

第 4 项保留既有 issue.json 格式、材料路径边界和原生依赖另读职责，不新增自动网络重试、认证设置或恢复预算。助手显式调用仍会读取最新 issue，宿主同一准备过程复用已成功输入，按现有 freshness 要求决定重读。未选项与受阻实施项：无。

## 验证与证据

命令 cwd 均为上述维护工作树，Python 使用已确认 `E:/Miniconda3/python.exe`。项目内 `.scratch/evidence/` 保留原始结果与模拟响应。

- `python .scratch/check-preparation.py`：exit 0，四组场景通过。成功响应保留原字节、argv、时间和 exit 0；后续 TLS 失败 exit 1 保留旧成功原响应与 issue.json；成功传输但非法 JSON 保留原字节且不产出成功输入；材料路径越界失败发生前响应已保存；缺 gh 则记录 exitCode=null 和入口错误。这些检查使用模拟 gh，无网络访问。
- `python .scratch/check-review.py`：检查三份文档本地链接；沿实际 schema、快照和提取脚本应用前三项指引，引用历史精确独立证据（两步 exit 0、stopped=true，业务与清理通过）。只读 Docker 查询确认本票容器/网络为空、数据卷保留。详见 review-results.json；不是本轮新跑业务验收。
- `git diff --check`：exit 0。三端路径 `git diff --exit-code`：exit 0。运行时/业务源码不变，没有重复 lifecycle、Maven 或整套统一 verifier；本轮助手行为由针对性模拟场景验证。

核对脚本首次读取历史中文 JSON 时使用 Windows 默认 GBK，exit 1；改为显式 UTF-8 后重跑通过。这是本轮临时核对脚本错误，不是业务失败，保留于 `.scratch/evidence/review-first-error.txt`。

技能变更应用场景以真实历史失败和修正后的 schema/探针为依据；本轮没有重新调用模型进行压力测试，不保证未来模型必然遵循提示。首次原生依赖读取的完整历史响应/精确时间仍缺失，本轮无法补造。未修改原执行报告，以免把当时的“未实施”事实改写为历史已实施。

## 资源与恢复

未创建容器、网络或数据卷，未接触其他任务资源。编码/独立运行已经停止，人工服务最终交付时已停止；本轮只读核对。恢复审查从本维护树 Git diff、本报告和 `.scratch/evidence/` 开始。撤销本轮三份准备文件即可回退，无需迁移数据；保留旧原始证据。后续 PR、精确候选、合并提交和同步结果记录在本维护树 `.scratch/evidence/delivery-results.json`，以远端回读为准。
