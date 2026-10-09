# 余味居协作入口

## 目录导航

- `yuweiju-backend/`：单 Maven 后端，Java 源码 `src/main/java/com/codeying/`。
- `yuweiju-web-vue/yuweiju-admin/`：Vue 管理端实际工程。
- `yuweiju-weixin-miniapp/`：小程序，含编译产物与手写页面。
- `docs/agents/`：协作流程；`docs/standards/`：三端技术规范；`.agents/skills/`：项目技能。
- `yuweiju-document/api/`、`yuweiju-document/db/`：接口/数据库资料；`yuweiju-document/execplans/`：已有历史计划。

## 按任务读取

- 实施、验证或编写方案：读 [workflow](docs/agents/workflow.md)，说明三端影响，局部调整说明其他端为何无需修改。
- 修改某端：读该端 AGENTS 和 docs/standards 对应规范；详细技术要求在该规范单一维护。
- 探索业务/架构：读 [domain](docs/agents/domain.md)，再按领域读 GLOSSARY 和 ADR。
- 修改接口/数据库：读相关新旧 API、数据库设计与当前 issue；旧 To-do 当前不存在，需求以已确认 issue 为准。
- GitHub Issues：读 [issue-tracker](docs/agents/issue-tracker.md)；分流另读 [triage-labels](docs/agents/triage-labels.md)。

## 项目文件输出

- 本项目任务产生的文档、脚本、日志、截图、临时文件和其他产物，统一写入 `E:\Learning Files\yuweiju-takeout` 内；临时产物放入项目根目录的 `.scratch/`。
- 执行写入命令前确认实际工作目录与目标绝对路径均在项目目录内。不得自行在父目录、桌面、用户目录或外部 worktree 中创建项目文件。

## 必要边界

- 保留用户变更与已有安全措施；认证、数据安全、不可逆操作、正式发布按项目要求和任务授权处理。
- 保持接口、数据库与原数据兼容；必要调整先说明消费者、迁移和回退。
- 日志及文档脱敏，不提交密码、密钥或令牌。含原库写入的脚本先查副作用，失败测试使用隔离环境。
- 新增 TODO/FIXME 写明模块、目的和关联 issue/方案。
- 默认不新增 hash、冻结 contract、baseline 或 gate；只有具体失败场景且 Git、版本号、主键、事务、唯一约束、类型和普通测试不足时才考虑。
