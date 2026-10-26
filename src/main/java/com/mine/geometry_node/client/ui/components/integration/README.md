# 游戏与业务集成组件

本层把公共控件接到 Minecraft 原生绘制和 `client/feature` 能力，供不同页面复用。依赖可以向 `base/composite`、公共弹层协议和业务 API 下行；跨编辑器路由通过注入协议或 `client/integration` 装配，不把完整 Area 放到这里。

| 子目录 | 关键文件与职责 |
| --- | --- |
| [asset](asset/README.md) | 公共文件浏览、资产选择、导航、文件操作交互、任务进度和缩略图；Area 和 picker 共用，业务仓库位于 `feature/asset`。 |
| `entity` | `EntityRegistrationView` 显示实体登记状态；`editor` 提供可复用实体编辑弹窗、`EntityEditDraft/Session` 和属性/类型/标签/阵营子视图。其 `graph` 子目录通过 `EntityGraphBindings`、Actions、Filter 连接关联图列表；工作区选图由外部适配。 |
| `equipment` | `EntityEquipmentView` 组合装备、背包和预览；`EntityEquipmentDraft` 保存分离草稿；Interaction、PageLayout、PreviewPreparation/Templates 与 slot/preview View 分别负责交互、排版和预览准备。宿主决定何时成批提交及接收确认。 |
| `faction` | `FactionRichField`、RelationsView、MembershipsView 提供阵营表单；`FactionCatalogSubscription` 统一刷新与订阅；`FactionIntegerInput`、TextViews、Ui 提供局部字段与展示工具。 |
| `metadata` | `DataDictionaryBinding` 适配自定义数据读写；`RepositoryDataDictionaryBindings` 连接业务仓库；Draft/Session 分离已完成修改与未完成表单；SummaryCell、Preview、Editor/Content 分别提供摘要、预览、完整和嵌入编辑。 |
| `nativepreview` | `NativePreviewHost`、`ViewportNativePreview` 定义共享原生预览协议；Layer 汇总绘制，View 管理宿主注册或 fallback surface，`DeferredEntityPreviewGuard` 保护延迟实体预览。 |
| `overlay` | `InventoryItemPickerOverlay` 显示物品选择，`ItemStackTooltipOverlay` 显示原生物品提示；复用公共宿主和焦点规则。 |
| `text` | `ModernDialogueText` 将领域对话文字投影为 ModernUI 可显示文本。 |
| `valuepreview` | `ItemSlotView`、Item/EntityPreviewCell、EntityTemplatePreviewView、MarkerTypePreview、QuestHintView 提供内容预览；`PreviewZoomController` 管理实例缩放，`ValuePreviewStyle` 只控制装饰。 |

## 线程和资源所有权

视图树、草稿表单及绑定切换在 UI 线程处理。业务通知先投递 UI 队列再校验 generation/连接/owner。Minecraft 原生渲染读取已发布的几何、可见性、缩放和内容快照，不从渲染回调遍历 MUI View 树。`ViewportNativePreviewView` attach 时注册共享宿主或创建 fallback，detach 时撤销注册、清除变换及监听；旧 surface 的回调不得绘制新实例。

公共 metadata 编辑窗口调用 `binding.openSession()` 并拥有该独立 binding，逻辑会话在写入完成后关闭它；嵌入式编辑器借用父 binding，父宿主负责关闭。`suspend()` 只释放临时菜单/焦点并保留表单，`dispose()` 丢弃未完成表单；已发出写入仍等待结果。摘要单元格不逐行读取完整字典。

`EntityEditSession` 是 UI 线程控制器，读取可以取消；已完成的编辑通过会话级调度器提交并等待确认，不能绑定到会被移除的 View 定时回调。关闭展示不丢弃已排队写入，也不自动重试结果未知的命令。装备组件只产生分离快照，不自行改写实体。业务仓库和运行时资源由其原 owner 管理。

新增可跨页面复用的业务控件放对应能力子目录；仅数据库内部复用的逻辑留在 `editor/datalibrary`。新增原生预览复用 `nativepreview` 的注册与快照机制，避免每个调用方另建一套渲染生命周期。
