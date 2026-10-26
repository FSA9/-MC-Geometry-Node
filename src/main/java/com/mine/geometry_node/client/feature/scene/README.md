# 客户端场景编辑镜像

这里维护客户端场景对象的不可变镜像、选择、预览、修改请求及历史。服务端是对象状态的确认方；UI 和渲染读取快照，不能直接改写镜像或提前把未确认请求记入成功历史。

本特性依赖 `core.engine.system.scene` 的对象/类型/能力、场景网络包、`client.history` 和 Minecraft/NeoForge 客户端生命周期，不依赖 ModernUI 或具体 viewport 控件。服务端存储、对象类型定义和渲染器不属于本目录。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| [ClientSceneService.java](ClientSceneService.java) | 单例应用服务和 `HistoryTarget`：世界/维度代次、定期同步、服务端 ACK、backend 镜像合并、选择、增删改、复制粘贴、变换预览、撤销/重做及碰撞镜像同步。 |
| [SceneEditBackends.java](SceneEditBackends.java) | 对象编辑权威路由与扩展注册：内建 authored 场景和 native entities 后端，按对象类型、操作支持、订阅需求及同步间隔发送请求。所有后端共享场景历史。 |
| [SceneEditCapabilities.java](SceneEditCapabilities.java) | UI、工具和修改入口共享的完整选择集能力判断；统一拒绝未解析目标、不支持的操作和混合后端，service 再叠加权限、同步和忙碌状态。 |
| [SceneClipboard.java](SceneClipboard.java) | 客户端内共享的脱离源对象的场景值剪贴板，复制已确认内容；粘贴重新生成对象 ID/初始 revision，保留类型参数、TRS、metadata 和活动对象映射。不同于图剪贴板和系统剪贴板。 |
| [history/SceneHistoryAdapter.java](history/SceneHistoryAdapter.java) | `HistorySession<Delta>` 的场景适配，最多保留 50 步；记录修改前后值及逐条版本证明，将 undo/redo 转为带期望对象版本的服务端补偿请求。 |

`history` 目前只有一个领域适配，由本页覆盖；历史栈和通用预约/完成机制仍在 `client.history`。

## 世界、快照与请求生命周期

`init()` 幂等注册 tick 和登出监听。世界身份或维度变化时递增 generation，清空镜像、pending、预览、选择、剪贴板及历史作用域。网络入口和发送前均校验世界/代次；旧世界回调不能发布到新世界。

`confirmedSnapshot()` 是服务端已确认对象；`snapshot()` 可以叠加当前预览或等待 ACK 的变换视觉状态。两者均以不可变值发布，内部修改在服务锁内串行。渲染和 UI 应根据用途选择快照，不能用视觉预览作为已确认历史。

修改先预约历史并校验能力/版本，再建立唯一 pending 并发包。ACK 匹配请求 ID 与 backend 后才完成对应预约；先发布最终镜像和选择，再完成历史，使订阅者看到一致状态。较旧的幂等 ACK 可以完成请求，但不能覆盖已接收的新版本镜像。

等待过久或传输抛出异常时保留预约并标记 `UNKNOWN`，按相同请求 ID 和内容重试。不能把超时当作“服务端一定没执行”，释放槽位后另发一条不同请求。当前 ACK 结算前禁止另一次修改；历史通知期间也禁止重入修改。

## 预览、历史与后端扩展

预览用 owner 身份独占，并在开始时检查世界 generation、对象 revision，必要时校验完整对象内容。预览变换只影响展示；取消不产生服务端写入。提交转为普通待确认请求；远端冲突或权限丢失会取消不再可靠的预览/视觉状态。宿主应在手势结束或销毁时释放自己持有的预览。

撤销/重做同时比较对象内容与版本证明，包括删除后的 tombstone 版本。远端先修改再恢复相同内容也不能自动恢复已断裂的版本链；不能只比较当前外观就允许回滚。失败 ACK 不推进历史栈。

新增对象类型首先在 core 场景类型/能力体系注册。外部编辑权威通过 `SceneEditBackends.register` 提供互不重叠的接受条件、发送入口、可支持修改和订阅同步策略，再通过 `acceptBackendSnapshot` 接收镜像；同一次请求不能跨多个后端。native 订阅需求由装配层调用 `configureNativeSubscription` 提供，feature 不查询具体窗口。后端 ID、对象 ID、协议字段和场景类型 ID 均保持稳定。
