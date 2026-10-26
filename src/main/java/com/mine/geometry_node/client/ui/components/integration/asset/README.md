# 公共资产浏览与选择 UI

`AssetPickerDialog` 与资产 Area 共用 `browser/AssetFileBrowserPanel` 和 `navigation/AssetNavigationPanel`。此目录负责公共交互与展示；资产模型、仓库、路径、文件事务、收藏及 preview provider 位于 `client/feature/asset`。图文档等跨能力装配位于 `client/integration/asset`，不要求 picker 依赖具体 Area 或 shell。

## 子目录与关键入口

| 文件/直接子目录 | 职责 |
| --- | --- |
| `AssetPickerDialog/AssetPickerOptions` | 选择模态窗口及来源、目标、过滤和初始目录选项。 |
| `AssetBrowserCoordinator` | 浏览器向宿主请求导航、上传/下载、远端建图和侧栏变化的接口，Area/picker 各自提供实现。 |
| `action` | AssetLibraryActionId/Registry 统一资产 UI 动作标识与分发。 |
| `browser` | AssetFileBrowserPanel 管理文件列表、选择和刷新；AssetFileItemView、FileContentLayout、AssetViewMode 负责条目与布局；ActionController/Clipboard 组织操作；AssetBrowserDocumentActions 为文档副作用提供注入点。 |
| `navigation` | AssetNavigationPanel 提供本地、收藏和远端目录导航。 |
| `dialog` | AssetDialogBase 复用弹窗基础；Confirm/OverwriteConfirmDialog 确认操作，File/FolderPickerDialog 保留旧路径回调入口，TransferProgressDialog 展示任务进度。 |
| `drag` | AssetDragPayload 提供资产条目的跨区域拖拽协议。 |
| `image` | ImageThumbnailView/Cache 管理图片缩略图订阅、解码、引用与回收。 |
| `schematic` | SchematicThumbnailView/Cache、Renderer、MaterialResolver 管理结构缩略图与材质解析。 |
| [preview](preview/README.md) | AssetPreviewView、Renderer/Renderers 根据 provider 产物选择展示实现，AssetPreviewCaches 聚合清理 UI 缩略图与远端预览产物。 |
| `task` | AssetTaskController 将 feature 定义的 AssetTask 放到 IO executor，管理取消、进度、UI 完成派发和被放弃结果的释放。 |

工作区通过 `AssetBrowserAreaBindings` 注入属性、文档保存订阅和传输能力，由 `WorkspaceAssetBrowserBindings` 实现。文档适配通过 `GraphDocuments` 打开图、删除前关闭受影响文档、提交后刷新文件引用；picker 不安装图文档动作。`AssetBrowserRepositories.init()` 在客户端装配时安装图标签读取器，保持图读取事务边界，避免 feature/asset 反向依赖图 UI。

## 选择契约

```java
var options = new AssetPickerOptions(
        AssetPickerOptions.Sources.REMOTE,
        AssetPickerOptions.Target.FILE,
        entry -> acceptsFile(entry),
        new AssetLocation.Remote(initialDirectory, false));
AssetPickerDialog.show(anchor, title, options, entry -> useSelection(entry));
```

返回 `feature/asset/model/AssetEntry`：远端 `path()` 相对服务器资产根，本地由 `localFile()` 标识。取消不调用结果回调；成功先关闭模态，再且仅一次调用结果回调。

来源为 LOCAL/REMOTE/BOTH，目标为 FILE/FOLDER/FILE_OR_FOLDER。文件 predicate 同时控制可见性和确认，目录保持可导航；目录目标隐藏文件。确认时再次检查来源、目标和 predicate，浏览器内触发选择也遵循同一校验。收藏是集合，不能选作当前目录；选择当前远端目录要求该位置已经成功列举，加载中或失败状态不能确认。

初始目录使用现有 `AssetLocation.Local/Remote`，null 在允许本地时使用本地草稿目录，否则使用远端根。初始远端路径不隐式建目录；BOTH 模式延迟发现远端能力时，仅在用户尚未另行导航时恢复初始远端目录。REMOTE 模式始终留在远端，即使能力不可用。旧 FilePickerDialog/FolderPickerDialog 保留原工厂与回调格式；文件路径推断/规范化规则和目录选择器的显式建目录动作保持各自入口的契约。

可选末尾 `UiTheme` 默认 EDITOR，控制模态外壳、遮罩、状态与动作；复用浏览器/导航保持其现有编辑器样式。`show` 从 anchor 查找 OverlayHost 并绑定保留的 UiWindowLifetime，用于嵌套模态所有权和焦点归还。

## 线程、任务与缓存所有权

浏览器列表、选择、导航与子 View 在 UI 线程更新；后台仓库结果先经展示调度通道，再校验请求 generation/位置。图/文件 IO 使用 feature 能力及任务 executor。`AssetTaskController` 的 UI 完成通道独立于 View 挂载，提交收尾与当前 owner 的展示回调分开：已提交动作仍完成，失效 owner 不再更新进度/列表，被放弃的 AutoCloseable 结果会释放。隐藏/停用时取消可取消任务和进度展示，不把已提交事务当作未发生。

图片/结构缓存拥有工作线程和底层缩略图，View 只持有订阅/handle，detach 时释放引用；缓存按引用及淘汰规则回收，单个 View 不关闭共享 executor。`AssetPreviewCaches` 同时清理 UI 缩略图和远端预览产物；`feature/asset/preview/AssetPreviewProviders` 只清理其远端产物。provider 到 View 的线程与 generation 契约见[预览说明](preview/README.md)。

新增可复用浏览/选择交互放本目录，新增仓库或文件事务放 `feature/asset`，图文档和工作区侧栏集成放 `client/integration/asset`，Area 专用工具栏与窗口布局留在 `editor/asset`。
