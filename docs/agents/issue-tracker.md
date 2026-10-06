# Issue tracker: GitHub

本仓库的需求、规格和决策记录在 `Endercloud001/yuweiju-takeout` 的 GitHub Issues。使用 gh CLI，显式指定 `--repo Endercloud001/yuweiju-takeout`。复杂实施方案可放 `yuweiju-document/execplans/` 并在 Issue 中链接；按 [workflow](workflow.md) 说明范围、三端影响与验证，历史计划不自动授权实施。

## 操作约定

- 创建：`gh issue create --repo Endercloud001/yuweiju-takeout --title "..." --body-file <文件>`。多行正文写入 UTF-8 文件。
- 读取：`gh issue view <编号> --repo Endercloud001/yuweiju-takeout --comments`。
- 列表：`gh issue list --repo Endercloud001/yuweiju-takeout --state open --json number,title,labels`；按需读取正文、评论。
- 评论：`gh issue comment <编号> --repo Endercloud001/yuweiju-takeout --body-file <文件>`。
- 标签：`gh issue edit <编号> --repo Endercloud001/yuweiju-takeout --add-label "..."` 或 `--remove-label "..."`。
- 关闭：`gh issue close <编号> --repo Endercloud001/yuweiju-takeout --comment "..."`。

技能要求 publish to the issue tracker 时创建 Issue；要求 fetch the relevant ticket 时读取 Issue 及评论。Issue 和 PR 共用编号空间，遇到歧义先确认对象类型。写入 GitHub 前遵循当前任务授权；连通性检查使用读取和推送 dry-run，实际创建 Issue/PR 是独立操作。

## Pull requests as a triage surface

**PRs as a request surface: no.** 若维护者以后改为 yes，triage 才把外部 PR 纳入请求分流。

## Wayfinding operations

wayfinder 使用一个地图 Issue 和其子 Issue 记录决策。地图标签为 `wayfinder:map`，子项标签为 `wayfinder:research`、`wayfinder:prototype`、`wayfinder:grilling`、`wayfinder:task`；使用时检查并创建所需标签。

优先使用 GitHub 子 Issue 和原生依赖。依赖命令为 `gh api --method POST repos/Endercloud001/yuweiju-takeout/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`；数据库 id 通过 `gh api repos/Endercloud001/yuweiju-takeout/issues/<编号> --jq .id` 获取。仅在 API 不支持时，改用地图任务列表、子项顶部 `Part of #<map>` 和 `Blocked by: #<编号>`。

推进无未关闭阻塞项且无人认领的子项。通过 `gh issue edit <编号> --repo Endercloud001/yuweiju-takeout --add-assignee @me` 认领，记录决定后关闭，再更新地图中的决定和来源链接。

## 本机连通性

2026-10-05 检查时，gh 默认 HTTP/2 请求出现 TLS handshake timeout；在单次 PowerShell 会话设置 `$env:GODEBUG='http2client=0'` 后读取成功。只在复现此问题时使用该设置；若已有 GODEBUG 配置，保留其他选项。使用系统凭据库，不将 Token 写入仓库。
