# 模型命令、报告与 HUD 状态

`ModelCommands` 是命令入口，调用 `ClientModelService` 管理实例和报告；`ModelCollisionCommands`、`ModelRenderCommands`、`ModelLightingCommands` 分开组织碰撞、绘制和局部受光控制，`ModelLightingCommandFeedback` 在执行时生成反馈。新增业务能力应先加入模型服务，命令只做参数适配，不在回调里导入/解码/构建网格。

`ModelReportContext` 捕获可比较的设置，`ModelPerformanceOverview` 解释观测样本，`ModelPerformanceReport` 组织有界性能视图，`ModelReportSummary` 生成聊天摘要。CPU 提交、批次 GPU 时间和 shader draw 采样是不同范围；没有测量不能填成零，详细几何与兼容调试也不等于性能导出的全部内容。

报告先在客户端/渲染线程取得服务快照，再交给有界 `REPORT_IO` worker 序列化/写入，完成反馈回到 Minecraft 线程。`ModelReportFiles` 在现有 `geometry_node/model` 报告目录以 `CREATE_NEW` 生成文件，重名不能覆盖旧报告；该目录不是 `geometry_nodes/caches/model` 缓存目录，不随“清缓存”删除。

`ModelHudState` 只拥有 enabled 与递增 revision。命令改开关，模型 reload/logout 使 revision 失效；实际文字缓存、颜色、换行和绘制属于 `ui/hud/model/ModelHud`，由 `integration/model` 注册。此包不持有 View 或 ModernUI 对象。不要为展示指标把 HUD 拉回 runtime，也不要让 IO worker 直接读活动 renderer 状态或操作 GL 查询。
