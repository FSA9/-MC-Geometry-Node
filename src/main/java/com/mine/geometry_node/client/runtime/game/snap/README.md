# 视口几何吸附

几何吸附把渲染线程采集的有限几何绑定到发布帧，移动交互只根据显示帧和显式设置求解。UI 不为每次鼠标移动重新扫描活世界。

| 文件 | 职责 |
| --- | --- |
| `ViewportSnapSettings` | 每视图的启用、目标类型、基准及是否排除不可选对象的值设置；关闭吸附不丢失已选配置。 |
| `ViewportSnapGeometry` | 不可变几何、网格与查询值，是渲染线程到交互求解的快照边界。 |
| `ViewportSnapSources` | 仅在几何吸附移动查询有效时，在客户端渲染线程捕获方块、场景和插件几何。 |
| `ViewportSnapMeshCounts` | 在网格构建、细分和世界空间转换前估算顶点/边/面开销。 |
| `ViewportSnapSolver` | 网格吸附和基于帧几何的移动求解，供模态移动与轴手柄共用；不读取活世界、输入或 UI 状态。 |

`ViewportSnapSources.Collector` 对内置源和插件共用预算：最多 512 个网格、32768 个顶点、65536 条边和 65536 个面。达到限制时停止接收，不能为插件另开无界集合。`add(counts, builder)` 支持先判断预算再构建，并在结果提交时重新核对实际拓扑数量。

扩展使用 `ViewportSnapSources.register(Source)`。回调在渲染线程运行，使用传入的 `PresentedViewportFrame`、`Query` 和共享 `Collector`；只提交不可变几何，不保留世界/实体给 UI。需要复用场景网格时走 `SceneGeometryInputs` 已有的局部/世界有界缓存，不复制一套常驻网格历史。

查询与结果随视口帧发布。UI 应使用 `drawPresented` 实际返回帧的 `snapGeometry`，让吸附、选择与显示像素保持一致。窗口/相机变化时继续遵循帧尺寸和录制检查，不用最新相机强行解释旧帧几何。
