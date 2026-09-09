# 立即清理并轮换敏感凭据 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans`（或 `superpowers:subagent-driven-development`）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 从代码仓库和构建产物中移除真实凭据，轮换已经暴露的数据库、OSS、微信支付、地图和大模型密钥，并让三端在没有本地密钥文件时仍能通过明确配置启动或安全失败。

**架构：** 保留 Spring Boot 现有 `SkyProperties`、`AliOssProperties` 和 JWT 配置作为配置入口，改为由环境变量或部署平台 Secret 注入；开发者只提交脱敏的 `.env.example`。凭据迁移采用“先发新密钥、切换、验证、撤销旧密钥”的双凭据窗口，避免线上支付和图片服务中断。

**技术栈：** Spring Boot externalized configuration、Maven、Gitleaks、GitHub Secret Scanning/Push Protection、Docker/CI Secret、阿里云 RAM、微信支付商户平台、百度地图控制台、DeepSeek/OpenAI-compatible API。

---

## Purpose / Big Picture

当前 `yuweiju-backend/src/main/resources/application-dev.yml` 含有数据库密码、阿里云 OSS AccessKey、微信支付密钥、百度地图 AK，`application.yml` 还包含一个带默认值的 AI API Key。任何获得仓库或构建产物的人都可能直接访问外部资源。完成本计划后，代码仓库只保存变量名和示例占位符；开发、测试、生产通过环境变量或 Secret 注入；启动时缺少关键生产配置会明确失败，而不是使用危险默认值。

本计划遵守根目录 `AGENTS.md` 的凭据外置和跨端影响要求，并参考 `gitleaks/gitleaks` 的仓库扫描能力。该计划只处理凭据与配置，不改变业务接口字段；因此管理端和小程序只需验证其 API Base URL 与登录流程未受影响。

## Progress

- [ ] 盘点当前代码、构建产物、Git 历史和部署环境中的凭据。
- [ ] 在外部平台创建新凭据并完成双凭据切换。
- [ ] 清理配置文件、样例文件和构建产物，加入扫描门禁。
- [ ] 增加配置校验、启动检查和最小回归测试。
- [ ] 完成旧凭据撤销、泄露确认和恢复演练。

## Surprises & Discoveries

- 发现 `application-dev.yml` 存在可直接使用的真实凭据，证据为 `sky.datasource.password`、`sky.alioss.access-key-secret`、`sky.wechat.apiV3Key` 等字段。
- 发现 `application.yml` 的 `spring.ai.openai.api-key` 具有 `sk-...` 默认值；默认值即使意图是开发方便，也会在错误配置时变成生产泄露源。
- 根目录没有可用的 Git 元数据时，不能假设历史清理已经完成；必须在实际仓库镜像和远端平台上分别确认扫描结果。

## Decision Log

- **Decision：** 先轮换外部凭据，再删除工作区明文。**Rationale：** 先删除代码会留下旧凭据仍然有效的窗口。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 不把 Secret 写入新的 Docker Compose、Maven profile 或 `.env.example`。**Rationale：** 示例文件只允许变量名和无效示例值。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 首阶段不改变 JWT Token 字段、数据库字段或 API 响应结构。**Rationale：** 凭据治理不应制造跨端兼容风险。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 任务 1 一旦确认凭据曾出现在 Git 历史、GitHub push 或构建产物中，立即标记为“已泄露”并启动轮换，不等待扫描工具再次命中。**Rationale：** 已被推送的 Secret 可能已被爬虫、Fork 或第三方 Secret Scanning 获取。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 微信支付轮换严格区分商户 API 证书、API v3 密钥、微信支付平台证书/公钥和商户私钥文件；不把“商户主动更换”和“微信侧证书获取/平滑更新”当作同一个操作。**Rationale：** 商户 API 证书用于请求签名，平台证书或微信支付公钥用于响应/回调验签。**Date/Author：** 2026-08-08 / Codex。

## Outcomes & Retrospective

完成后应能证明：扫描工作区和提交历史不再发现有效凭据；生产启动只使用外部 Secret；旧凭据已撤销；管理端和小程序仍能完成登录、菜品图片读取、下单支付沙盒回归。若历史仓库无法重写，必须在远端平台保留“已泄露、已轮换”的安全事件记录，而不是把删除当前文件误认为历史清理完成。

## Context and Orientation

后端配置位于 `yuweiju-backend/src/main/resources/application.yml` 和 `application-dev.yml`，配置映射类位于 `yuweiju-backend/src/main/java/com/codeying/properties/`，启动类为 `com.codeying.App`。OSS、微信支付、百度地图和 AI 客户端分别在 `utils/`、`service/impl/` 和 `config/` 使用配置。管理端以 `yuweiju-web-vue/yuweiju-admin/src/api/http.ts` 读取 `VITE_API_BASE` 并发送 Token；小程序相关业务页面目前自带 `baseUrl` 和 Token 读取逻辑。`target/`、管理端 `dist/` 和小程序编译生成目录都不能成为凭据的备份位置。

## Plan of Work

### 任务 1：建立凭据清单与证据快照

**文件：**

