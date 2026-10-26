# 节点灯光的客户端世界运行时

本树把公共灯光定义变成当前世界的光源采样、投影纹理、受光计算和诊断。定义和包结构属于 `core.engine.system.light` / `core.network`；这里使用 Minecraft、OpenGL 与可选 Iris 适配，不依赖 UI。

| 目录 | 入口与边界 |
| --- | --- |
| `store` | `ClientLightStore` 是服务端定义的客户端镜像，处理资源作用域、驻留与实体姿态。 |
| `material` | `NodeLightWorldMaterial` 提供原版世界材质的传输编码，维持既有模型遮挡判定；不是通用 UI 色板。 |
| [`render`](render/README.md) | 每帧灯光选择、光源表面、投影图集、体积网格及原版受光路径，拥有实际 GPU 资源。 |
| [`shader`](shader/README.md) | 原版/光影程序适配、签名保护及驱动程序二进制缓存。 |
| [`diagnostic`](diagnostic/README.md) | 采集窗口、非阻塞 GPU 查询及异步报告输出。 |

## 定义、驻留和投影图不是同一生命周期

`client.network.ClientNetworkReceiverRegistry` 初始化 store 的世界卸载/tick 监听，再把 `PacketGraphLight` 排队到客户端线程。store 校验当前维度与 LIGHT 类型的 `GraphResourceId`；同一世界保留定义，`ResourceResidency.watch` 决定是否激活本地资源。实体跟随按 render partial tick 采样，不用服务器 tick 位置替代。

每个 entry 拥有驻留注册及投影对象。投影通过 `runtime/render/image/ClientImageAssetManager` 分别保留服务器字节与纹理 lease；休眠释放纹理，定义删除、替换或世界卸载再关闭内容保留和驻留注册。相同投影路径的替换可转移既有所有权，不能先释放后继续借用旧对象。字节晚到的重新获取也在世界 tick 进行，不要求屏幕或一次绘制来维持资源。

`NodeLightRenderer.prepare` 建立受预算限制的本帧采样，世界切换时清理派生 GPU 状态。模型生命周期在 reload/logout/shutdown 取消诊断并清理灯光渲染资源；shutdown 还关闭原版体积光程序。store 的世界定义由自己的卸载事件负责撤销，不应由“清渲染缓存”删除。

新增光源类型应先沿公共定义/协议扩展，再实现 store 的解析与 renderer 支持；光影包接收适配通过 `shader/adapter` 的显式列表选择，不能把未知 GLSL 当成已支持版本。
