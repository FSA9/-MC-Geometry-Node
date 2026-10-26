# GLOBAL / 维度公共数据访问

`ClientScopedDataRepository` 管理按编辑器所有者隔离的实时请求会话，服务端存储仍由 scoped state 提供。目标 `GLOBAL` 是当前存档跨维度共享的一份 PUBLIC 字典；其他目标是服务端目录中的维度 ID。这里不枚举本地实体或区块、不访问原版 NBT、不新建客户端持久存储。

- 每个 `Session` 保存独立的维度目录、按目标分开的已读数据和请求状态，界面通过事件读取不可变快照。
- 网络请求统一使用 `ClientRequestTracker`，检查连接代次、请求 ID、操作和目标，显式超时。断线拒绝未完成的修改；旧服回包不能进入新会话。
- 替换提交使用最近一次权威数据的 revision，提交前确认编辑基线仍与快照一致，服务端再执行原子比较并替换。失败和未知结果不伪装为成功、不自动重放写入。
- 关闭视图可取消读取，已接受的修改保留到回包或超时且完成回调只调用一次。临时隐藏应保留 session，最终关闭才调用 `close()`。

这是活动编辑会话的请求状态，所有者负责释放，不能被缓存清理操作任意取消。表单草稿、范围选择和视图布局属于 UI，位于 `ui/editor/datalibrary/global`；`ScopedDataDictionaryBinding` 负责公共编辑器适配。feature 不依赖 UI、ModernUI 或具体工作区。
