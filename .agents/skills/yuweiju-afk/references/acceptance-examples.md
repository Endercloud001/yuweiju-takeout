# 模型代理与官方小程序验收示例

仅在验收涉及真实模型、官方组件或截图回退时读取。示例来自 #16 的实际失败与修正；端口、页面、数量和节点名由当前任务实查，示例不授权外部请求。

## 真实模型代理

共享辅助模块 [model-relay-support.py](../../../../.sandcastle/environment/model-relay-support.py) 提供 Content-Length/chunked 解码及持久化额度，未启动服务器。任务可用 importlib.util.spec_from_file_location 导入模块，在现有 task-local BaseHTTPRequestHandler 中接入：

```python
# 同一个进程只创建一个 budget，路径放本票项目内 evidence，父目录先准备。
budget = support.CallBudget(evidence / 'real-call-budget.json', authorized_maximum)
# do_POST 内，先限定业务路径，设置 socket 读取超时，再解析 framing 和 JSON。
self.connection.settimeout(10)
body = json.loads(support.read_body(self.rfile, self.headers))
# 完成字段校验和模式选择后，仅真实转发分支 reserve；替身分支不占真实额度。
sequence = budget.reserve()
# reserve 成功后才发固定已授权上游请求，单次超时，无自动重试。
# 仅记 sequence、HTTP状态、耗时与异常类；不记正文、请求头或密钥。
```

read_body 的 ValueError 映射友好4xx并关闭连接；坏 framing 不转发、不消耗真实额度。reserve 耗尽返回429；持久写入失败停止该请求，不能忽略错误继续转发。超时/HTTP失败的已预留次数保留，不能因失败退回可能已计费的调用。已有文件按 UTF-8读取，损坏即失败，不能归零。重启复用同一 ledger 和已授权上限；删除/换文件不作为恢复方式。此锁仅覆盖一个进程内共享实例，多进程部署不适用。

先用本地 HTTP 替身验证 chunked（含扩展/trailer）和 Content-Length、坏 framing、额度耗尽、并发及重启；检查替身次数与 ledger，再由后续任务已有授权决定是否真实调用。控制入口绑定127.0.0.1；容器代理沿既有 internal/受限出口网络，仅宿主控制端口发布到127.0.0.1。凭据只用既有只读挂载，不写入 ledger。结束记录本票具名资源并停机，保留费用 ledger。

## 官方组件实际节点

先确认连接的 SDK 项目及 currentPage.path 是本票验收页，再查询运行时实际渲染节点。源码标签查询为空时，结合组件映射及实际渲染树查找 SDK 名称；#16 的 dish-card 实际为 wx-dish-card，该名称不能套用到每个任务。

定位卡片内真实按钮并 tap，核对候选 dishId、显示名称/价格/图片及 tap 后受保护购物车该菜数量变化。节点存在、page.data 有卡片或驱动 exit0 都不能独立代表加购通过。无按钮或 tap 后业务未变化，保留失败，修正定位/检查后再验；可以复用已成功返回的真实卡片，避免重复付费调用。

## 截图回退

优先 SDK screenshot，并打开图确认当前项目、页面及模拟器实际渲染。saveFile 额度失败先保存异常，不清理其他任务或共享存储。原生捕获前核实窗口所属项目和当前页，标明模拟器所在区域；捕获后打开图片，确认关键业务区域/按钮可见，可记录项目标题及区域坐标，不仅检查 PNG 存在。

捕获编辑器、其他项目、空白/遮挡模拟器或无法辨认页面时，标为无效截图，不计页面通过；重新定位后重拍。若需用户重编译/进入页，记录该协作步骤及后续有效图，保留首轮失败。裁剪只选核实的模拟器区域，不用数据 JSON、源码或 HTTP200 代替页面截图。
