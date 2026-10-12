# Issue #20 人工验收

状态：2026-10-13 用户已确认全部通过。反馈：`候选0b2e0be；菜品通过；套餐通过；上传通过；外观通过。`。下方步骤作为已验收版本的操作记录保留。

候选：`0b2e0be3e65674f17ff2d4f59fd40e5997cf13db`。管理端：[打开验收服务](http://127.0.0.1:18320/login)。后端：`http://127.0.0.1:18220`。

这是本机隔离 MySQL/Redis 的商品与账号；图片上传/默认库连接已授权的真实 OSS bucket。请使用隔离账号 `sandbox_admin` / `sandbox-only-login`，不要输入原库账号。服务源码及 jar 均来自准确候选的独立快照；管理端按 `VITE_API_BASE=/api` 构建。原库及原素材保持，新增测试 OSS 图片保留。

## 必须验收

1. 登录后打开“菜品管理 → 添加菜品”。打开默认图片库，按文件名/对象键搜索，选择并确认；图片应在编辑页正常显示。填写名称、价格和菜品分类，保存；在列表重新打开编辑，图片仍应保持、正常加载，原布局和素材外观保持。
2. 打开“套餐管理 → 添加套餐”。选择套餐分类并按页面要求添加菜品，选择默认图片，保存并重新打开编辑；套餐图片仍应保持、正常加载，原布局保持。隔离库含探针生成的套餐分类，可任选一个 type2 分类；测试名称建议包含“issue20验收”。
3. 上传流程：在菜品或套餐编辑页面使用项目 `yuweiju-document/xilanhua.png`；应提示上传成功、显示图片，保存并重新打开后图片保持。上传不允许的文件扩展名应显示友好错误。此步骤每次合法上传都会在真实 OSS 新增一个对象，不覆盖/删除已有对象。若希望不再新增，指定图片的自动真实上传及保存链路已有独立证据，可明确反馈“真实上传自动证据认可”。

自动检查已覆盖空文件、格式/大小边界、未登录/错误身份、上游存储失败、SDK marker 分页、资源关闭、日志脱敏、真实 SQL 商品/套餐保存、两端当前商品接口与历史订单图片保持；不是用 HTTP200 或 Mockito 代替真实数据库结果。后端98测试、管理端17测试、真实隔离探针250断言、真实 OSS 上传/逐字节读取、实际浏览器5场景均通过。

## 反馈

请回复：`候选0b2e0be；菜品通过/失败；套餐通过/失败；上传通过/失败或真实上传自动证据认可；外观通过/失败；异常及操作时间：…`。

全部必需项通过后，按已有授权进行必要提交、推送、PR、合并、同步及关闭。AFK监督器本次仍记为超时取消，准确候选已另行独立通过；不会因此重启编码或把监督器状态改写为成功。

## 服务与资产

服务保留到“全部人工验收通过并交付完成”后停止，不设未授权到期时间。具名资源：`yuweiju-issue20-acceptance-backend-1`、`yuweiju-issue20-acceptance-admin-1`、`yuweiju-issue20-mysql-1`、`yuweiju-issue20-redis-1`。停止入口（仅停止这四个容器，保留卷/证据/OSS测试对象）：

```powershell
wsl -d Ubuntu --exec python3 '/mnt/e/Learning Files/yuweiju-takeout/.scratch/issue20-afk-20261012/acceptance.py' stop
```

恢复材料：同目录 `acceptance-state.json`、`acceptance-compose.json` 和准确独立 snapshot；已停止的具名容器保留环境，可先启动本票MySQL/Redis并确认healthy，再启动本票backend/admin。不要重跑真实上传脚本制造重复证据。

真实图片：[上传并保留的西兰花](https://java-learning-endercloud.oss-cn-shanghai.aliyuncs.com/issue20-acceptance/20261012/xilanhua_1d0f50d5-c26a-471c-bc31-3389366dd9ce.png)。你本次提供的AccessKey被上游返回InvalidAccessKeyId；同bucket的既有本机配置只读核对成功后用于本次验收。凭据值不进入报告、证据或候选提交。
