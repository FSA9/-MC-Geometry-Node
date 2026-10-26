# 范围公共数据访问

本包提供编辑器对现有 scoped state 的远程访问，不拥有新的存储系统。

- `ServerScopedDataService`：当前连接与服务端线程、权限、真实维度枚举、目标解析及网络回复。只解析 PUBLIC 的 GLOBAL / WORLD，不接受外部 namespace 参数。
- `ScopedDataOperations`：可独立验证的目录、读取及原子比较后替换，提交前预检载荷、提交后核验容量拒绝。
- `ScopedDataConnections`：连接限速和清理，不保留数据与 UI 状态。

字典操作复用 `ScopedMetadataTargets`、`DataAccess` 与 `GraphMetadataDataValues`；存储仍是 `ServerScopedStateSavedData`。协议位于 `core/network/packet/scopeddata`，客户端请求会话位于 `client/feature/scopeddata`，界面位于 `client/ui/editor/datalibrary/global`。这里只处理明确请求，不加载区块、不广播全字典，也不定时扫描世界。
