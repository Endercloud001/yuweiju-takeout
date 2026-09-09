# 余味居外卖系统协作规则（AGENTS.md）

## 目录地图
- `yuweiju-backend/`：Spring Boot + MyBatis Plus 后端仓库，承载业务控制器、服务、Mapper 及共享组件库。
- `yuweiju-web-vue/`：管理端（Vite 7 + Vue 3.5 + TypeScript + Pinia + Element Plus），通过 REST API 操作后台。
- `yuweiju-weixin-miniapp/`：微信小程序用户端，面向消费者的点餐体验。
- `yuweiju-document/api/`、`yuweiju-document/db/`、`yuweiju-document/execplans/`：接口、数据库与 ExecPlan 文档库。
- 根目录的 `AGENTS.md`、`PLANS.md` 和 `.codex/config.toml` 共同定义协作与计划标准。

## 工作模式
1. 修改接口或数据库前先读新旧 API 文档（`yuweiju-document/api`）、数据库设计稿（`yuweiju-document/db`）和 To-do List（`yuweiju-document/execplans/余味居 外卖项目 To-do List.md`），再写 ExecPlan。Plan 最终必须遵守 `PLANS.md`。
2. 任何涉及行为或架构调整前，概览相关子项目的 `.trae/rules`：后端用 `yuweiju-backend/.trae/rules/rules.md`，管理端用 `yuweiju-web-vue/.trae/rules/project_rules.md`。规则为该子项目的最底层约束，需在 plan 与 PR 中引用/说明如何遵守。
3. ExecPlan、开发、测试和文档同步必须涵盖三端；若只动一端，请在 plan 中写明为何可以局部调整并说明影响面。

## 参考资源
- 后端 `.trae` 规则：`yuweiju-backend/.trae/rules/rules.md`（包含 Java 风格、架构划分、日志及异常说明）。
- 管理端 `.trae` 项目规则：`yuweiju-web-vue/.trae/rules/project_rules.md`（包括目录结构、UI 资源、接口约定）。
- ExecPlan To-do：`yuweiju-document/execplans/余味居 外卖项目 To-do List.md`（记录当前重点需求与待办）。以上三份文件必须在涉及对应领域时复读并归档到 plan/PR Remark。

## Guardrails
- 不可随意重命名接口字段或破坏数据库兼容性，除非 ExecPlan 明确列出迁移步骤与回退路线。
- 任何新增 `TODO` 必须写明所属模块和目的，且定位到 plan/issue id。
- 跨端改动必须说明影响范围：例如后端接口变更需注明对管理端/小程序的调用点。

## Approach
- Think before acting. Read existing files before writing code.
- Be concise in output but thorough in reasoning.
- Prefer editing over rewriting whole files.
- Do not re-read files you have already read.
- Test your code before declaring done.
- No sycophantic openers or closing fluff.
- Keep solutions simple and direct.
- User instructions always override this file.