- 读取：`yuweiju-backend/src/main/resources/application.yml`
- 读取：`yuweiju-backend/src/main/resources/application-dev.yml`
- 读取：所有 `*.yml`、`*.yaml`、`.env*`、CI 文件和部署脚本
- 创建：`yuweiju-document/security/credential-inventory.md`

- [ ] 用 `rg -n "password|secret|api-key|access-key|token|privateKey|ak:"` 扫描三端和文档，记录变量名、用途、当前载体、轮换方式和影响接口；文档中禁止记录真实值。
- [ ] 用 `gitleaks detect --source . --redact --report-format sarif --report-path gitleaks.sarif` 扫描工作区；对每个结果记录文件路径、规则名和是否需要历史扫描。
- [ ] 读取远端仓库的保护设置并记录是否启用 Secret Scanning、Push Protection、分支保护和环境审批。
- [ ] 一旦发现任一凭据存在于 `.git` 历史、GitHub push、Fork、构建缓存或日志，立即在清单中标记“已泄露”，停止等待型讨论并转入任务 2 轮换；即使当前工作区已删除也必须执行。

### 任务 2：轮换外部凭据

**外部操作：** 阿里云 RAM/OSS、微信支付商户平台、百度地图控制台、DeepSeek/OpenAI-compatible 服务、MySQL/Redis 部署环境。

- [ ] 为数据库用户创建最小权限的应用账号；先将新密码写入部署 Secret，不写入仓库。
- [ ] 创建新的 OSS RAM AccessKey，并限制到目标 Bucket 与必要动作；切换后验证上传、默认图片列表和读取 URL。
- [ ] 分别登记并处理微信支付四类材料：商户 API 证书/私钥（商户主动申请、部署和在过期前更换，用于请求签名）、API v3 密钥（用于回调敏感字段解密）、微信支付平台证书或微信支付公钥（微信侧提供/更新，用于响应和回调验签）以及证书序列号。不要用商户 API 证书验微信回调，也不要把 API v3 密钥当作证书。
- [ ] 如果采用平台证书模式，调用官方 `/v3/certificates` 下载接口，以 API v3 密钥解密证书，按 `Wechatpay-Serial` 选择证书，并保留旧证书与新证书的重叠验证窗口；如果采用微信支付公钥模式，单独管理公钥 ID 和更新记录，不混用两种模式。
- [ ] 先部署新的商户 API 证书/私钥和 API v3 密钥，再验证请求签名、回调解密、响应验签、退款回调和证书序列号；确认新平台证书/公钥能够验签后再撤销/移除旧商户材料。
- [ ] 优先复用微信支付官方 Java 工具库或当前已引入的官方兼容组件处理签名、验签、证书加载和回调解密；只有在工具库无法覆盖当前模式时才增加自定义适配，并为自定义代码写证书序列号和探测签名测试。
- [ ] 为百度地图和模型供应商创建新 Key，限制来源、配额和模型权限；用一条脱敏健康检查验证调用。
- [ ] 双凭据验证通过后撤销旧凭据，并保存外部平台操作编号或截图索引到安全系统，不保存密钥本身。

### 任务 3：改造配置注入与安全失败

**文件：**

- 修改：`yuweiju-backend/src/main/resources/application.yml`
- 修改：`yuweiju-backend/src/main/resources/application-dev.yml`
- 修改：`yuweiju-backend/src/main/java/com/codeying/properties/*.java`
- 创建：`yuweiju-backend/src/main/resources/application-local.example.yml`
- 创建：根目录或部署目录的 `.env.example`

- [ ] 删除所有真实值和 AI Key 默认值，将必填项写成无默认值的 `${...}`；本地示例只使用 `CHANGE_ME`，并明确不能用于生产。
- [ ] 为 `AliOssProperties`、微信支付配置、地图配置、AI 配置和数据库配置增加 `@Validated` 的 `@NotBlank`；将文件路径改为环境变量，不允许提交本机绝对路径。
- [ ] 对 profile 做明确拆分：`application.yml` 只放非敏感共性配置，`application-local.example.yml` 只作为复制模板，真正的 `application-local.yml` 加入忽略规则。
- [ ] 增加启动配置检查，错误信息只指出变量名，不打印变量值；日志中对 URL、用户名和 Key 使用脱敏形式。
- [ ] 为 AI Key 增加主动配置审计：启动时记录当前 `modelProfile`、Key 指纹末四位和供应商，不记录完整 Key；检查 Spring AI provider、LiteLLM/网关、旧 `AiModelServiceImpl`、fallback profile 和 CI Secret 是否都已切换到新 Key。

### 任务 4：加入持续扫描和历史治理

**文件：**

- 创建：`.gitleaks.toml`
- 创建：`.github/workflows/secret-scan.yml`
- 修改：`.gitignore`
- 可能修改：各仓库的远端保护设置

