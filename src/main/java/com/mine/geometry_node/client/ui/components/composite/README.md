# 组合组件

本层组合 `base`、`theme` 和公共弹层/工作区协议，提供可复用交互。宿主提供数据、动作、提交规则和状态存储；完整通用契约见[组件总览](../README.md)。

| 子目录 | 关键文件与职责 |
| --- | --- |
| `button` | `UiSplitButton` 组合主动作与下拉动作。 |
| `display` | `UiTagChip`、`UiTagStrip` 展示标签、删除动作和标签集合。 |
| `input` | `UiSearchBar` 组合搜索输入与操作；`ColorSwatchView` 打开颜色编辑；`UiGradientStopEditor` 编辑渐变停靠点并管理预览/取消。 |
| `layout` | `UiPropertyRow` 组织属性标签和值；`UiFixedSplitLayout` 管理固定尺寸分栏、首选尺寸及用户调整通知。 |
| `list` | `UiNavigationRow` 提供导航选择行。 |
| `menu` | `UiContextMenu` 支持级联上下文菜单；`UiSearchableMenu` 和 `UiSearchMenuLayout` 提供搜索菜单；`UiGridMenu` 提供网格；`UiPopover` 管理锚点弹出。`UiRadialMenu`、`RadialMenuLayout`、`HeldMenuKey` 处理径向菜单布局和按住按键的开启/释放。 |
| `navigation` | `UiSubTabs` 提供页面内部子页签。 |
| `overlay` | `ModalWindowView` 提供模态窗口骨架及逻辑 owner；`ExpandedTextInputOverlay`、`ColorPickerDialog` 组合长文本和颜色编辑。 |
| `sidebar` | `EditorSidebar` 组织可注册面板；`SidebarLayoutController` 管理显隐与布局。`api` 下的 `SidebarPanel`、Context、Definition、Factory、Registry、Scope 定义扩展协议和面板适用范围。 |
| `table` | `UiTableColumns` 管理稳定列 ID、宽度与列位置；`UiTableViewport` 同步表头/内容和横向滚动；`UiTableRow/RowFrame` 组织行；`UiTableFilterHeader`、`UiColumnFilterPopover` 提供排序筛选入口；`UiTableStyle` 定义局部视觉；`UiTableBatchWork` 将大批挂载拆成 UI 队列中的有界工作。 |

## 所有权与扩展

视图、交互、弹层和分批挂载都由 UI 线程管理，`UiTableBatchWork` 不把 View 创建移到后台。宿主在隐藏/销毁时取消批次、关闭菜单、解除监听；业务查询与对象编辑仍交给外部能力。

列宽和分栏组件只导出/恢复展示快照并通知用户操作，不自行写文件。宿主先恢复，再订阅 `addUserStateListener`，关闭时解除订阅；自动测量和布局通知不能当作用户写入。sidebar 面板根据 owner 生命周期刷新，业务订阅也由相应面板/宿主解除。

新增通用交互组合进入相应子目录；新增业务侧栏的具体实现放 `editor/<能力>/sidebar`，通过 `sidebar/api` 注册。传入自定义子 View 时宿主负责将同一 `UiTheme` 继续传递；内容颜色保持业务语义。
