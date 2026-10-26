# 动态视觉效果与世界调试绘制

`ClientVisualManager` 管理由 `PacketSpawnDynamicVisual` 驱动的短期效果。`GeometryNodeClient` 在初始化时注册内置 factory，每次 client tick 推进寿命，在世界几何提交阶段绘制，logout 时统一清理。这不是编辑器界面或模型资产加载服务。

| 子目录 | 具体职责 |
| --- | --- |
| `effects` | `AbstractVisualEffect` 定义 tick/render/close；`DirectedVisualEffect` 复用朝向，`BeamGeometry` 生成束几何；线、盒、laser/ray、物品和图片分别有独立效果类。 |
| `math` | `ClientPropertyFetcher.Resolver` 把公共表达式的实体绑定解析为当前世界中的值；每次求值上下文以世界、partial tick 和 UUID 校验实体身份。 |
| `image` | `ClientImageAssetManager` 校验图片来源并拥有运行时纹理、服务器内容和租约。 |
| `debug` | `GeometryDebugRenderer` 绘制服务端调试几何；`SchematicProjectionRenderer` 维护有数量/几何/NBT 预算的投影数据并借用原版方块、实体渲染能力；`GeometryDebugPipelines/RenderTypes` 管理对应世界绘制管线。 |

## 效果何时进入和离开世界

新增效果通过 `registerFactory(effectType, factory)` 接入，factory 将协议快照转成实例。注册表和活动列表不是通用并发集合；注册应在启动阶段完成。待添加队列是并发队列，实际合入和寿命更新仍发生在客户端 tick；暂停时不推进效果。

`tick()` 移除到期实例时调用 `close()`。`clear()` 同时关闭待添加和活动实例，不能只清活动列表。新增效果若持有租约或原生对象，必须在自己的 `close()` 释放；渲染使用 Minecraft 的 PoseStack、buffer 与 SubmitNodeCollector，不依赖 ModernUI。

表达式按 render partial tick 采样，效果自己的 age 与世界时间分别进入公共表达式上下文。新增动态属性继续使用 `LiveValue` 和端口定义的校验/回退语义，不在本包复制一份表达式协议。

## 图片的内容所有权和纹理所有权

`ClientImageAssetManager` 支持 resource、server 及公共资产引用解析出的 local/pack/worldpack。内容先经过大小、尺寸和哈希校验，再通过 TextureManager 管理动态纹理。`TextureLease` 是一次纹理使用权，`ServerAssetLease` 是定义对服务器字节的保留权；定义休眠可以释放纹理而继续保留来源，恢复时再申请。

图片效果延迟到绘制时取纹理，服务器字节晚到时允许重试；灯光投影也复用这些租约。调用者必须关闭自己取得的租约，不能仅因控件关闭就释放其他世界资源正在使用的纹理。虽然管理器集合加锁，创建/释放 TextureManager 资源仍应在客户端渲染线程进行。

普通缓存清理仅移除可重建的路径查询和失败记录，不释放活动租约或未领取的服务器传输；连接清理的 `clear()` 才释放纹理并清除服务器内容。具体缓存调度见 [runtime/cache](../cache/README.md)。

调试几何和结构投影各自提供 clear，接收与清理由客户端世界生命周期驱动。新增调试协议时保留现有资源 ID、维度检查和预算；不要把世界数据的权威集合移到显示它们的 editor。