- [ ] 固定 Gitleaks Action 的版本并在 Pull Request、主分支推送和手工触发时运行；失败时上传脱敏 SARIF，不上传原始扫描文件。
- [ ] 将 `.env`、私钥、证书、`target/`、`dist/`、运行时模型目录和本地导出文件加入忽略规则；确认忽略规则不会隐藏需要提交的模板。
- [ ] 如果历史扫描命中，先在镜像仓库备份，再由仓库管理员使用 `git filter-repo` 或平台提供的历史清理流程；强制所有开发者重新克隆并重新轮换仍可能暴露的凭据。
- [ ] 若任务 1 已确认历史或 GitHub push 出现过明文，则本任务不能以“当前扫描未命中”为通过条件；必须完成远端历史清理、所有 Fork/构建缓存核查、Secret Scanning 关闭告警和旧凭据撤销记录。

### 任务 5：验证与恢复演练

- [ ] 在无 Secret 的干净环境运行 `mvn -q -DskipTests package`，确认应用在生产 profile 下以可读错误退出。
- [ ] 使用新 Secret 启动后端，验证管理员登录、用户登录、OSS 默认图片列表、AI 助手、下单支付沙盒和退款回调。
- [ ] 先用新 AI Key 验证模型调用，再撤销旧 Key；在撤销后重复 AI smoke test，并检查没有重试、fallback、LiteLLM virtual key、Spring profile 或容器旧环境变量继续使用旧 Key。
- [ ] 在三端执行管理端 `npm run lint && npm run typecheck && npm run test`，小程序使用微信开发者工具编译并走登录、菜品、购物车、客服页面。
- [ ] 暂时撤销一个非核心外部 Key，确认服务显示可理解的降级错误且不泄露 Secret；恢复后再次验证。

## Concrete Steps

工作目录为 `E:\Learning Files\yuweiju-takeout`。只使用 PowerShell 读取和编辑文件，执行任何历史重写或外部撤销前必须保存审计记录。推荐命令：

    rg -n "password|secret|api-key|access-key|token|privateKey|ak:" . --glob '!**/target/**' --glob '!**/dist/**' --glob '!**/node_modules/**'
    gitleaks detect --source . --redact --report-format sarif --report-path gitleaks.sarif
    mvn -q -f yuweiju-backend/pom.xml -DskipTests package
    npm --prefix yuweiju-web-vue/yuweiju-admin run lint
    npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck

预期结果是扫描退出码为 0，或只剩明确登记的无效测试字符串；生产 profile 缺 Secret 时启动失败且日志只显示变量名。

## Validation and Acceptance

验收必须同时满足：工作树和历史扫描无有效密钥；配置文件不含真实 Secret；任何历史/GitHub push 暴露都被直接按已泄露处理；新凭据能支撑登录、OSS、支付沙盒、地图和 AI 最小链路；微信支付商户 API 证书、API v3 密钥、平台证书/公钥分别完成验证；旧凭据在外部平台状态为 revoked/disabled；AI 旧 Key 撤销后不存在隐藏重试或 fallback 使用；CI Pull Request 会阻止新 Secret 合入；缺配置时服务安全失败；管理端和小程序 API 行为没有字段变化。

## Idempotence and Recovery

扫描、配置校验和构建可重复执行。凭据轮换不可通过代码回滚旧密钥；若新凭据验证失败，继续使用旧凭据仅限双凭据窗口，并在安全负责人批准后处理。历史重写前必须保留不可公开的备份；重写后所有协作者重新克隆。支付证书和数据库密码的恢复依赖外部平台的备份与审计记录。

## Artifacts and Notes

计划完成后应产生脱敏的 `yuweiju-document/security/credential-inventory.md`、`.gitleaks.toml`、Secret Scan 工作流、`.env.example` 和配置校验测试。任何日志、报告或截图都不得包含完整 Token、密码、私钥、AccessKey 或支付密钥。

## Interfaces and Dependencies

配置接口保持现有变量语义：`SKY_DATASOURCE_*`、`SKY_ALIOSS_*`、`SKY_WECHAT_*`、`SKY_BAIDU_MAP_AK` 和 `DEEPSEEK_API_KEY`；实际命名以 Spring 属性绑定实现统一。外部工具和参考：

- [Gitleaks](https://github.com/gitleaks/gitleaks)：检测密码、API Key、Token，并可接入 pre-commit/GitHub Action。
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)：环境变量和 profile 注入。
- [微信支付 API v3 平台证书验签](https://pay.wechatpay.cn/doc/v3/merchant/4013053420)：`Wechatpay-Serial`、平台证书验签和回调失败处理。
- [微信支付 API v3 平台证书下载](https://pay.wechatpay.cn/doc/v3/merchant/4012551764)：平台证书列表获取与平滑更换。
- [微信支付 API v3 概述](https://pay.wechatpay.cn/doc/v3/merchant/4012081606)：商户 API 证书、平台证书/公钥和 API v3 密钥的职责区别。
- [微信支付官方工具库说明](https://pay.wechatpay.cn/doc/v3/merchant/4012081606)：优先复用官方 Java/Go 工具库封装的密钥加载、签名、验签和请求头能力。
- [AI 运营化计划](./ExecPlan%20-%20把%20AI%20从“同步调用模型”升级为可运营系统.md)：旧 Key 撤销后的模型 profile/fallback 交叉验证。
- [余味居后端配置](../../yuweiju-backend/src/main/resources/application.yml)：当前配置入口。
