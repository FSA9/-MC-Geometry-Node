# 模型诊断 HUD 的事件接线

`ModelHudIntegration.register(modBus)` 注册 `RegisterGuiLayersEvent` 监听，以既有 `geometry_node:model_hud` ID 把 `ui.hud.model.ModelHud::render` 放入 HUD 层。`GeometryNodeClient` 在构造阶段、网络接收注册前调用一次；不要在每次进世界时重复注册 listener。

运行中的模型服务和世界 GPU 资源由 `runtime/model/ModelClientLifecycle` 管理。它独立注册 setup/reload/logout/shutdown 与世界渲染事件，**不导入 UI**；模型命令与生命周期通过 `runtime/model/diagnostic/ModelHudState` 的 enabled/revision 连接 HUD。

reload/logout 增加状态版本，下一次 HUD render 使文字缓存失效；HUD 自己按既有频率、窗口宽度、语言和世界变化刷新内容。HUD 只借用模型指标快照，不拥有模型资产 lease、导入 worker 或 GL 查询；关闭模型/GPU 资源不需要回调这个 integration。

此类拥有事件连接，不拥有画面状态，也不跨线程处理指标。新增 HUD 内容放到 UI，新增指标放到模型 runtime 的快照接口，新的资源释放仍加入模型生命周期，避免把不同层的责任合回一个类。
