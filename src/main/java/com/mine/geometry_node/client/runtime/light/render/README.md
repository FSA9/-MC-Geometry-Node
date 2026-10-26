# 灯光绘制与 GPU 对象所有权

`NodeLightRenderer` 是每帧入口：从 `ClientLightStore` 及场景输入取得 resolved lights，按视点选择最多 8 个光源，发布相机、generation、灯光参数和原版/光影受光绑定。它缓存的是本帧观测与程序接口，服务端定义仍由 store 持有。

| 文件组 | 资源与工作 |
| --- | --- |
| `NodeLightSourceRenderer` | 绘制发光表面；在透明世界几何前建立原版源表面的深度/颜色。 |
| `NodeLightCookies` | 拥有聚光投影图集、上传重试和带 fence 的旧纹理回收；来源纹理仍由 image lease 管理。 |
| `NodeLightTargets` | 组织 surface atlas、体积受光绑定与绘制期间的状态恢复。 |
| `NodeLightFroxels`、`NodeLightFroxelQuality` | 把持久化质量设置映射成有界体积网格，执行注入、深度积分和历史复用。 |
| `NodeLightEntityShadows` | 为存活动物实体采样动画身体遮挡几何，不依赖它是否被主相机绘制。 |
| `NativeTerrainLights` | 原版地形受光，与模型接收者共享光源及世界/模型遮挡。 |
| `NativeVolumeLights` | 原版后处理体积光，在合适的世界后处理阶段合成；clear 与最终 close 分开。 |

这些对象属于渲染线程。GL 程序接口注册、draw 包装、纹理上传、查询和 fence 回收必须留在当前图形上下文，不把它们交给文件 IO worker。`NodeLightRenderer.NativeBinding` 等临时绑定按作用域恢复宿主 uniform/纹理状态，新增受光路径也要覆盖异常退出。

`NodeLightRenderer.deleted(program)` 移除已删除程序的派生接口；`clear()` 清灯光快照、图集、实体遮挡及体积派生状态。资源重载或世界变化不能只重置 Java 计数而保留无效 GL handle；应用 shutdown 通过模型生命周期调用 `NativeVolumeLights.close()` 完成程序级关闭。图集旧资源在 GPU 不再使用后退休，不把“已从本帧列表移除”当成可立即覆写。

此处可借用 `runtime/model` 的 BVH、光采样和受光资源；不拥有导入模型 lease，也不注册窗口。GLSL 编译与包识别的扩展入口见 [shader](../shader/README.md)。
