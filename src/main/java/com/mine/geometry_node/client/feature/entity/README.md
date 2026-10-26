# 客户端实体业务边界

这里拥有连接级实体请求、注册命令和外观快照；具体窗口、草稿和绘制控件由 UI 拥有。本包不保存完整实体对象或编辑器视图。

核心侧的对应能力聚合在 `core/engine/system/entity`：`edit`、`index`、`preview` 管理权威操作，`appearance` 提供共同的外观规则与编解码。两侧按所有权划分，不要求目录完全镜像；完整边界见[引擎功能系统](../../../core/engine/system/README.md)。

| 目录 | 职责与生命周期 |
| --- | --- |
| `edit` | [ClientEntityEditor](edit/ClientEntityEditor.java) 管理实体读取和带预期 revision 的修改；共用有界 FIFO、单个在途请求、发送间隔、超时和连接代次隔离。发送前校验修改载荷，返回结构化结果及失败消息键。 |
| `index` | `ClientEntityIndex` 管理注册状态读取和带预期 revision 的注册修改；维护独立请求队列，成功修改后通知监听者。已提交修改归连接所有，关闭视图不取消其回执或刷新通知。 |
| [preview](preview/README.md) | `EntityPreviewClient` 在 Minecraft 客户端/渲染线程适配网络与连接身份；`EntityPreviewService` 管理有界批次、内存快照缓存、刷新、重试和超时；`EntityPreviewPump` 按需唤醒，使暂停界面仍能发送和过期。 |

- 编辑、索引和预览使用各自的 core 网络协议，不因共有实体标识而合并队列或改变取消、超时语义。
- `preview` 的 service、pump 及其内部协议保持包内可见，业务测试与所属实现同包；编辑和索引的包内测试入口同样保留。
- 业务服务依赖 core 协议和 Minecraft/NeoForge 适配，不依赖具体编辑器或 ModernUI View。编辑失败消息的翻译及聊天呈现由 `ui/components/integration/entity/editor/EntityEditorViews` 处理。
- `client/network/ClientNetworkReceiverRegistry` 装配初始化及预览回执路由；编辑和索引仍由各自 `init()` 注册接收器。连接代次在入队前捕获，回执在所属线程处理。
- 预览缓存按连接身份和服务端命名空间隔离，同连接中的维度切换不清空其他维度快照。缓存 ID、资源 ID、网络格式及其他运行时标识不随 Java 包路径变化。

## 请求所有权与关闭

`ClientEntityEditor.read()` / `apply()` 返回 `Request`，`result()` 提供异步 `Reply`，`close()` 取消调用方等待。尚未发送的请求可以移出队列；已发送请求即使取消等待，也保持串行槽位直到回执或超时，不能把取消理解为服务端撤销修改。

[ClientEntityIndex](index/ClientEntityIndex.java) 的读取请求可以关闭；`setRegistered()` 提交后的写请求归连接所有，其 `Request.close()` 不取消写入回执，成功后仍发送 change 通知。视图应移除自己的监听并忽略过期绘制回调，不应阻断全局刷新。

编辑与索引各自通过 `init()` 注册接收器、客户端 tick 和断线监听，且初始化幂等。断线增加 generation、结算旧请求并清理串行状态；网络线程捕获的代次必须在客户端线程处理回执时继续校验。

## 扩展入口

新增编辑字段与命令先遵守 core 的实体编辑验证和协议，再从 `ClientEntityEditor` 提交；草稿、控件、错误展示留在 UI。新增注册状态交互通过 `ClientEntityIndex` 及其监听完成，不另建一份注册状态权威。新增外观消费方使用 `EntityPreviewClient`，不要直接依赖包内 service 或自行发送预览请求。

对应业务回归测试位于 `src/test/java/com/mine/geometry_node/client/feature/entity/` 的 `edit`、`index`、`preview`。这些包内测试入口用于验证排队、取消、连接代次和缓存边界，应随实现保留。
