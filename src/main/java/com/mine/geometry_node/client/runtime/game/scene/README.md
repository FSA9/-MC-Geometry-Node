# 场景到视口的运行时适配

场景文档、权限与编辑快照由 `client.feature.scene` 管理。本目录把这些快照转换为世界绘制输入、诊断几何、图层设置及基于已显示画面的创建位置，不维护第二份场景文档或编辑历史。

| 文件 | 职责 |
| --- | --- |
| `ScenePresentations` | 按对象类型/类别注册图层、点标记、绘制样式和网格工厂；按类型提供原生对象诊断几何，并缓存可重建结果。 |
| `SceneGeometryInputs` | 将 authored 场景对象转换为现有 debug 世界渲染器的输入和吸附网格。局部网格烘焙旋转/缩放，平移和对象颜色不触发局部顶点重烘焙。 |
| `SceneLightInputs` | 将场景灯光适配为运行时灯光输入；场景对象不进入图资源镜像的所有权/退出生命周期。 |
| `ViewportVisualization` | 每视图独立的显示、可选、网格和 gizmo 设置及不可变快照；显示开关不修改场景对象的实际 enabled 状态。 |
| `ViewportDebugClient` | 客户端线程上的诊断订阅、按世界隔离的服务端诊断快照，以及渲染时选择当前图层所需的数据。 |
| `ViewportSceneMath` | 根据发布帧进行投影、射线、包围盒拾取/框选和约束拖动；归一化图像坐标左上角为 `(0, 0)`，黑边与 UI 变换由调用方处理。 |
| `ViewportCreationPlacement` | UI 线程从显示帧捕获射线与后备位置；客户端线程验证世代、维度、权限和宿主后查询已加载方块并回调结果。 |

渲染与几何捕获优先消费 `EditViewportRuntime.renderedScene()` 冻结的场景，避免同一图像的世界绘制与交互几何使用不同版本。纯数学方法只读取传入快照；需要世界或区块的数据访问留在客户端/渲染线程。创建落点回调在客户端线程执行，调用者负责后续 UI 线程交接。

`SceneGeometryInputs` 的局部/世界吸附网格都有条目与拓扑总量上限，构建前检查本次吸附预算；世界世代变化时清理。可重建的场景几何与诊断缓存通过运行时缓存注册接入统一治理；它们与区块实际接收存储、GPU 帧租约是不同生命周期，清理这些缓存不会代替后两者的退出流程。

扩展外观使用 `ScenePresentations.register`、`registerCategory` 和 `registerDiagnostic`。注册只描述客户端外观和几何，schema、权限、属性表单继续由各自所有者管理。需要新交互身份或吸附数据时分别接入 [selection](../selection/README.md) 和 [snap](../snap/README.md)，并绑定实际显示帧。
