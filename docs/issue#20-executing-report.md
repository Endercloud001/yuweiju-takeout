# Issue #20 执行报告

更新：2026-10-13（Asia/Shanghai）。

## 当前状态

代码候选 `0b2e0be3e65674f17ff2d4f59fd40e5997cf13db` 已通过准确提交的独立验证，真实OSS及本机浏览器验收准备已通过；用户已确认菜品、套餐、上传、外观全部通过；四项Acceptance criteria均满足，已进入授权交付。本段为合并前记录，最终PR/合并/关闭/服务状态另存本地交付报告及Issue关闭评论。人工步骤见 `docs/issue20-human-acceptance.md`。

**AFK监督器状态仍为 aborted / timeout**：最大两轮、编码总计3600000ms、独立verificationMs1200000ms、预算内closing180000ms；实际编码开始 2026-10-12 23:09:38 +08:00，截止2026-10-13 00:09:38 +08:00，停止记录完成 2026-10-13 00:10:46 +08:00。后者包含截止后的资源回收，未授权延长编码或重启。最终结果result=null、进程退出1、failure=`Error: AFK total time limit reached`，不能因日志里出现完成标记或候选通过验证将此次Loop标成成功。

第一轮创建实现提交 `2a3ec2afbef785d68dddaae0802c437803a83f50` 及记录提交 `41af2c0003d896a96e78d5957eb86abab9a87ba3`。第一轮agent exit0、工作树干净，但completionTextObserved=false（输出普通COMPLETE而非精确标记），监督器进入第二轮。第二轮误查新工作树而认为旧检查日志缺失；宿主核实 `/afk-evidence/iteration-1/files/.scratch/issue20/` 可读，在closing保留原字段/截止并补充路径更正及精确标记。第二轮复用相同代码证据，停止冗余检查并保存 `ca04edd379265550e3ccc0e1402e1948f3fff429` / `0b2e0be3e65674f17ff2d4f59fd40e5997cf13db` 进度提交；与41af2c0相比只增加16行进度，无应用/测试/探针改变。精确标记出现但agent进程未在截止前结束，超时照常取消。没有第三轮或AFK续跑。

## 范围、职责与三端影响

- 后端：AdminCommonController保留协议/验证/响应，UploadService/Impl承担原有格式、大小、命名和存储规则；保留png/jpg/jpeg/bmp、5MiB默认及20MiB硬上限、dir/scene/maxSize、鉴权和响应schema/code。
- 默认库来自配置OSS对象，不是SQL；Service保留scene/prefix/allowedExtensions规则，SDK工具按marker分页、过滤后累计结果上限，关闭流与客户端、保留异常cause，友好失败且不输出上游正文/凭据。修复非图片挤占名额及对象路径编码；没有机械新建Mapper、素材平台或存储迁移。
- 管理端：菜品/套餐上传增加运输错误提示，同样token、VITE_API_BASE、code===1、布局与素材；新增真实Element Plus/DOM/XHR组件行为测试。
- 小程序源码未修改；当前商品图片字段/URL仍兼容，两端商品接口与历史订单/明细图片通过真实隔离SQL/HTTP核对。没有改写历史图片、原库/schema/原ID/认证、依赖版本或新增hash/contract/baseline/gate。

任务15文件：

```
.sandcastle/environment/Issue20Probe.java
.sandcastle/environment/issue20-check.sh
.sandcastle/task-progress.md
yuweiju-backend/src/main/java/com/codeying/controller/admin/AdminCommonController.java
yuweiju-backend/src/main/java/com/codeying/service/UploadService.java
yuweiju-backend/src/main/java/com/codeying/service/impl/DefaultImageLibraryServiceImpl.java
yuweiju-backend/src/main/java/com/codeying/service/impl/UploadServiceImpl.java
yuweiju-backend/src/main/java/com/codeying/utils/AliOssUtil.java
yuweiju-backend/src/test/java/com/codeying/controller/admin/ImageProtocolTest.java
yuweiju-backend/src/test/java/com/codeying/service/impl/ImageBusinessTest.java
yuweiju-backend/src/test/java/com/codeying/utils/AliOssResourceTest.java
yuweiju-document/execplans/issue20-upload-default-images.md
yuweiju-web-vue/yuweiju-admin/src/views/dish/add.vue
yuweiju-web-vue/yuweiju-admin/src/views/image-workflow.test.ts
yuweiju-web-vue/yuweiju-admin/src/views/setmeal/add.vue
```

