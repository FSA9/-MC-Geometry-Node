# 视口选择与世界动作

本目录把原生世界与插件目标转换为可跨线程消费的身份和几何。`ViewportTarget` 只保存有类型的 source/id、包围盒、装饰和可见采样点，不把活实体或世界交给 UI。目标集合在渲染捕获时写入 `PresentedViewportFrame`，后续拾取与上下文动作使用该帧。

| 文件 | 职责 |
| --- | --- |
| `ViewportSelectionSources` | 选择源注册、渲染线程捕获、图层可选性、可编辑对象映射和按 source 分发动作。 |
| `ViewportEntitySource` | 使用实际 viewport renderer 提取出的实体插值位置生成目标，再进行视锥和世界遮挡采样；每个新帧先重置提取集合。 |
| `ViewportInteractionSource` | 将原生 interaction 诊断网格对应的身份与服务端边界绑定。 |
| `ViewportSelection`、`ViewportTarget` | 选择集合运算及不可变身份/几何值，供多个交互入口共用。 |
| `ViewportSelectionRenderer` | 在不透明深度之后、半透明实体之前绘制世界中的选中脚底圆盘。 |
| `ViewportWorldActions` | 发起有界的实体移动请求，维护调用方反馈；在客户端发送前检查请求、世界世代、维度和连接，按请求 ID 匹配回复。 |

选择发布还携带场景世代。`EditViewportRuntime` 仅在请求世代匹配本帧场景时接受选择，避免跨世界的旧身份进入世界标记。UI 使用实际显示图像的目标和投影；尚未显示的新相机或最新实体坐标不能替代它们。

插件通过 `ViewportSelectionSources.register(Source)` 注册，source ID 必须是合法且唯一的资源标识，`geometry_node:scene` 为保留源。`capture` 在渲染线程执行，每个返回目标的 source 必须匹配注册源。只提供选择的插件可不实现编辑映射；支持编辑时提供 `editableObject`/`editableType`，并按图层提供可选性。

`action` 从交互调用方同步分发，不自动切换线程；插件若要访问世界或发送网络必须自行交接到客户端线程。内置 `ViewportWorldActions` 用弱引用 pending 记录、请求 ID 和世代检查匹配回复，取消会撤销当前 pending，超时与失效结果不会写入新的请求反馈。服务端仍负责最终动作校验。
