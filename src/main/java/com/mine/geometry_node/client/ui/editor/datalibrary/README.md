# 数据库区域

`editor/datalibrary` 是 `AreaEditorType.DATA_LIBRARY` 的唯一 UI 实现根目录。
`DatabaseWindow` 是唯一的区域入口，负责页签、表面注册、世界对象/数据仓库的侧栏装配和子页生命周期。
子页不是另一个 Area，也不注册独立窗口。

| 目录 | 职责 |
| --- | --- |
| `library` | 数据仓库的目录树、条目表格、值编辑、拖拽手势与移动校验、条目检查器模型。`DataLibrarySnapshot` 只是渲染索引，不是第二份权威仓库。 |
| `objects` | 世界对象与实体共用的服务端查询表格，以及定位到视口的区域操作。全局实体和阵营成员复用同一套列、筛选、预览和行更新。 |
| `global` | 全局数据的分类导航、实体页、阵营列表及单个阵营的基本信息/关系/等级/成员子页。保留表单和订阅的生命周期归该目录所有。 |
| `inspector` | 本区域的选择展示模型、选择提供者协议和已注册的属性侧栏。展示等价性不比较回调；保留的控件始终派发到最新选择。 |
| `state` | 本区域的页签、筛选、列宽、侧栏和分割位置等 UI 状态。包调整不改变已有键名、版本和生命周期。 |
| `presentation` | 数据库翻译词汇和本区域的紧凑操作按钮样式。通用表格、尺寸转换、预览等直接使用公共组件。 |

## 公共逻辑边界

- `client/feature/datalibrary`、`client/feature/worldquery`、`client/feature/faction`：仓库 API、异步命令、网络适配和客户端数据处理；不依赖编辑器视图。
- `client/feature/datalibrary/DataLibraryEntryValues`：条目替换、值比较和序列化；视图不重复实现持久化规则。
- `ui/components/integration/metadata/RepositoryDataDictionaryBindings`：把仓库能力接到公共数据编辑器；该适配器提供独立编辑会话和冲突保护；独立管理窗口拥有 `openSession()` 返回的 binding 并在写入完成后释放，嵌入编辑器的共享 binding 由父宿主关闭。
- `ui/workspace/drag/payload/DataLibraryDragPayload`：跨区域传输协议。数据库手势和内部移动校验留在 `library`，图编辑器只依赖此协议。
- `ui/components`：通用表格、菜单、按钮、分割线、侧栏、预览，以及可复用的阵营/数据/实体登记控件。数据库内部复用不自动构成公共组件。
- `ui/components/integration/entity/editor`：可复用实体编辑弹窗，不是 Area；数据库通过 `client/integration/entity/WorkspaceEntityEditor` 接入选图和工作区路由。
- `ui/shell/registration`：注册数据库 Area 和属性侧栏。区域内部不重复实现注册体系。

## 状态与生命周期

业务快照留在仓库中；`UiStateContext` 只保存展示偏好和有限的内存状态。
隐藏页面暂停展示、菜单和监听，但不会撤销已经提交的仓库写入。
切换连接销毁旧表单并拒绝过期回调；工具栏操作还要校验当前连接与快照已就绪。
原生物品选择器可以暂时离开 ModernUI 宿主，提交仍以原连接和条目身份进行冲突检查。

实体页与阵营成员页使用注册侧栏，默认折叠。阵营成员查询在服务端结果上限之前固定
`ENTITIES + REGISTERED + 当前阵营`，筛选不能放宽此约束。
成员筛选和列宽使用有界共享状态域；逐个阵营的选择只保留在当前子页实例中，避免无限创建状态域。
已卸载成员使用登记快照，修改要求实体已加载且存活。旧快照缺失的阵营信息在实体下次加载时补齐。

阵营 UUID 仅作为内部引用，不在名称、提示和搜索中显示。
`FactionCatalogSubscription.open()` 负责进入页面时刷新，关闭只分离展示订阅。
`FactionRichField`、`FactionRelationsView` 等公共控件仍由 `components/integration/faction` 维护。

## 关键文件与新增代码位置

- `library/DataLibraryView` 协调仓库快照、树/表格、过滤和选择；`DataLibraryTreeLayout`、`EntryCard`、`Toolbar` 负责布局与行展示；`ValuePresentation/ValueEditors` 分开格式化和编辑提交。`DataLibrarySelection`、`ScrollAnchor` 保存展示状态，`EntityTypes` 获取实体类型展示信息，`DragSurface/DropPolicy` 管理数据库手势与内部落点校验。新增仓库视图交互放 `library`，条目替换/序列化规则放 `feature/datalibrary`。
- `objects/WorldObjectsView` 拥有 `ClientWorldObjectRepository.Session`、列与行绑定，`WorldObjectNavigation` 把选中对象定位到场景视口。它服务世界对象、全局实体与阵营成员查询；新增公共查询规则放 `feature/worldquery`，新表格交互留在此包。
- `global/GlobalDataView` 通过 `Section` 定义和 `GlobalDataSection` 生命周期接口组织分类；`EntityCatalogView` 复用对象表格，`FactionCatalogView` 管理列表与保留的 `FactionEditorView`，`FactionRanksView/FactionViewState` 管理等级子页和局部状态。新增全局分类在此实现 section，不新增 Area。
- `inspector/DatabaseSelection/DatabaseSelectionSource` 提供跨子页的选择投影；`DatabaseDetailsPanel` 是注册侧栏，`DatabaseInspectorViews` 负责控件复用。新增详情展示走此协议；外部可复用实体/数据编辑弹窗进入 `components/integration`。
- `state/DatabaseViewState` 定义本 Area 稳定状态键、版本和清洗，`presentation/DatabasePresentation` 提供翻译及本区域按钮外观。它们不承接仓库或公共主题职责。

## 线程与资源所有权

视图、草稿、选择及分批挂载由 MUI UI 线程拥有。仓库通知和连接 scope 通知先排入展示队列，再检查 generation、当前连接和快照是否就绪；展示刷新可以使用 `View.post()`。`UiTableBatchWork` 在 UI 队列中分批建行，不在后台线程创建 View；隐藏/切换 scope/销毁时取消旧批次和未完成的展示挂载。已提交命令、字典/实体编辑的确认和资源收尾使用独立 UI executor 或业务会话调度通道，不能依赖 View 仍挂载。

`DatabaseWindow` 拥有三个子页和侧栏装配；`onHide` 隐藏各页并保存展示状态，`onDispose` 移除仓库/scope 监听并销毁子页。`WorldObjectsView` 只在最终 dispose 关闭自己创建的查询 session；隐藏时停止展示和查询触发。`FactionCatalogView` 隐藏时关闭展示订阅并停用保留表单，销毁或切换连接时才 dispose 表单。`DataLibraryView` 管理自己的刷新任务、类型查询、菜单与拖拽目标，取消后不关闭共享仓库。

所有直接子目录及其小叶子均由本 README 覆盖；它们不是独立窗口/注册体系。
