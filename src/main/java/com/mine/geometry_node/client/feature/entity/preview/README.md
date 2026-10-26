# 实体外观快照解析

本目录为可见的实体预览提供外观模板，不提供实体编辑会话，也不持有 Minecraft 世界、实体实例或 UI 控件。

| 文件 | 职责与可见性 |
| --- | --- |
| [EntityPreviewClient.java](EntityPreviewClient.java) | 公共入口，适配客户端线程、当前连接、服务端身份和网络包，返回 `LOADING / READY / UNAVAILABLE / ERROR` 及模板。 |
| [EntityPreviewService.java](EntityPreviewService.java) | 包内解析器，拥有有界待发队列、批次与并发限制、不可变外观缓存、刷新与退避重试。泛型模板必须不可变。 |
| [EntityPreviewPump.java](EntityPreviewPump.java) | 包内按需唤醒器，在有工作时继续驱动发送与超时，避免暂停界面停止 tick 后请求无法推进。 |

## 调用与生命周期

`EntityPreviewClient.init()` 由 `ClientNetworkReceiverRegistry` 在首次使用前初始化。`resolve(entityId, dimension)` 只应在可见原生预览的客户端/渲染线程回调中调用；空维度使用当前维度。绘制实体和释放绘制资源由消费方负责。

`resolve()` 读取缓存或登记解析需求，并唤醒 pump；网络回执由统一接收器捕获 generation 后投递客户端线程，再交给 `accept()`。service 合并目标请求并对结果尺寸施加限制；tick 与 pump 负责刷新、过期和重试，不要求每个控件单独建定时器。

连接对象或服务端命名空间改变会隔离旧请求与缓存，并取消旧 pump 代次。同一连接内的维度切换不清空其他维度快照。断线清理解析需求，后续可见预览按新连接重新解析。

缓存 ID 为 `geometry_node:entity_preview.snapshots`，由 service 向统一缓存注册表登记；缓存清理由所属线程处理。缓存只保存模板快照，不能把控件、原生实体或世界对象放入其中。

## 扩展与测试

新增消费方使用公共 facade，按状态显示占位或模板，不直接接触 service、pump 的包内协议。修改批量、重试、尺寸或缓存策略时保留连接隔离及暂停界面的进度保障。`EntityPreviewServiceTest`、`EntityPreviewPumpTest` 位于 `src/test/java/com/mine/geometry_node/client/feature/entity/preview/`，使用可控时钟与队列验证这些边界。