## 独立验证（与代理检查及监督器状态分开）

runtime/main/startCommit=`fcf5d28a8320efd5d209671b0814257de11a360b`，项目内runtime干净；Ubuntu、Node22.23.3、Git-safe image、专属认证、双网络及本票MySQL/Redis预检exit0。模型沿用gpt-6.1-sol / medium。root工作区既有修改未覆盖。

准确候选独立入口为所选runtime `.sandcastle/verify-task.py --config <task>/config.json --commit 0b2e0be3e65674f17ff2d4f59fd40e5997cf13db`，WSL Ubuntu；快照 `/mnt/e/Learning Files/yuweiju-takeout/.scratch/issue20-afk-20261012/independent/task-review-1791821508263417671/snapshot`，不挂认证、不调用模型。三组命令从快照根/管理端执行，均exit0，resources.independentVerification=passed、stopped=true：

1. `mvn -B -f yuweiju-backend/pom.xml compile && mvn -B -f yuweiju-backend/pom.xml package`：XML统计98 tests，0failure/error/skip。
2. 管理端 `npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm run test && npm run build`：17测试通过，所有子命令成功。
3. `bash .sandcastle/environment/issue20-check.sh`：250具名断言，businessPassed=true、cleanupPassed=true；真实Spring/MyBatis/MySQL/Redis、真实OSS SDK本地HTTP替身，不是原OSS或原库。schema/主键/约束先只读核对，真实鉴权/SQL持久化/历史图片/资源关闭分别断言。

代理失败检查批次分别保留：Mockito循环调用计数夹具、TypeScript必填msg、OSS XML夹具Owner metadata、MockMvc builder链顺序；修正夹具/调用并重跑，未删除断言。批次5/6最终检查成功；第二轮批次7compile实际通过，其package/admin流水线主动停止、probe未重跑，明确不记作成功。Ponytail完整差异/调用者/测试/配置复查、采纳/拒绝理由与最终检查见已提交task-progress和保存证据；明确stop ponytail/normal mode。

## 真实OSS和本机验收准备

首次使用用户提供配置：真实指定图片上传HTTP200但businessCode0；仅记友好拒绝，未把HTTP200当成功。后续只读SDK请求返回InvalidAccessKeyId。原同bucket项目配置只读查询通过；再次只读确认测试prefix为空（0对象）后，用其刷新本票backend环境、保存首次失败记录，完成指定素材测试。原配置文件及原素材未修改。

成功记录：source=`yuweiju-document/xilanhua.png`，source/readback均363490字节且逐字节相等，Content-Type=image/png；对象 `https://java-learning-endercloud.oss-cn-shanghai.aliyuncs.com/issue20-acceptance/20261012/xilanhua_1d0f50d5-c26a-471c-bc31-3389366dd9ce.png` 保留。Git/SQL回退不撤销OSS新增对象；不删除或覆盖原素材。未对真实OSS执行失败/边界测试。

验收URL `http://127.0.0.1:18320/login`，后端 `http://127.0.0.1:18220`，127.0.0.1绑定，具名隔离库/缓存。管理端以准确候选源码、VITE_API_BASE=/api重新构建验收产物（exit0）；不是代码修复或额外AFK轮次。采用Vite既有proxy和本票专用allowedHosts，不关闭全局主机保护。首次容器主机拒绝截图及错误保留；初次驱动还错误使用精确登录按钮名称（按钮双面文案）并遗漏build时API配置，修正宿主驱动/验收配置后重跑，候选源码未变。

实际Chromium5场景全部通过：页面真实登录/代理、批准上传PNG解码、菜品/套餐默认库真实读取与筛选/选择/URL交接/解码、无JavaScript page errors；四张业务截图保留。API未登录拒绝核对通过。用户随后明确反馈菜品、套餐、上传、外观全部通过；人工反馈与准确候选对应。服务保留至全部验收通过并交付后停止，保留卷、证据和OSS测试资产；没有设置未授权的到期停止。

## 资源与证据

