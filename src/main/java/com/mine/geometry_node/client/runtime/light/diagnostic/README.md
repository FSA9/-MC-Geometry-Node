# 灯光报告的采集会话

`LightReports` 注册 light report/profile 命令，在渲染线程捕获上下文、提交工作量和采样区间，再异步写入 `geometry_nodes/reports/light`。这里不拥有游戏 UI，也不把 CPU 提交时间解释成 GPU 或整个帧的成本。

`LightReportSession` 管理 IDLE → QUEUED → CAPTURING → DRAINING → SAVING。采集期间持有世界引用及查询释放责任；开始时先认领查询所有权，后续任何部分初始化失败都可清理。进入保存阶段先释放世界和 GL 查询，仅保留快照供 IO，完成回调必须匹配当前 generation 才能反馈或解除 busy。

`NodeLightPixelDiagnostics` 执行报告用的全屏像素探测；`NodeLightReceiverCostDiagnostics` 用有实际输出的配对查询观测 receiver 成本。GPU 查询非阻塞读取，待结果自然就绪后 drain，不能在保存 worker 上轮询 GL 或释放查询。

`LightReports.tick()` 即使世界绘制被抑制也推进世界变化/超时取消；模型生命周期在 logout、resource reload、shutdown 调用 `cancel()`。取消在渲染线程内同步释放查询并增加 generation，旧磁盘完成消息不会写回新会话。新增探测必须加入这一所有权边界，不能只在成功保存路径 delete 查询。
