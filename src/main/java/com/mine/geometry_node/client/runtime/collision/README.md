# 服务端几何碰撞的客户端预测副本

`ClientGeometryCollisions` 处理 `PacketGeometryCollision`，保存服务端几何定义、位移和排除实体，再把本地驻留部分发布给公共 `ModelCollisionScene`。客户端本地导入模型的碰撞走 `runtime/model/runtime/ClientModelCollisions`，两者不要混成一个实例表。

处理包时校验当前世界、维度和 `GraphResourceId` 类型。UPSERT 准备几何，位移更新沿用已有几何，REMOVE 关闭相应记录。每条记录拥有一个 `ResourceResidency.Registration`：离开驻留范围撤下预测碰撞，但保留定义，重新驻留时可恢复；这不同于删除服务端资源。

这些可变集合和驻留回调由客户端世界线程使用，没有通用并发容器保护。切换世界或对应 `LevelEvent.Unload` 会调用 `clear()`，关闭驻留注册并撤下已发布场景。这里不拥有 GPU、模型导入任务或 UI；新增碰撞类型应沿公共资源协议和碰撞场景契约接入。
