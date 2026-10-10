# Issue #8 主线交付记录

2026-10-10，维护者调用 yuweiju-deliver，沿用本聊天必要提交、推送、PR、合并、同步和关闭授权；范围为GitHub main代码交付。候选64d5622的五组业务人工验收通过，分类提示文字的补充修复已通过22项浏览器断言和管理端四项检查。调用交付技能表示继续交付修复结果；没有新增真实微信验收或部署声明。

基于已实时fetch且与GitHub一致的main `e6d62a7379b505713f182caafa45e14ed3c65fcb`，在项目 `.scratch/issue8-deliver-20261010` 创建独立分支 `codex/issue8-integration-20261010`。仅cherry-pick业务/记录提交b3e351f、64d5622（对应0af28af、6895104），无冲突，再加入单行CSS修复和本票报告/合成截图。原工作区分支、AGENTS等未提交修改保持。

源码候选 `957d247af0ba8cec8d2938faad01bfc6ee8fa4d2`：分类、菜品、口味、套餐目录条件具名Mapper，Service保持规则和代理事务；用户分类入口移除Wrapper；异常保留cause。分类弹窗focus/has-value状态opacity从0.72改0。API/schema/原数据/两端外观保持，小程序无需源码更改。

证据复用按源码而非未经核对的历史成功：`git diff --exit-code 64d5622 957d247 -- yuweiju-backend .sandcastle/environment/Issue8Probe.java .sandcastle/environment/issue8-check.sh .sandcastle/environment/issue8-browser.cjs` 为0，故精确64d5622的后端54tests、158真实SQL/Spring/HTTP/Redis断言和4项浏览器结果适用。其无认证/无模型独立记录为本机 `.scratch/issue8-afk-20261010/independent/task-review-1791609941495677730/resources.json`，全部检查通过且容器停止。UI修复工作树与957d247全部管理端源码的Git差异为0，22项computed-style/实际弹窗截图验证可复用（`.scratch/issue8-ui-fix-20261010`）。未机械重跑数据库探针或原库。

最终集成候选使用main统一Windows/Linux verifier另行执行管理端新npm ci、lint/typecheck/test/build，绑定957d247、UID1000、UTF-8、无认证挂载/无模型；配置/证据在 `.scratch/issue8-delivery-check-20261010`。后续报告提交只增补文档，源码未改变；准确结果及审查结论在下方补记。

维护者报告1/3/4/5通过，2业务正常且指出显示缺陷。新分类/套餐弹窗输入后提示隐藏、失焦/清空/排序0已验证；[修复前](verification-evidence/2026-10-10/issue8-ui/before.png)、[菜品分类修复后](verification-evidence/2026-10-10/issue8-ui/after-dish.png)、[套餐分类修复后](verification-evidence/2026-10-10/issue8-ui/after-setmeal.png)均为浏览器合成数据截图，没有原账号或认证内容。自动检查不能冒称真实微信身份交换，隔离小程序采用dev mock登录。

本票人工服务已按要求停止，五容器/两网络移除，卷保留；显示测试临时容器也已退出，无服务重启。执行报告历史各段的状态按日期/阶段区分，后续更新不覆盖先前失败证据。完整记录见 [执行报告](issue%238-executing-report.md)。

回退可正常revert本票提交，无schema迁移；不自动回滚已提交目录/缓存副作用。三份历史JSON全项目搜索仍缺失，已获替代证据授权并补充本轮结果。没有Release、服务器部署、原库写入或真实微信配置变化。PR合并/关闭/本地引用同步以平台回读为准，最终结果会补记到本机同名交付报告及issue结论。

## 最终候选验证与审查

统一verifier对957d247的五项管理端命令全部exit 0，independentVerification=passed、authMounted=false、modelCalled=false、stopped=true。证据目录为本机 .scratch/issue8-delivery-check-20261010/evidence/task-review-1791615511949989198/，原始resources.json与check日志保留。

code-review并行规格/规范审查固定范围e6d62a7..957d247，未发现实质源码问题；规范审查发现两份报告的历史状态表述过时，本次仅同步文档。审查没有代替实际测试。最终补记提交仅改文档，管理端及后端源码继续分别绑定上述验证。