根目录全部产物在项目内；临时材料统一 `.scratch/issue20-afk-20261012/`。两轮编码容器已移除，资源stopped=True，独立容器已停止；业务探针本地HTTP/Spring上下文和临时Redis key均清理，合成成功商品及历史图片夹具按manifest保留。验收backend/admin/mysql/redis按授权仍运行。服务状态/停止入口见人工验收文档及acceptance-state.json。

关键本地证据：coding/*-result.json、*-resources.json、*-iterations/iteration-1及2；independent/task-review-1791821508263417671/resources.json、exit-status.tsv、check-0/1/2.log、snapshot/.scratch/issue20；real-oss-results-supplied-key-failed.json、oss-readiness.json、oss-local-readiness.json、real-oss-results.json；browser-results.json、browser-results-first-failure.json、首次失败截图及dish/setmeal四图；acceptance-admin-build.log。

凭据值不进入报告/日志/候选；用户提供的私有配置被根Git本地exclude排除且Windows ACL限制，未传给编码代理。失败尝试/资源回收及证据保存记录留存。Issue#20正文/评论/原生依赖原始响应和fetch退出记录保留，#2已关闭，未改父Issue。

## 一次全Loop复盘（提案，不自动实施）

先核对stop记录，再核对两轮日志、15文件diff、第一轮检查及独立准确候选结果。本轮没有成功监督器result；全Loop复盘仍包含超时及宿主验收准备失败。

| 证据与影响 | 建议 | 状态 |
| --- | --- | --- |
| 第一轮exit0/clean且有任务提交，但completionTextObserved=false，浪费第二轮setup/检查时间 | 所选prepare prompt模板明确完整 `<promise>COMPLETE</promise>`，不要只写COMPLETE；保留现有deadline/完成判断 | 本轮closing已补具体提醒；模板改造仅提案 |
| 第一轮完整证据在iteration-1/files，第二轮误查新worktree，重跑与预算冲突 | runtime现有逐轮提示明确该只读证据根，并提示同源码检查可带来源复用 | 本轮做路径更正；长期改造仅提案 |
| 缺作者身份导致正常commit128，env解决后成功 | 准备阶段核对任务提交身份，并仅为任务设置已有作者/committer环境，不改全局Git或弱化guard | 本轮正常环境更正；准备检查改造仅提案 |
| 用户提供OSS key首次到真实上传才实测InvalidAccessKeyId | 所有要求真实OSS的任务在编码前用同bucket只读SDK能力核对，而非字段存在；输出仅错误类别，不记录body/凭据 | 本轮诊断/同bucket本机配置修正完成；通用准备改造仅提案 |
| 宿主临时inspect.py遮蔽stdlib、Vite host限制/API build配置和双面按钮locator产生准备错误 | 临时脚本避免标准库模块名，复用具名preview配置，实际调用点/按钮结构决定驱动；VITE_API_BASE在验收build明确传入 | 本票准备已修正；未改runtime或扩大业务范围 |

上述维护改造需后续明确授权走yuweiju-retro-improve；本票不新增hash/冻结contract/baseline/gate，不额外续跑，业务候选交付不等待环境改造。

交接前最终核对：Windows localhost登录页HTTP200；JS/CSS均HTTP200且MIME正确；实际代理的未登录默认图片请求HTTP401、businessCode0。候选完整diff凭据扫描无本次提供或本机既有OSS凭据。浏览器最终再次5场景通过，选择截图等待弹窗关闭动画结束后取得；此为宿主截图调用更正，没有候选源码变化或额外OSS写入。来源：handoff-ready.json、candidate-credential-check.json、browser-results.json及四张截图。

## 人工验收与交付核对（2026-10-13）

用户反馈：`候选0b2e0be；菜品通过；套餐通过；上传通过；外观通过。`。用户同时确认满足任务后可使用yuweiju-deliver；启动时已授权必要提交、推送、PR、合并、同步及关闭，验收后交付完成停止具名服务、保留卷。四项issue验收逐项完成：职责与真实来源；合法选择保存及异常拒绝；资源/cause/脱敏；两端外观/素材及历史图片保持。原生#2依赖仍closed。GitHub main当前fcf5d28与起点一致，项目内独立交付工作树fast-forward集成，无应用修改或冲突；将引用准确候选的既有独立检查，不额外重跑未改源码。监督器timeout历史保持，候选独立验证、人工接受和业务交付分开记录。未新增Release/tag或生产部署。
