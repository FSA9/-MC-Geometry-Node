# 资产预览回调与生命周期

本目录承接 `client/feature/asset/preview` 的预览产物，仅负责 UI 展示，不实现资产下载协议或仓库。

| 文件 | 职责 |
| --- | --- |
| `AssetPreviewView` | 订阅 provider、保存订阅 generation、切换缩略图与 fallback，并管理 preload/attach/detach。 |
| `AssetPreviewRenderer` | 将产物转换为预览 View 的扩展接口。 |
| `AssetPreviewRenderers` | 按产物选择并构造渲染实现。 |
| `AssetPreviewCaches` | 聚合图片/结构 UI 缩略图和远端预览缓存清理。 |

本目录没有子目录。`LocalAssetPreviewProvider` 通过 `platform/ClientThread` 在 Minecraft 客户端线程交付结果；`RemoteAssetPreviewProvider` 沿用 `ClientAssetPreviewService` 的回调契约。

`AssetPreviewView` 的订阅、挂载和卸载由 MUI UI 线程管理。provider 的展示回调只调用 `View.post()`，在 UI 队列执行时校验订阅 generation，再创建缩略图、增删子视图和切换 fallback。不能在 provider 回调线程读取 generation 后直接修改视图树。此投递仅用于可丢弃的展示刷新；已提交事务的收尾与资源释放应由独立 UI executor/业务会话负责，不能依赖 View 的待挂载队列。

`preload()` 可以在挂载前订阅，结果由 View 的待挂载队列保留；挂载不重复订阅。detach 先递增 generation、关闭订阅，再移除缩略图并恢复 fallback。已入队或取消后才到达的旧展示结果因此失效，重新挂载会创建新一代订阅。

预览 View 拥有订阅及其子视图；图片/结构缓存拥有解码产物与共享缓存资源，provider 拥有其读取/下载过程。新增 UI 预览类型实现 renderer；新增产物生成/下载方式扩展 `feature/asset/preview`，不要在本目录添加网络协议。

既有回归用例 `AssetPreviewViewTest` 覆盖跨线程交付、队列执行前 detach、重新订阅、取消后的迟到回调，以及挂载前 preload；这里记录契约，不将局部线程修复等同于冻结问题根因的结论。
