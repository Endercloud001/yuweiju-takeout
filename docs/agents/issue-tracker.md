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

## 超时后的读取与 REST 后备

GraphQL 失败后可使用 `gh api` 调用 REST；REST 也可能 TLS 超时，HTTP/2 设置不保证解决。每次保留操作、目标、退出状态及成功返回对象的必要字段。写入超时可能已在远端成功，先读再决定是否重试；读取也失败时保留未确认状态，停止写入重试。下列写入示例只用于任务已授权的操作，连通性测试仅使用 GET。

PowerShell 设置 `$repo = 'Endercloud001/yuweiju-takeout'`，正文/request 文件放项目 `.scratch/`，UTF-8 JSON 保留真实换行。发请求前核对 `git remote get-url origin`、分支、`git rev-parse HEAD`，远端 PR 的 head.sha 必须与已审查候选一致。按操作选择：

```powershell
# 创建 PR 前及超时后：查所有状态，匹配 head 分支、base 和仓库，防止重复创建。
gh api --method GET "repos/$repo/pulls" -f state=all -f head='Endercloud001:codex/具名分支' -f base=main --paginate --jq '.[] | {number,state,head:{ref:.head.ref,sha:.head.sha,repo:.head.repo.full_name},base:.base.ref,html_url}'
# pr-request.json 为 {"title":"...","head":"codex/具名分支","base":"main","body":"..."}
gh api --method POST "repos/$repo/pulls" --input .scratch/pr-request.json --jq '{number,html_url,head:.head.sha}'

# 评论前及超时后：分页读取，核对作者、完整正文及本次操作时间。
gh api "repos/$repo/issues/13/comments" --paginate --jq '.[] | {id,author:.user.login,body,created_at,html_url}'
# comment-request.json 为 {"body":"..."}；仅在读取确认尚无本次评论时发出。
gh api --method POST "repos/$repo/issues/13/comments" --input .scratch/comment-request.json --jq '{id,html_url}'

# 合并前及超时后：同时确认 head、merged、merge_commit_sha。
gh api "repos/$repo/pulls/具名PR编号" --jq '{number,state,merged,head:.head.sha,base:.base.ref,merge_commit_sha}'
# merge-request.json 为 {"sha":"已审查完整候选SHA","merge_method":"merge"}
gh api --method PUT "repos/$repo/pulls/具名PR编号/merge" --input .scratch/merge-request.json --jq '{merged,sha,message}'

# 关闭 Issue 前及超时后读取；state 已 closed 时无需再关闭或重复评论。
gh api "repos/$repo/issues/13" --jq '{number,state,state_reason,pull_request}'
gh api --method PATCH "repos/$repo/issues/13" -f state=closed --jq '{number,state,state_reason}'
```

PR 查询发现已有匹配对象就复用；已关闭未合并的对象先核对原因，不能自动重新创建。评论没有通用幂等键，存在相同正文或状态不明时先核对，不盲目 POST。合并必须核对返回 `merged=true`，并再次 GET 确认远端 merged/head/merge_commit_sha；HTTP 成功、CLI exit 0 或本地分支存在均不单独证明完成。接口字段见 GitHub 官方 [PR REST](https://docs.github.com/en/rest/pulls/pulls) 和 [评论 REST](https://docs.github.com/en/rest/issues/comments)。

Git HTTPS 推送的 DNS 路由故障另行排查，不将 API 超时直接归因 DNS。仅在已确认解析/路由问题时，使用 `Resolve-DnsName github.com -Type A` 重新查询本次地址，选择当前有效地址；先 `git -c "http.curloptResolve=github.com:443:<本次IP>" push --dry-run origin HEAD:refs/heads/<已授权分支>`，再按既有授权执行真实推送并用 `git ls-remote` 核对 head。此配置只用于单次命令，保持 HTTPS 主机名与证书校验；不写 hosts/global Git，不固定历史 IP，不设置 sslVerify=false。若临时设置 GODEBUG，在 finally 恢复先前值。
