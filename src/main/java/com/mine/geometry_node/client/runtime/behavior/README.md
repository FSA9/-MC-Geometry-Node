# 行为调试的客户端镜像

`ClientBehaviorDebugStore` 接收服务端的 `PacketBehaviorDebugSnapshot`，按实例 UUID 保存状态、说明和执行快照。这里不执行行为树，也不把界面选中项当作服务端执行状态。

- `subscribe` / `unsubscribe` 只发送订阅协议；`handle` 才更新镜像。收到 `CANCELLED` 时移除实例。
- 最多保留 16 个实例，超限移除插入顺序最早的项；`entries()` 返回条目列表快照。
- 读写入口使用同步锁保护集合；网络接收由 `client.network` 排队进入客户端线程，UI 可以读取快照，但不能直接改集合。

`GeometryNodeClient` 在 logout 时调用 `clear()`。此包没有 GPU、文件或 View 所有权。新增调试字段应先扩展公共侧快照协议，再在实际 UI 中呈现；不要在这个 store 中创建控件或连接具体 editor。
