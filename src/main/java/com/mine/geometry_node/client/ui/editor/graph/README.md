# 图编辑器 Area

`GraphEditorWindow` 是 `GRAPH_EDITOR` Area 入口，实现 `ViewportSurface`，组合标签画布与公共图属性侧栏。图会话和编辑命令位于 `client/feature/graph`；`ui/document/DocumentManager` 只提供标签激活门面。

## 关键入口

| 文件 | 职责 |
| --- | --- |
| `GraphViewportPanel` | 标签栏、当前标签选择和跨 Area 拖入；监听文档变化，保持已绑定会话的控件及未提交输入。资产读取由 `integration/graph/GraphAssetImports` 装配。 |
| `Viewport` | 画布 View、分层显示、菜单、焦点和原生预览宿主；协调组件生命周期。 |
| `ViewportController` | 把视口动作转为业务命令，绑定当前 `GraphSession/EditorContext`，维护本视口各会话的显示状态。 |
| `ViewportCamera`、`ViewportSessionState` | 画布坐标、缩放以及单个视口的会话状态；初始值可从 `GraphSessionViewState` 取得。 |
| `CanvasVisualItem` | 画布视觉对象的公共交互抽象。 |
| `ViewportImageExporter` | 画布导出，编码/文件写入使用独立 IO executor，导出资源在任务完成后释放。 |
| `GraphUiStyle` | 图编辑器局部排版/视觉参数，颜色引用公共主题。 |

## 子目录

| 直接子目录 | 职责及内部层级 |
| --- | --- |
| `action` | ViewportAction/Id/Request/State/Registry/Sink 定义可执行动作、状态查询与统一分发；菜单、工具栏、快捷键共用。 |
| `interaction` | InteractionManager/Context 和 ViewportEventDispatcher 分发手势；ConnectionInteraction 管理连线交互，ContextMenuRouter 路由菜单，KeyManager 适配快捷键，GraphInteractionConfig 保存图交互参数。 |
| `node` | UINode/UIRerouteNode、NodeLayer、NodeVisualAdapter 管理节点视觉；NodeLayout/Engine/Metrics 负责端口和提示布局，NodeOverlayController 管理局部覆盖。`hint` 的 Factory/ValueBinder/Utils、NumericInputSpec 与 ButtonHintActionDispatcher 选择提示、绑定值并派发按钮动作；`hint/renderers` 包含文本/数值/颜色/选择/路径/物品/实体/数据引用提示及视口缩放适配，`hint/overlays` 保存商店编辑弹层与草稿。注释内容构建在 `feature/graph/query`。 |
| `connection` | ConnectionLayer、ConnectionNodeVisual 绘制与命中连线。 |
| `frame` | FrameLayer、UIFrame、FrameVisualAdapter 管理 Frame 视觉，FrameBoundsCalculator 计算边界。 |
| `layers` | BackgroundLayer 绘制画布背景/网格。 |
| `menu` | ViewportMenu、PortMenu、GroupNodeMenu、FrameMenu 组装各上下文菜单，动作交给统一分发；GraphMenuOverlay 统一局部属性菜单的有效期，目标 visual 被替换后不能提交。 |
| `selection` | ViewportSelection 保存本视口的选择及相关通知，不拥有图数据。 |
| `toolbar` | ViewportToolbar、ToolStrip、ToolButton、ToggleButton 组合画布工具动作。 |


## 依赖、线程与资源

输入与菜单通过 action/controller 调用 `feature/graph/edit` 的命令；撤销/重做由 `client/history` 与业务编辑上下文管理。节点 View、Hint 与属性侧栏只保存展示/输入草稿，可复用的资产选择、实体编辑、装备、数据字典和原生预览直接使用 `components/integration`。

UI 线程拥有画布 View、焦点、手势、相机和选择。每个 Viewport 独立保存自己的会话视口状态，`GraphSessionViewState` 只提供文档级共享默认值；关闭一个 Area 不关闭共享图会话。`onHide` 停用面板、保存侧栏、关闭临时交互，`onDispose` 解除属性绑定、文档监听和控制器绑定，画布及工具栏不能再重新订阅。切换会话先取消旧手势/菜单。原生预览通过 NativePreviewHost 注册，渲染端读取发布的变换/内容快照，不能遍历 MUI 视图树。

展示通知可 `View.post()` 并在执行时校验当前会话/owner；已提交操作的确认、事务收尾与资源释放使用独立 UI executor 或业务会话的调度通道，不能依赖 View 挂载。IO executor 只处理适合后台执行的数据/文件工作；游戏世界和 Screen 操作通过客户端线程入口。

新增画布动作先进入 `action` 并由 controller 对接命令，新增持久图编辑命令进入 `feature/graph/edit/commands`；新增 Hint 放 `node/hint`，共享业务控件放 `components/integration`；新增属性能力经 Target/Snapshot 和注册侧栏接入。不要把图编辑命令移回 `ui/document/edit`。

## 共享能力的位置

图编辑器和资产浏览器共用的属性协议、快照、面板及任务属性控件统一放在 `ui/components/integration/graph/properties`，本 Area 不再拥有它们。`integration/graph/GraphSessionPropertiesTarget` 将共享协议接到图会话；`GraphEditorPreviews` 装配对话/商店/任务本地预览。属性侧栏注册 ID 与业务命令保持稳定。

画布拖入在接受时捕获会话、图 scope 和画布 dp 坐标；异步完成回 UI 线程并校验生命周期，过时结果不能落入其他文档或图组。磁盘读取、下载临时文件维护由 integration 独立完成，关闭窗口也不能遗留下载临时文件。
