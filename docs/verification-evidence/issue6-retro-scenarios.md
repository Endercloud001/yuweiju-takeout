# Issue #6 AFK 指导应用测试证据

2026-10-11，测试来源 main 5271d78，修订前后由同一只读子代理进行。原始业务失败见[执行报告](../issue%236-executing-report.md)。

## 修订前

只读子代理 `/root/skill_scenarios` 阅读当前 main 的 SKILL.md 和 startup/runtime/acceptance；未调用 SDK、模型运行时或数据库。测试入口是技能指导的实际步骤选择，未执行业务场景。

1. 官方能力不足：SKILL 第2步要求 required real acceptance capability is ready，acceptance 仅要求收到重编译确认；缺 SDK 有效版本/实际页读取，可能以 CLI 和人工确认替代。
2. 模板断言不足：只有按业务项验证原则；未指导区分 tab 与订单行、模板参数转换与底层 formatter。
3. SDK 交互不足：已有截图超时要求；Page/Vue 方法差异及 data 超时没有明确等价替代证据步骤。
4. 合成身份部分足够：已有不同账号隔离和权限预期；缺固定 openid 说明，两个 code 可能被错误当作不同用户。
5. 编码部分足够：已有 Windows UTF-8/LF 写入示例；缺配置和中文 Markdown 显式读取编码，默认 GBK 仍可能失败。

原失败证据来自用户指定的执行报告：待付行文案/按钮失败、宿主 formatter 误报、服务端口关闭、SDK 无页、lower 不是 Page 方法、支付 data 超时、固定 openid 空用户夹具错误及 GBK 准备失败。文档应用测试只核指导缺口，不重新制造这些业务/环境副作用。

## 修订后

同一只读子代理 `/root/skill_scenarios` 重新阅读四文件，复测相同五场景，均通过。未访问 SDK、IDE、模型运行时或数据库。

| 场景 | 从修订文档选择的实际动作 | 结果与限制 |
| --- | --- | --- |
| IDE 登录/auto 成功但无页 | 核目标隔离路径/端口/副作用，SDK 有限超时读取有效版本和实际路由；无页判能力未就绪 | 通过；只证明就绪，非业务通过；非官方小程序任务不受此条件影响 |
| tab 文案和 formatter 参数误判 | 核参数语义，复用真实 SFC/store/API/tools 渲染，按订单行断言，触发模板事件核路由/金额 | 通过；先旧候选失败再修复通过；普通测试不替代官方交互/真实后端 |
| lower 不是 Page 方法、支付 data 超时 | 查 WXML 注册，触发 scrolltolower 并核页码/新增记录；保留 data 超时，核路由/ID/store 金额 | 通过；替代证据仅可证明导航与交接，不证明支付渲染/处理；缺等价证据仍受阻 |
| 两个 code 固定 openid | 查 code→openid→主键；具名隔离库独立用户、优先现有登录/签发助手，真实认证校验保持 | 通过；同主键不同 token 仍不是第二身份；不能替代真实微信双账号 |
| 默认 GBK 读取、Linux CRLF 写入 | 显式 utf-8-sig 读取、utf-8 写入、ensure_ascii=False、newline='\n'；核实际文件、保留失败输入 | 通过；不丢弃解码错误、不重建已启动输入或重置预算 |

边界附查通过：文档未新增真实数据库、真实密钥或额外预算授权。该结果是技能检索与应用测试，不是 SDK/监督器运行测试。
