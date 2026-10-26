# 客户端公共资产能力

本包为资产 Area、公共 picker 和其他客户端调用方提供同一套资产数据、目录读取、文件操作、预览产物和传输服务。这里不持有窗口、标签页或 ModernUI View，也不决定怎样打开图文档。

## 目录与职责

| 目录 | 关键入口与定位 |
| --- | --- |
| `model` | `AssetEntry` 统一本地文件与远端 `AssetDescriptor`；`AssetTypeRegistry`、`AssetType`、`AssetTypeAction` 描述类型、来源与动作能力。内置类型对齐 core 的 `AssetTypeCatalog`。 |
| [repository](repository/README.md) | 用 `AssetLocation`、`AssetQuery`、`AssetListing` 和可取消的 `AssetRequest` 统一目录读取；不实现窗口导航或选择状态。 |
| `path` | `AssetPathUtils` 处理远端路径与文件名；`AssetBrowserPathPolicy` 处理根目录、快捷访问配置路径和 `geometry_nodes/local_drafts`。 |
| `favorites` | `AssetFavoriteStore` 读写 `assetBrowser.favoriteAssetPaths`，使用本地仓库的路径键；支持文件及目录移动后的收藏路径更新。 |
| [file](file/README.md) | `LocalAssetService` 执行本地操作，`AssetFileTransactionManager` 协调结构变更与外部参与者。 |
| `remote` | `RemoteAssetClient` 管理远端权限能力、目录和文件管理请求；`RemoteGraphPathIndex` 仅索引已观察到的目录快照中的图路径，用于建议，不代表完整资产库。 |
| [preview](preview/README.md) | 解析本地或远端预览产物，管理订阅、网络请求与磁盘缓存；不解码或绘制 UI 缩略图。 |
| `task` | `AssetTask`、`AssetTaskContext` 定义任务执行、进度、取消与提交阶段协议；执行器、进度弹窗由调用方提供。 |
| [transfer](transfer/README.md) | `ClientAssetTransferService` 管理上传下载作业，`ClientAssetTransferPlanState` 管理提交前的远端计划请求。 |

## 依赖方向与装配

依赖方向是 `UI / integration → feature.asset → core、client.platform、client.network、client.config`。本包不依赖具体 editor、shell、图文档服务或 ModernUI。

- 公共浏览协调器、列表、导航、picker、对话框和缩略图控件位于 [ui/components/integration/asset](../../ui/components/integration/asset)。资产 Area 位于 [ui/editor/asset](../../ui/editor/asset)，二者共享公共组件和本包服务。
- [integration/asset](../../integration/asset) 连接工作区动作、传输流程、属性面板和缓存装配。`AssetBrowserRepositories.init()` 通过 `LocalAssetRepository.TagReader` 注入图标签读取，避免公共仓库反向依赖图实现。
- 开图等具体文档动作由 UI 的窄动作接口注入，实际装配通过 `integration.graph.GraphDocuments`；本包不承担文档打开、标签页切换或视图关闭。

## 生命周期与扩展

类型、仓库注册表和共享服务通常随客户端进程存在；目录请求、预览订阅由使用者取消或关闭。连接切换时，外层连接装配负责重置远端请求、预览和传输状态，旧连接结果不能继续更新新连接的视图。

新增类型先在 core 定义真实类型与协议能力，再补充客户端 `AssetTypeRegistry` 中的动作描述。新增预览通过 `AssetPreviewProviders` 注册数据 provider，绘制器在 UI 层注册。新增文件变更使用事务参与者协议协调外部状态；不要在文件服务中引用具体编辑器。新增任务通过 `AssetTaskContext` 上报进度，并保留提交前取消、提交后完成收尾的边界。

Java 包名不是资产标识。扩展或迁包时应保留类型 ID、缓存 ID、配置键、远端路径格式和传输 purpose 的既有含义。
