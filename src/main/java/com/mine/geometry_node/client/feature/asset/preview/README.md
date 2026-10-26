# 资产预览产物与缓存

本目录解析预览所需的本地文件及格式信息。图片尺寸读取、纹理、schematic 渲染和 ModernUI View 位于 UI 层；数据 provider 不引用这些控件。

## 从条目到产物

| 文件或目录 | 职责 |
| --- | --- |
| [AssetPreviewProvider.java](AssetPreviewProvider.java) | `supports()` 与 `subscribe()` 的公共协议；监听者收到产物或不可用结果，使用者持有并关闭订阅。回调在 Minecraft 客户端线程执行。 |
| [AssetPreviewArtifact.java](AssetPreviewArtifact.java) | 携带文件、预览种类及格式提示；不持有解码后的图像或渲染资源。 |
| [AssetPreviewProviders.java](AssetPreviewProviders.java) | 默认注册本地、远端 provider，按注册顺序选择首个支持条目的实现；支持显式注册和注销。 |
| [LocalAssetPreviewProvider.java](LocalAssetPreviewProvider.java) | 解析本地可预览资产的源文件。 |
| [RemoteAssetPreviewProvider.java](RemoteAssetPreviewProvider.java) | 根据远端路径、预览种类、大小和修改时间构造 revision，委托共享服务解析产物。 |
| [ClientAssetPreviewService.java](ClientAssetPreviewService.java) | 合并相同服务端和 revision 的订阅，连接缓存查找、协议请求与落盘，持有文件使用权。 |
| [ClientAssetPreviewCache.java](ClientAssetPreviewCache.java)、[ClientAssetPreviewPaths.java](ClientAssetPreviewPaths.java) | 管理持久化预览及服务端隔离路径，使用 generation 防止清理前的异步结果重新写回。 |
| [protocol/ClientAssetPreviewProtocol.java](protocol/ClientAssetPreviewProtocol.java) | 请求跟踪、回执和取消的网络边界。此小层级由本文覆盖。 |

## 生命周期

共享服务先查磁盘缓存，未命中再请求并落盘；同一解析中的订阅共享工作。最后一个等待者关闭后，取消仍在跟踪的网络请求。结果成功送达后，订阅仍持有磁盘产物，直到显式 `close()`；不能在收到结果后遗忘订阅。

`resetConnection()` 清除等待项和持有者并重置协议，过期异步工作通过条目身份及缓存 generation 检查隔离。应用关闭时 `close()` 进一步释放缓存注册和 I/O 执行器。普通视图销毁只关闭自己的订阅。

缓存根目录是游戏目录下的 `geometry_nodes/caches/asset_previews`，服务端子目录根据稳定服务端身份散列生成。缓存注册 ID 保持 `geometry_node:asset.client.previews`。清理当前服务端或全部缓存时保留仍有持有者的服务端目录，通过异步屏障排序清理与后续读取、写入。

## 扩展与 UI 边界

新增来源或解析方式实现 `AssetPreviewProvider` 并注册；新增服务端预览格式还需遵守 core 的 descriptor、revision 和协议格式。公共 UI 在 `ui/components/integration/asset/preview` 中把产物交给 renderer，并在 UI 线程校验视图生命周期。

`AssetPreviewProviders.clearCurrentRemoteCache()` / `clearAllRemoteCaches()` 只处理服务产物。需要连同 UI 缩略图清理时，应由外层使用 UI 的 `AssetPreviewCaches` 装配入口。不要为了清理缩略图让本包依赖 UI。

磁盘缓存治理回归入口是 `src/test/java/com/mine/geometry_node/client/feature/asset/preview/ClientAssetPreviewCacheGovernanceTest.java`。
