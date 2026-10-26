# 客户端模型运行时

本树把公共 glTF/模型资产变成客户端世界中的实例、预测碰撞和 GPU 绘制。`core.engine.system.model` 继续提供导入、公共 repository/instance/碰撞契约；`client.runtime.model` 提供 Minecraft 资源入口、线程调度和渲染后端，不依赖 UI 或具体 integration。

| 目录/入口 | 实际责任 |
| --- | --- |
| [`runtime`](runtime/README.md) | `ClientModelService`、定义/激活/发布队列、驻留及客户端/集成服务端碰撞。这里的第二级 runtime 是模型实例运行层，不是另一个客户端根。 |
| [`render`](render/README.md) | GPU 后端、几何准备、灯光/BVH、LOD、阴影和采样指标。 |
| `resource` | 单个 `ResourcePackModelProvider` 把 ResourceManager 中的逻辑路径交给公共 `StreamModelResourceProvider`；公共层负责路径约束、有界落盘和取消，provider 关闭时关闭委托。 |
| `compat` | 可选 Iris 桥接、pipeline 身份/赋值生命周期、sampler fallback 和资产 PBR 附件；详见下面的兼容约束。 |
| [`diagnostic`](diagnostic/README.md) | 模型命令、报告、质量控制和纯状态 `ModelHudState`；实际 HUD 位于 `ui/hud/model`。 |
| `ModelClientLifecycle` | 将服务、模型后端和 NeoForge 事件装配起来，不注册 UI。 |
| `ModelCacheIntegration` | 模型自身各类缓存的统一控制适配，不删除活动模型定义或租约。 |

## 启动和释放链

`GeometryNodeClient` 调用 `ModelClientLifecycle.register(modBus)`：建立 `ModelRenderInstanceSource`，注册模型 setup、resource reload、世界绘制、tick、level unload、logout 和 shutdown。`prepareFrame` 在已有帧入口中轮询 Iris、处理有界上传并准备受光；主不透明、透明及后处理后的透明阶段保持各自时序。

setup 在客户端队列中注册缓存。reload 取消灯光诊断、刷新 Iris、使模型 GPU 资源失效并清阴影/节点灯光及 HUD 状态版本；logout 清世界实例、碰撞及派生绘制资源；shutdown 注销模型缓存适配，关闭服务的 worker/repository、阴影与节点体积光资源。客户端和集成服务端的 level unload 分别进入各自 owner 线程的卸载路径。

HUD 注册独立在 [integration/model](../../integration/model/README.md)。命令与世界生命周期只操作 `diagnostic/ModelHudState`，render 中的 UI 根据 revision 重建文字缓存，不能让模型重载反向导入 HUD。

## Iris 兼容对象的归属

`compat/IrisModelBridge` 通过反射接入可选 Iris，并拥有进程级注册；无 Iris 时客户端仍可加载。`IrisLifecycleTracker` 用对象身份识别 pipeline 重建，不只比较布局字符串。`IrisPipelineAssignments` 的进程级赋值寿命与可重载的 GPU 程序分开。

`ModelIrisPbrBindings` 的附件纹理由模型资产拥有，Iris 的全局 holder 只是借用方；`IrisSamplerFallbacks` 处理宿主校验所需绑定。新增兼容逻辑应放在这一明确桥接或相应 shader 适配中，不能把 Iris 可选类型泄漏到公共导入契约。

缓存文件位于 `geometry_nodes/caches/model`，碰撞子目录为 `collision`。磁盘操作由缓存 IO 执行器处理，GPU/查询操作始终排队到客户端线程；清理 lookup、闲置 GPU 和磁盘缓存不等同于删除实例。具体资产/租约的释放责任见 [实例运行层](runtime/README.md) 和 [GPU 资源](render/resource/README.md)。
