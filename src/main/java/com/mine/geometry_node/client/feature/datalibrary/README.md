# 客户端数据字典访问

本包是服务端权威 Data Library 的客户端访问边界。窗口读取这里的快照并提交命令；本地快照不是独立数据库，也不能以 UI 的乐观修改替代服务端保存结果。

## 文件与依赖方向

| 文件 | 定位 |
| --- | --- |
| [DataLibraryRepository.java](DataLibraryRepository.java) | 调用方接口，定义条目、目录、查询、变更、监听和保存状态。条目的值、标签与自定义 `data` 字典是不同字段；`sameContent()` 做语义快照比较。 |
| [ClientDataLibraryRepository.java](ClientDataLibraryRepository.java) | 共享门面，合并首次加载，生成默认唯一名称，维护条目更新计数、标签或字典保存消息和完成通知。可注入 gateway 供测试或其他装配使用。 |
| [RemoteDataLibraryGateway.java](RemoteDataLibraryGateway.java) | 远端快照和命令协议，隔离业务门面与实际传输。 |
| [NetworkRemoteDataLibraryGateway.java](NetworkRemoteDataLibraryGateway.java) | 默认实现，维护权威快照、连接代次和串行变更队列；通过控制包及资产传输执行远端操作。 |
| [DataLibraryMapper.java](DataLibraryMapper.java) | 客户端条目与 core 文档、目录、对象键之间的转换。 |
| [DataLibraryEntryValues.java](DataLibraryEntryValues.java) | 使用 core 值编解码及当前注册表进行值替换、比较与序列化。 |
| [DataLibraryTransferRequests.java](DataLibraryTransferRequests.java) | 构造固定远端路径 `data-library` 和专用 purpose 的上传、下载请求，限制允许的上传用途。 |

依赖方向为 `ui/editor/datalibrary → 本包 → core、feature.asset.transfer、client.network / platform`。本包不持有 ModernUI View；类型值与元数据的规则由 core 提供，通用文件传输由 asset 提供。

## 加载、提交与连接生命周期

`ensureLoaded()` 合并并发的首次加载需求，`refresh()` 显式刷新。读取必须同时关注 `hasLoadedSnapshot()`，空列表不保证已成功取得权威快照。

网络 gateway 串行执行变更，上传或控制操作后重新读取服务端快照，再推进队列和通知门面。条目或目录的 expected 内容经指纹参与并发冲突校验。字典保存 `replaceData()` 在发布 pending 状态前校验并快照化输入，拒绝过期 expected 或重复保存；完成消息为空表示成功，否则交给 UI 翻译消息键。

门面与 gateway 分别拥有连接代次及 `resetConnection()`：前者清理首次加载、pending 和保存回调，后者清理远端快照、队列和请求跟踪。外层连接生命周期装配需要重置两者，不能只销毁数据库窗口。旧连接的上传下载仍须完成临时文件清理，但不能发布到新连接快照。

窗口只拥有自己的 change listener，销毁时调用 `removeChangeListener()`；关闭视图不应清空全局仓库或取消已提交变更。字典保存完成通知与窗口生命周期应分别处理。

## 扩展与验证

新增业务操作先补 `DataLibraryRepository` / `RemoteDataLibraryGateway` 的明确方法，再落实默认网络实现、core 校验与权威刷新。新增值类型应复用 core codec 和 mapper，避免 UI、门面、网络层各维护一套类型转换。保存完成语义必须覆盖失败、断线和重复回调。

现有回归入口在 `src/test/java/com/mine/geometry_node/client/feature/datalibrary/` 的 `ClientDataLibraryRepositoryTest`、`NetworkRemoteDataLibraryGatewayTest`。本目录为一个协作层级，无需逐文件再拆 README。
