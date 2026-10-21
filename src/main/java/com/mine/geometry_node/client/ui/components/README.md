# UI 组件层级规则与推荐入口

新增界面先按职责选择组件，再查看下方使用契约。尺寸与文字使用 `UIUtils` 的项目缩放，颜色使用 `UiTheme`；业务查询、数据写入、历史命令和服务器交互由调用方负责。

## 层级与选择规则

| 层级 | 职责 | 选用边界 |
| --- | --- | --- |
| `base` | 单一职责的绘制、输入行为、布局、选择计算，以及弹层输入和生命周期机制。 | 需要一个基础能力时直接使用；不导入 `composite` 或 `integration`，不创建完整业务编辑器。 |
| `composite` | 组合基础能力，提供完整的搜索栏、标签、分栏、菜单、表格、颜色编辑、文本编辑和 sidebar。 | 已有完整交互优先复用，不在调用方重新拼装同一套行为；业务数据与提交仍由调用方提供。 |
| `integration` | ModernUI 与 Minecraft 原生渲染、游戏内容和项目运行时之间的适配。 | 展示物品、实体、标记类型或选择背包物品时使用；原生渲染实现不下沉到 `base`。 |

- 分层看实际职责和依赖，不按继承自 `View`、类大小或是否只包含工具方法判断。`ColorSwatchView` 会打开完整颜色编辑器，因此属于 `composite`；`UiSelectButton` 只显示当前值和箭头，因此属于 `base`。
- 搜索输入用 [UiSearchInput](base/input/UiSearchInput.java)，工具栏搜索用 [UiSearchBar](composite/input/UiSearchBar.java)，菜单搜索区用 [UiSearchMenuLayout](composite/menu/UiSearchMenuLayout.java)，完整选择菜单用 [UiSearchableMenu](composite/menu/UiSearchableMenu.java)。
- 弹层输入、焦点、外部点击、Esc、Area 生命周期和安全移除机制属于 `base`；菜单内容、颜色编辑、文本编辑属于 `composite`；背包选择和物品提示属于 `integration`。
- helper 跟随所属职责和使用者归层；纯算法、无状态或不引用 Minecraft 并不自动构成下沉 base 的理由。只有具备独立的基础职责和实际复用需求时才考虑提取，不把每个纯算法强拆到 base。原生预览的 `PreviewZoomController`、`ValuePreviewStyle` 留在 `integration/valuepreview`。
- `base` 可以使用项目的主题、缩放及 Area 生命周期接口；这里的基础层不等同于脱离项目的独立 UI 库，但禁止直接依赖 Minecraft、core 数据模型及具体编辑器。完整模态弹层通过 `OverlayMount` 接入 shell，并保留独立宿主路径。
- 组件内允许的依赖方向为 `integration → composite → base`，也允许 `integration → base`；禁止反向依赖。`composite` 可以接收纯数据类型，但不直接调用 Minecraft 渲染、世界状态或具体编辑器／运行时实现；本地化 `Component` 是允许的适配。编辑器专用拼装留在对应编辑器。
- 迁移必须更新 package、调用方与测试引用；不保留旧包名转发类或平行实现。本页组件链接统一使用分层后的路径。
- `ComponentLayeringTest` 检查生产源码的层级边界、目录归属和包声明，阻止后续改动重新混入旧目录或产生反向依赖。

## 各层清单

以下列出全部 72 个生产组件源文件：23 个 base、36 个 composite、13 个 integration。内部实现也列出，供维护时确认归属，不代表都应被调用方直接使用。

### base：基础能力

| 分类 | 类 | 职责 |
| --- | --- | --- |
| `button` | [UiActionButton](base/button/UiActionButton.java)、[UiIconButton](base/button/UiIconButton.java) | 动作与图标按钮的状态、命中和视觉。 |
| `display` | [UiDisplayText](base/display/UiDisplayText.java) | 只读文字、完整值提示和行点击透传。 |
| `icon` | [SvgIconView](base/icon/SvgIconView.java)、[UiIconColors](base/icon/UiIconColors.java)、[VectorIconView](base/icon/VectorIconView.java) | 图标绘制与统一着色。 |
| `input` | [UiCheckBox](base/input/UiCheckBox.java)、[UiNumericInput](base/input/UiNumericInput.java)、[UiSearchInput](base/input/UiSearchInput.java)、[UiSearchSubmit](base/input/UiSearchSubmit.java)、[UiSelectButton](base/input/UiSelectButton.java)、[UiTextInput](base/input/UiTextInput.java) | 文本、搜索输入、搜索提交协议、复选框、数值输入与下拉触发外观。 |
| `layout` | [ResizableDivider](base/layout/ResizableDivider.java)、[TagFlowLayout](base/layout/TagFlowLayout.java)、[ThinResizableDivider](base/layout/ThinResizableDivider.java) | 分割线拖动与已有子控件的换行排列。 |
| `list` | [UiListSelection](base/list/UiListSelection.java) | 按稳定键计算单选和多选。 |
| `menu` | [UiMenuFocus](base/menu/UiMenuFocus.java)、[UiMenuItems](base/menu/UiMenuItems.java)、[UiMenuPanelLayout](base/menu/UiMenuPanelLayout.java)、[UiPopupOverlay](base/menu/UiPopupOverlay.java) | 弹层输入与生命周期底座、焦点归还、条目视觉和限高面板测量。 |
| `scroll` | [UiHorizontalScrollView](base/scroll/UiHorizontalScrollView.java)、[UiScrollBehavior](base/scroll/UiScrollBehavior.java)、[UiScrollView](base/scroll/UiScrollView.java) | 纵横滚动及内部共享行为。 |

### composite：完整组合组件

| 分类 | 类 | 职责 |
| --- | --- | --- |
| `button` | [UiSplitButton](composite/button/UiSplitButton.java) | 组合两个独立动作的分段按钮。 |
| `display` | [UiTagChip](composite/display/UiTagChip.java)、[UiTagStrip](composite/display/UiTagStrip.java) | Tag 展示、删除入口、单行标签组及溢出提示。 |
| `input` | [ColorSwatchView](composite/input/ColorSwatchView.java)、[UiGradientStopEditor](composite/input/UiGradientStopEditor.java)、[UiSearchBar](composite/input/UiSearchBar.java) | 完整搜索栏、带选择器的颜色块、渐变色标编辑。 |
| `layout` | [UiFixedSplitLayout](composite/layout/UiFixedSplitLayout.java)、[UiPropertyRow](composite/layout/UiPropertyRow.java) | 属性行和固定尺寸分栏。 |
| `list` | [UiNavigationRow](composite/list/UiNavigationRow.java) | 名称、数量与操作组成的导航行。 |
| `menu` | [UiContextMenu](composite/menu/UiContextMenu.java)、[HeldMenuKey](composite/menu/HeldMenuKey.java)、[RadialMenuLayout](composite/menu/RadialMenuLayout.java)、[UiGridMenu](composite/menu/UiGridMenu.java)、[UiPopover](composite/menu/UiPopover.java)、[UiRadialMenu](composite/menu/UiRadialMenu.java)、[UiSearchMenuLayout](composite/menu/UiSearchMenuLayout.java)、[UiSearchableMenu](composite/menu/UiSearchableMenu.java) | 可搜索菜单、网格菜单、任意内容 popover、扇形菜单及配套布局和按键协议。 |
| `overlay` | [ModalWindowView](composite/overlay/ModalWindowView.java)、[ColorPickerDialog](composite/overlay/ColorPickerDialog.java)、[ExpandedTextInputOverlay](composite/overlay/ExpandedTextInputOverlay.java) | 完整颜色编辑、长文本与富文本编辑；文本编辑含 PortType、RichTextValue 适配，不属于通用输入底座。 |
| `sidebar` | [EditorSidebar](composite/sidebar/EditorSidebar.java)、[SidebarLayoutController](composite/sidebar/SidebarLayoutController.java) | 标签栏、内容切换、拖动排序、展开收起和编辑器布局联动。 |
| `sidebar/api` | [SidebarPanel](composite/sidebar/api/SidebarPanel.java)、[SidebarPanelContext](composite/sidebar/api/SidebarPanelContext.java)、[SidebarPanelDefinition](composite/sidebar/api/SidebarPanelDefinition.java)、[SidebarPanelFactory](composite/sidebar/api/SidebarPanelFactory.java)、[SidebarPanelRegistry](composite/sidebar/api/SidebarPanelRegistry.java)、[SidebarPanelScope](composite/sidebar/api/SidebarPanelScope.java) | 面板、上下文、作用域、工厂、定义和注册表；属于编辑器扩展协议，不作为 base API。 |
| `table` | [UiColumnFilterPopover](composite/table/UiColumnFilterPopover.java)、[UiTableBatchWork](composite/table/UiTableBatchWork.java)、[UiTableColumns](composite/table/UiTableColumns.java)、[UiTableFilterHeader](composite/table/UiTableFilterHeader.java)、[UiTableRow](composite/table/UiTableRow.java)、[UiTableRowFrame](composite/table/UiTableRowFrame.java)、[UiTableStyle](composite/table/UiTableStyle.java)、[UiTableViewport](composite/table/UiTableViewport.java) | 列模型、行与单元格、横向视口、表头筛选、样式和分帧建行。 |

### integration：Minecraft 与运行时适配

| 分类 | 类 | 职责 |
| --- | --- | --- |
| `nativepreview` | [NativePreviewHost](integration/nativepreview/NativePreviewHost.java)、[ViewportNativePreview](integration/nativepreview/ViewportNativePreview.java)、[ViewportNativePreviewLayer](integration/nativepreview/ViewportNativePreviewLayer.java)、[ViewportNativePreviewView](integration/nativepreview/ViewportNativePreviewView.java) | 共享原生渲染 surface、注册协议、坐标变换、裁剪、attach/detach 和独立 surface 回退。 |
| `overlay` | [InventoryItemPickerOverlay](integration/overlay/InventoryItemPickerOverlay.java)、[ItemStackTooltipOverlay](integration/overlay/ItemStackTooltipOverlay.java) | 读取客户端背包的选择器与从 ItemStack 获取内容的提示层。 |
| `valuepreview` | [EntityPreviewCell](integration/valuepreview/EntityPreviewCell.java)、[EntityTemplatePreviewView](integration/valuepreview/EntityTemplatePreviewView.java)、[ItemPreviewCell](integration/valuepreview/ItemPreviewCell.java)、[ItemSlotView](integration/valuepreview/ItemSlotView.java)、[MarkerTypePreview](integration/valuepreview/MarkerTypePreview.java)、[PreviewZoomController](integration/valuepreview/PreviewZoomController.java)、[ValuePreviewStyle](integration/valuepreview/ValuePreviewStyle.java) | 物品、实体和表格预览单元格；标记预览适配项目运行时注册表，缩放与样式辅助类跟随预览。 |

`InventoryItemPickerOverlay.ItemStackView` 当前是公开嵌套展示组件，背包选择器、`ShopMenuFragment` 和 `ShopEditorOverlay` 都在使用。它随外层文件归入 `integration/overlay`；复用时应识别它是物品展示能力，不能因嵌套在选择器中就下沉到 base。原生桥接只认识 `NativePreviewHost` 协议，由 Graph 的 `Viewport` 实现宿主，不依赖具体编辑器类。

`ItemSlotView` 依赖 `ViewportNativePreviewView` 和 `ItemStackTooltipOverlay`；`EntityPreviewCell` 组合 `EntityTemplatePreviewView` 与 base 的 `UiDisplayText`。这些依赖保留在 integration 内或指向 base。`ColorSwatchView` 直接打开 `ColorPickerDialog`，两者一起归入 composite，避免基础颜色块反向依赖完整弹层。

`EditorSidebar` 组合基础控件并安装 `SidebarPanelRegistry` 中的面板；`SidebarLayoutController` 组合固定分栏或权重布局。具体面板由编辑器实现并在 shell 注册。`SidebarPanelContext.requireOwner()` 的具体 owner 类型由面板工厂检查，不进入基础组件。

## 契约导航

原有使用契约继续保留在本页；组件源文件链接均指向新层级：

- [按用途选择](#按用途选择)、[菜单与弹层](#菜单与弹层)、[表格](#表格)。
- [原生游戏内容与专用覆盖层](#原生游戏内容与专用覆盖层)、[预览缩放（默认关闭交互）](#预览缩放默认关闭交互)。
- [公共输入与生命周期契约](#公共输入与生命周期契约)、[新组件与调用约定](#新组件与调用约定)、[界面状态接入](#界面状态接入)。

## 按用途选择

| 用途 | 推荐入口 | 职责与使用方式 |
| --- | --- | --- |
| 文字动作按钮 | [UiActionButton](base/button/UiActionButton.java) | `Role` 选择语义配色，`Density` 选择文字密度；统一悬停、按下、禁用、焦点反馈。切换开关使用 `Palette.toggle(selected)`，只调用 `setSelected()` 不会替换调色板。 |
| 图标动作按钮 | [UiIconButton](base/button/UiIconButton.java) | 包装图标并统一命中尺寸、背景和状态；不要直接把 SVG 当按钮。 |
| 左右分段按钮 | [UiSplitButton](composite/button/UiSplitButton.java) | 一个外框、两个独立动作；如吸附开关＋配置下拉。组合 `UiActionButton`；外层 `setEnabled()` 同步控制两侧按钮。 |
| SVG／简单矢量图标 | [SvgIconView](base/icon/SvgIconView.java)、[VectorIconView](base/icon/VectorIconView.java) | 仅绘制；统一着色使用 [UiIconColors](base/icon/UiIconColors.java)。 |
| 文本输入、错误提示 | [UiTextInput](base/input/UiTextInput.java) | 输入框视觉、鼠标手势保护、`setErrorMessage()` 与可选 `setEditActions(confirm, cancel)`；输入草稿、解析、提交和撤销仍由调用方决定。 |
| 搜索输入 | [UiSearchInput](base/input/UiSearchInput.java) | 继承文本输入视觉，增加单行约束与 `setOnQueryChanged()`；程序设置文本也会通知。 |
| 输入＋方形查找按钮 | [UiSearchBar](composite/input/UiSearchBar.java) | 组合搜索输入和 32×32 逻辑像素按钮；点击或 Enter 提交，键入不自动查询；外层禁用同步控制输入和按钮，保留草稿。 |
| 多输入共享搜索提交 | [UiSearchSubmit](base/input/UiSearchSubmit.java) | `bind(button, action, inputs...)` 统一普通／小键盘 Enter、过滤重复按键；不改草稿、不执行筛选。资产浏览器双输入使用此入口。 |
| 复选框 | [UiCheckBox](base/input/UiCheckBox.java) | `setChecked()`／`setIndeterminate()` 静默同步勾选／半选状态；用户点击半选时全选并通知监听器。数据集合由调用方维护。 |
| 数值拖动、步进、内联编辑 | [UiNumericInput](base/input/UiNumericInput.java) | 通过 `Binding`、`Spec` 接入数据与数值规则；普通设置数值文本也可直接组合 `UiTextInput` 和校验。 |
| 下拉触发按钮 | [UiSelectButton](base/input/UiSelectButton.java) | 仅显示当前值和箭头；菜单内容与选中数据由调用方提供。 |
| 颜色块预览与选择 | [ColorSwatchView](composite/input/ColorSwatchView.java) | 透明棋盘背景、颜色选择器；通过 supplier 读取颜色、回调提交，组件不绑定节点或历史。 |
| 渐变显示与色标拖动 | [UiGradientStopEditor](composite/input/UiGradientStopEditor.java) | `Style` 控制尺寸，`Binding` 分离临时预览与最终提交；取消／禁用／移除／窗口失焦时回到已提交值，调用方校验提交快照并维护撤销。 |
| 颜色选择 | [ColorPickerDialog](composite/overlay/ColorPickerDialog.java) | 完整颜色编辑弹层，使用自身展示入口管理生命周期。 |
| 名称＋数量导航行 | [UiNavigationRow](composite/list/UiNavigationRow.java) | 组合一个按钮及被动文字层；通过 `button()` 绑定操作，`setPalette()` 更新状态。 |
| 单选、Ctrl／Shift 多选 | [UiListSelection](base/list/UiListSelection.java) | 基于稳定键与当前显示顺序计算选择；复选框独立增减集合，不当作普通行单选。 |
| 标签＋值＋末端操作 | [UiPropertyRow](composite/layout/UiPropertyRow.java) | 只负责对齐和窄布局；调用方传入值控件、复制按钮和 `Layout` 尺寸。不会自动读写对象。固定尺寸值（例如勾选框）使用带 `valueParams` 的构造方法，尾部自动补足空白；不要用弹性键列将值推到右端。 |
| 固定尺寸边栏／上下分栏 | [UiFixedSplitLayout](composite/layout/UiFixedSplitLayout.java) | 保存独立逻辑尺寸，剩余区域伸缩；适合数据库目录和属性栏，避免多分割线比例联动。测量及拖拽限位使用分割条含边距的布局占用量，保留完整透明命中区。 |
| 分割线输入与比例分栏 | [ResizableDivider](base/layout/ResizableDivider.java) | 通用拖动处理；`weighted()` 用于按比例分配的兄弟面板。`Orientation` 表示拖动轴。 |
| 细线外观分割线 | [ThinResizableDivider](base/layout/ThinResizableDivider.java) | 继承同一输入逻辑；始终只绘制 1dp 细线，悬停／拖动变蓝，背景透明。保留 8dp 命中区，通过负边距覆盖两侧面板，布局只占细线宽度，不露出父容器底色。 |
| 自动换行布局 | [TagFlowLayout](base/layout/TagFlowLayout.java) | 只排列已有子控件，不负责标签数据或点击。 |
| 表格里的只读文字 | [UiDisplayText](base/display/UiDisplayText.java) | 保留完整值提示并向外层行透传点击，防止文字提示独占选择手势。 |
| 单个 Tag 显示块 | [UiTagChip](composite/display/UiTagChip.java) | 统一主题、24dp 高度和省略文字；默认只读透传行点击。`setPrefix()` 仅设置视觉前缀，`setOnRemove(description, action)` 可配置删除，普通点击可用 `setOnClickListener()`；不修改标签数据。 |
| 固定行高的标签展示 | [UiTagStrip](composite/display/UiTagStrip.java) | 组合 `UiTagChip` 显示单行只读标签及溢出提示；`setPrefix()` 统一前缀。多行排列使用 `TagFlowLayout`＋`UiTagChip`。 |
| 纵／横滚动 | [UiScrollView](base/scroll/UiScrollView.java)、[UiHorizontalScrollView](base/scroll/UiHorizontalScrollView.java) | 共用内部 `UiScrollBehavior`，统一滚动条交互边界；轨道与滑块使用边界内填充绘制，避免缩放后描边越界与淡出残影。 |
| 编辑器右侧栏 | [EditorSidebar](composite/sidebar/EditorSidebar.java)、[SidebarLayoutController](composite/sidebar/SidebarLayoutController.java) | 面板标签、展开／收起与布局联动；窗口失焦取消标签拖动，延迟滚动只定位当前仍在目录中的标签；可扩展面板通过 [sidebar/api](composite/sidebar/api) 注册。 |

## 菜单与弹层

通用级联右键菜单使用 `UiContextMenu`，业务调用方仅组装菜单文字和动作，不依赖资产浏览器。可复用模态窗口外壳使用 `ModalWindowView`。共享弹层协议、句柄及挂载适配位于 `ui/overlay`；宿主实现 `OverlayHost`，`MainUiShell` 将请求委托给自己的层管理器。组件不查找具体 shell，不导入 `ui/shell`；无宿主时沿用各组件的独立展示模式。

| 需求 | 推荐入口 | 组合关系 |
| --- | --- | --- |
| 普通文字选项／级联菜单 | [UiContextMenu](composite/menu/UiContextMenu.java) | `addMenuItem()`／`addSubMenuItem()` 组装，`showAt(x,y,host)` 或 `showBelow(anchor,host)` 定位；模态内菜单使用该窗口的 `contentHost()`，避免挂到较低的根层。 |
| 可搜索文字选项 | [UiSearchableMenu](composite/menu/UiSearchableMenu.java) | `showAt(anchor, host)`；固定搜索区、限高滚动列表、锚点随节点变换跟随。 |
| 图片／预览卡片选择 | [UiGridMenu](composite/menu/UiGridMenu.java) | 正方形预览＋可选底部文字；`Style` 控制卡片宽度、列数和间距，窄空间自动折行。 |
| 勾选、输入、多状态选择等任意组合 | [UiPopover](composite/menu/UiPopover.java) | 传入内容 View，调用 `showBelow()` 或 `showAtRoot()`；组合公共弹层底座和滚动面板。 |
| 扇形选择 | [UiRadialMenu](composite/menu/UiRadialMenu.java) | 开关由调用方决定；[HeldMenuKey](composite/menu/HeldMenuKey.java) 提供按住／松开触发协议。保留键盘焦点以接收松开，不套普通弹层的抢焦点方式；禁用会关闭并清除按压／悬停，重新启用后须显式打开。 |
| 搜索菜单的标题与搜索区 | [UiSearchMenuLayout](composite/menu/UiSearchMenuLayout.java) | `addHeader()` 统一标题、搜索和间距，可关闭搜索；不执行查询、不创建弹层。 |
| 菜单条目与卡片状态 | [UiMenuItems](base/menu/UiMenuItems.java) | `row()`、`card()`、`decorate()` 共用选中／悬停／禁用视觉，保持背景对象稳定。 |
| 滚动菜单面板及边界测量 | [UiMenuPanelLayout](base/menu/UiMenuPanelLayout.java) | 统一对称内边距、限高测量和位置约束；锚点跟随、上下翻转和级联策略由具体菜单持有。 |
| 特殊普通弹层实现 | [UiPopupOverlay](base/menu/UiPopupOverlay.java) | 底层负责缩放输入、外部点击、Esc、焦点、Area 生命周期和安全移除。仅需要自定义摆放／多面板级联时扩展；通常直接组合 `UiPopover`。 |

`UiSearchBar` 是工具栏搜索，`UiSearchMenuLayout` 是菜单内容布局，`UiSearchableMenu` 是完整选择菜单，三者职责不同。类别导航、业务权限和选项回调不进入弹层底座。

## 表格

Tag 的视觉统一使用 `UiTagChip`：世界对象／数据仓库表格和资产列表通过 `UiTagStrip` 组合，Graph 属性与数据库详情通过 `TagFlowLayout` 组合。禁止在业务界面另写标签背景、描边、删除图标和 hover 样式。上述入口统一直接显示标签文字，不额外添加 `#` 前缀；删除回调仍由上层提交并维护历史。只读标签不会吞掉行选择手势，删除按钮则独立消费点击。

[UiTableColumns](composite/table/UiTableColumns.java) 保存列定义和逻辑宽度；[UiTableRow](composite/table/UiTableRow.java) 让表头、数据行使用同一列模型并处理列边界拖动；[UiTableViewport](composite/table/UiTableViewport.java) 提供横向溢出与测量更新；[UiTableStyle](composite/table/UiTableStyle.java) 提供共享行、单元格、配色和固定序号列。

表头需要列筛选时使用 [UiTableFilterHeader](composite/table/UiTableFilterHeader.java)：标题点击保留排序，右侧独立的 18dp 正方形按钮打开筛选，始终显示一个下拉箭头，`setFilterActive()` 通过背景色标记已启用的条件。序号、勾选等特殊列不设置筛选。按钮右侧留出列宽拖动判定区。

[UiColumnFilterPopover](composite/table/UiColumnFilterPopover.java) 组合公共输入框、复选框和 `UiPopover`，提供关键词、多选（全选／反选）、数字区间、XYZ 区间、标签包含／排除编辑器；每次只打开一份草稿，应用／清除才回调，点击外部／Esc 不提交。它不保存状态、不发查询；调用方使用 `ColumnFilters` 表达条件，在各自数据入口做 AND 匹配，并通过 scoped MEMORY 状态保存。数字边界包含端点，空边界不限制；非数值只在启用数字条件时排除。标签按“包含标签块 → 输入框 → 排除标签块 → 输入框”排列，复用 `UiTagChip` 和 `TagFlowLayout`。Enter 将输入加入本地草稿，标签块可移除；应用时也包含尚未按 Enter 的输入，关闭弹层丢弃草稿。提交具备代次与重入保护：回调中新开的弹层不会被旧提交关闭，递归点击不会重复执行同一提交。逗号属于标签内容，不作为分隔符。维度等多选菜单可以附加独立 scope 开关。

批量创建行使用 [UiTableBatchWork](composite/table/UiTableBatchWork.java)：`start(steps, complete)` 在 UI 线程执行首批，单帧最多 32 步／约 3ms，后续分帧继续；每一步应只创建／挂载一行，不在提交前创建整张表的 View。再次 `start` 或 `cancel` 立即使旧计划及其已入队回调失效。新查询、切换作用域、隐藏及释放时由调用方取消；快照、选择、结果计数和完成后的缓存清理仍由业务层维护。该组件不启动线程、不做网络等待、不把 MUI View 操作移到工作线程。

自适应数据行使用 `WRAP_CONTENT` 并设置最小行高。`UiTableRow` 先按内容测量，再让 `MATCH_PARENT` 单元格填满最终高度。有背景按钮、选中条或边线等覆盖层时，使用 [UiTableRowFrame](composite/table/UiTableRowFrame.java) 让它们跟随内容高度，避免覆盖层撑满剩余空间。

两种数据库表格共用 `UiTableStyle` 的 30dp 表头、44dp 普通行、12dp 正文字号、38dp 结果栏和 28dp 状态栏；选择列统一通过 `selectionColumn()` / `selectionCell()` 创建，使用 32dp 列宽和 16dp 复选框。

表头与数据行必须共用同一个列模型。序号表示筛选、排序后的显示顺序，不作为对象 ID。尺寸变化只更新测量布局，不触发业务查询或重建整张表。

一个列模型由对应的 `UiTableViewport` 管理宽度监听；调用方不再覆盖 `setOnWidthsChanged()`。viewport 构造时建立监听，detach 时仅解除自己的监听，attach 时重新绑定并同步已有行。列边界与分割线按局部坐标拖动；取消、禁用、移除或窗口失焦均结束当前手势，重新获得焦点后的移动／松键不会续接旧拖动。

## 原生游戏内容与专用覆盖层

| 用途 | 入口 |
| --- | --- |
| 物品展示 | [ItemSlotView](integration/valuepreview/ItemSlotView.java) |
| 实体模板／实体引用展示与转动预览 | [EntityTemplatePreviewView](integration/valuepreview/EntityTemplatePreviewView.java) |
| 表格／属性栏中的只读实体值 | [EntityPreviewCell](integration/valuepreview/EntityPreviewCell.java)；默认64dp 正方形框，纵向留白12dp，窄列自动等比缩小；`(Context, int preferredSizeDp)` 可指定属性栏尺寸 |
| 表格／属性栏中的只读物品／方块外观 | [ItemPreviewCell](integration/valuepreview/ItemPreviewCell.java)；复用 `ItemSlotView`，默认尺寸、可配置尺寸构造器和可见性策略与实体单元格一致 |
| 实体／物品内容缩放 | [PreviewZoomController](integration/valuepreview/PreviewZoomController.java)，通过两个预览组件的 `zoom()` 获取 |
| 标记类型预览 | [MarkerTypePreview](integration/valuepreview/MarkerTypePreview.java) |
| 游戏背包物品选择 | [InventoryItemPickerOverlay](integration/overlay/InventoryItemPickerOverlay.java) |
| 物品提示覆盖层 | [ItemStackTooltipOverlay](integration/overlay/ItemStackTooltipOverlay.java) |
| 长文本／富文本展开编辑 | [ExpandedTextInputOverlay](composite/overlay/ExpandedTextInputOverlay.java) |
| 自定义原生渲染预览 | [ViewportNativePreviewView](integration/nativepreview/ViewportNativePreviewView.java)；通过 [NativePreviewHost](integration/nativepreview/NativePreviewHost.java) 注册，由共享 Layer 管理渲染 |

节点中的 `UIItemSlot`、`UIEntityTemplatePreview` 仅负责端口取值、提交与编辑器入口；底层展示已复用上述公共组件，不要在节点包内复制绘制实现。

`integration/nativepreview` 是 ModernUI 与 Minecraft 渲染的桥接层，`integration/valuepreview` 是具体内容控件；已有物品／实体控件优先复用。长文本／富文本编辑位于 `composite/overlay`，不属于 Minecraft 原生绘制桥接。模态编辑覆盖层通过项目的 `OverlayMount` 管理，与局部菜单生命周期不同。

实体展示通过 `setDisplayTemplate()` 绑定保存的模板，或通过 `setDisplayReference(UUID, dimension)` 绑定当前世界实体（空维度表示当前维度）。模板实体在原生渲染线程创建；实体引用按 UUID 和维度校验，不持有或销毁真实实体。状态通知在 UI 线程交付，并忽略旧数据源／移除前的过期渲染结果。

`EntityPreviewCell` 组合该预览与状态文字，保持只读、固定朝向、缩放关闭，点击和普通滚轮交给行／滚动容器。通过 `setTemplate()` / `setReference()` 更新已存在的单元格，并以 `setVisibilityRow(row)` 绑定所属数据行；空值与没有可渲染外观的值留空，未加载和渲染失败保留状态提示，不要再用序列化字符代替预览。

两个预览单元格均接受正数 `preferredSizeDp`，表格继续使用默认64dp，数据库属性栏使用144dp，空间不足时自动缩小。属性栏以 `setVisibilityRow(cell)` 将单元格自身设为完整可见性锚点；面板取消激活时隐藏预览容器，恢复时复用原控件。不要另建属性栏专用原生预览或在业务层复制裁剪判断；尺寸配置不启用缩放，也不改变节点现有行为。

`ItemPreviewCell` 通过 `setStack()` 更新只读物品外观，使用已有 `ItemSlotView` 原生绘制，不开启编辑或滚轮缩放。内容以 `ItemStack.matches` 比较，仅变更时保存副本并刷新原生控件，重复摘要刷新不会重新复制或重置 surface。64dp 预览框上下各留白 12dp，使普通宽度的数据行高为 88dp；窄列与实体单元格一样等比缩小。空 stack 留空并保留布局高度，点击与普通／Ctrl 滚轮交给行和滚动容器。必须以 `setVisibilityRow(row)` 绑定所属数据行，复用基类的整行裁剪与 surface 同步，不能在调用方另写滚动位置判断。数据仓库的业务 helper 将 BLOCK_STATE 转为对应物品外观；没有物品形态的方块留空，方块属性不作为独立方块渲染。注册类型由业务值或调用方异步元数据快照提供，不从预览可见性或绘制结果推导。

原生实体绘制不继承 MUI 父级滚动容器裁剪。表格使用基类 `setFullVisibilityAnchor(row)`：UI 线程在滚动及每次绘制前检查祖先裁剪，只有数据行纵向完整可见、预览框横纵向均完整可见时才提交原生绘制。横向表格允许行宽超过视口。独立 surface 在 MUI `updateSurface()` 读取 alpha 时把裁剪结果编码为零／原透明度，由 MUI 与位置、尺寸一起同步；渲染回调只消费该帧的 alpha、当前绘制 pose 和 `onSurfaceChanged` 尺寸，不比较最新 UI 坐标，也不在渲染线程读取 View 几何，避免连续滚动时错误跳帧造成闪烁。普通节点预览默认不启用整行策略。共享 `NativePreviewHost` 路径仍通过不可变快照校验外部提供的变换，移除组件时注销监听；独立 surface 直接复用 MUI 自身的滚动／绘制监听，不额外注册每个预览的位置监听。

原生预览卸载时会注销宿主、清除旧视口变换并移除备用 surface；重新挂载必须获得新宿主变换或新 surface，旧渲染回调不能复用。业务侧刷新其他属性时，应保留来源未变化的预览控件及其挂载关系，避免反复创建原生资源。

### 预览缩放（默认关闭交互）

实体和物品保留各自的绘制实现，共用 `PreviewZoomController`。两者的 `zoom()` 默认倍率为 `1`、范围为 `0.5–2.0`，`WheelMode.DISABLED` 不接管滚轮。现有节点及其他调用方未开启此功能，原有外观、画布缩放和滚动行为不变。

```java
preview.zoom().setRange(0.5f, 2.0f);
preview.zoom().setWheelMode(PreviewZoomController.WheelMode.CTRL_WHEEL);
preview.zoom().setFactor(1.25f); // 可由滑块或固定展示配置驱动
preview.zoom().reset();        // 回到 1，受当前范围约束
```

- `DISABLED` 禁用滚轮缩放；`CTRL_WHEEL` 仅接管 Ctrl＋纵向滚轮；`WHEEL` 接管普通纵向滚轮。控件禁用时不处理缩放，达到上限／下限后仍消费已接管的缩放事件，避免突然滚动父列表。
- 配置和事件在 UI 线程处理，渲染线程读取安全发布的倍率。`getFactor()`／`getMinimum()`／`getMaximum()`／`getWheelMode()` 提供当前配置。
- 倍率及范围必须为正的有限数，最小值不得大于最大值；设置倍率或收窄范围会自动钳制。非法配置抛出异常且不修改已有状态。
- 程序设置倍率不需要开启滚轮；关闭滚轮也不隐式重置倍率。变更内容、开启编辑权限或切换视口倍率不会自动启用缩放。
- 内容缩放作用于原有适配结果，不改变展示框尺寸、布局和 `setViewportScale()` 的坐标变换。倍率大于 1 时内容可能放大至框外，由现有渲染裁剪限制显示区域。

## 公共输入与生命周期契约

- `UiTextInput` 接受鼠标按下后保护整段文本选择手势，即使移出输入框也不转为父级滚动／拖动。松键、取消、禁用、隐藏、移除、失焦或窗口失焦会释放保护；仅有输入焦点不会锁住整个界面。拖拽容器必须遵循原生子控件优先分发顺序，仅在自己的 `onTouchEvent` 回退路径启动容器手势，禁止在 `dispatchTouchEvent` 中先执行选择／拖动再分发给子控件。按钮、复选框和其他可编辑子控件同样拥有各自手势，只读展示仍可透传。
- 单行值编辑调用 `UiTextInput.setEditActions(confirm, cancel)` 绑定普通／小键盘 Enter 确认和 Esc 取消；重复按键不重复执行。多行 Enter 保留换行，现有 `OnKeyListener`（搜索提交、标签输入、弹层关闭等）保留优先级。该 API 不擅自保存或回滚业务值：调用方确认时提交，取消时恢复草稿／关闭编辑器，避免随后的失焦再次提交被取消内容。未绑定动作时保留原生键盘行为。
- `UiNumericInput` 的拖动只在松键时提交；普通编辑器失焦或 Enter 提交有效草稿，Esc 丢弃草稿。数值拖动也会保护父级拦截，释放或中断时归还。取消手势、禁用、移除及窗口失焦由组件处理；原生移除中先于 detach 的失焦也不会提交旧草稿。`UiGradientStopEditor` 在同类中断时回退预览；调用方只提供绑定和业务提交校验，无需复制生命周期防护。
- 调整包含编辑器的容器层级时使用 `bringChildToFront()`，不要先 `removeView()` 再挂回：原生移除会清除焦点、取消手势并触发编辑器生命周期。Graph 节点置顶遵循此规则；自定义捕获和原生分发路径各自结束对应手势，避免漏发或重复发送取消事件。
- `UiPopupOverlay` 负责 Area 绑定、窗口失焦关闭、延迟移除和焦点归还。布局或窗口焦点遍历中先关闭交互，再延迟移除；调用方以 `isOpen()` 判断状态，不以 `getParent()` 判断是否仍可操作。
- `ColorPickerDialog` 通过 `show()`／`showIn()` 绑定 owner 与 Area；正常 modal 获取键盘焦点不会关闭它，owner 移除或 Area 失效会取消；应用前逐级检查 owner／祖先可见性，不要求 standalone 已 attach。`ColorSwatchView` 忽略禁用、移除或再次打开之前遗留的回调；supplier 仍是权威值。颜色、长文本、背包弹层处理重复应用和关闭；应用或关闭回调抛异常时也会清理弹层，异常继续交给调用方处理。
- MainUI 中的模态焦点由 `OverlayMount`／层管理器负责；独立颜色、长文本和背包弹层复用 `UiMenuFocus`，归还同一根和有效 Area 内的原焦点，并尊重主动转移到其他控件的焦点。物品／实体预览在禁用、移除或窗口失焦时清除按压手势；原生预览注册由 `ViewportNativePreviewView` 在 attach／detach 时管理；fallback renderer 捕获自身 surface，并校验它仍是当前实例，忽略已废弃的 surface 回调。

## 新组件与调用约定

- 新增前先检查是否可组合按钮、输入、布局和弹层。业务专用拼装留在对应编辑器；只有共享职责进入此目录。
- 不在调用层重写通用按钮的 hover／pressed／disabled 绘制，不在悬停时反复替换背景触发布局。
- 面板缩放、根坐标转换使用 `UIUtils`；不要用屏幕坐标相减代替变换矩阵，也不要混用继承自 View 的 `dp()`。
- 明显操作使用 `contentDescription`，避免重复 tooltip；完整数据值需要提示时使用能正确透传行点击的展示控件。
- 草稿与已提交值分开；视觉刷新不能调用业务提交、触发历史命令或覆盖未提交输入。
- 目录按职责分类，修改组件时迁移已有调用方；不保留旧包名转发类或平行实现。

## 界面状态接入

组件不自行保存文件。调用方通过 `UiStateContext` 选择 `MEMORY`（本次游戏进程）或 `PERSISTENT`（跨重启）；完整分层、生命周期和接入约定见 [界面状态架构](../../../../../../../../../docs/ui/ui-state.md)。

- `UiTableColumns.exportCustomWidths()` / `restoreCustomWidths(Map<String, Float>)`：按稳定列 ID 保存逻辑 dp，仅导出显式调整的可调列；忽略未知／固定列及非法数值。`addUserStateListener(Runnable)` 返回可解除的订阅，不覆盖 viewport 的布局回调。
- `UiFixedSplitLayout.preferredExtentDp()` / `restorePreferredExtentDp(float)`：保存期望尺寸，窗口空间不足时的临时压缩不会写回偏好；用户拖拽使用 `addUserStateListener`。
- `SidebarLayoutController(split, sidebar, Consumer<Boolean>)`：固定尺寸侧栏只通知显隐，宽度由 split 管理；旧权重式构造器保持权重语义。

先恢复状态，再订阅用户变更。自动测量、缩放及恢复不会触发用户变更通知；表格 `setOnWidthsChanged` 属于布局刷新通道，不能作为存储通道。保存、作用域选择及订阅生命周期由编辑器管理。

## 自定义 Data 字典（数据库与其他能力面板）

`integration/metadata` 是异步能力与 UI 的桥接层；不读取原版 NBT 或物品数据组件，不能把领域仓库移入此包。

| 用途 | 入口 |
| --- | --- |
| 自定义数据异步绑定 | `DataDictionaryBinding`：`snapshot()` 是无 IO 的快照；`refresh()` 按需读取（允许使用已载入缓存），`reload()` 是用户明确要求重新读取权威源；`apply(expected,replacement,completion)` 由业务仓库原子比较并保存 |
| 表格数据摘要 | `UiDataSummaryCell(Context)` + `setSummary(count,summary,manage)`；零条留空；**不会为每行加载完整字典** |
| 标量／容器简述 | `UiDataSummaryCell.summaryValue(Object)`：字典／列表只显示长度，标量最多100字符，未知值只显示类型名 |
| 侧栏八条预览 | `UiDataDictionaryPreview` + `setBinding()`；支持搜索，`setManageAction`／`setManageEntryAction` 接到稳定 owner 的管理入口 |
| 完整字典编辑 | `UiDataDictionaryEditor.show(anchor,title,binding,initialKey)`；title 为标题“数据”旁的对象说明，可省略 title／initialKey；返回 false 代表没有共享弹层宿主 |

编辑器组合公共 `ModalWindowView`、`UiContextMenu`、`UiTextInput`、`UiSelectButton`、按钮与滚动容器，不再创建独立弹层体系。下拉选择复用 `UiContextMenu.showBelow(anchor, contentHost())`，挂在模态窗口内部，不能加到低于 modal layer 的全局根节点；Esc 先关闭菜单。顶部统一放置搜索、类型筛选和新增；左侧按20条分页并显示条目类型；右侧键与类型并排，布尔值复用复选框，字典与列表切换 JSON 编辑和有上限的结构预览。标题与对象说明在同一文本基线，底栏显示草稿状态和等距操作；还原条目只撤销当前表单，保存条目只更新草稿，应用才写入业务仓库。整数及其他数值使用普通文本输入，无上下微调按钮。编辑保留 Integer／Long／Float／Double 精度；重复键、溢出、非有限数和超限结构不能提交。未知注册类型与包含它们的复杂值保持只读，编辑其他条目不会把原值字符串化。

草稿只在应用时提交；取消与关闭确认不修改仓库。重新加载在存在草稿时先用弹窗内的丢弃确认，确认后重新取得数据并建立新的比较基线，可恢复版本冲突；取消确认会保留当前输入。组件自己的监听在 detach／modal close 时解除；异步通知和完成回调 post 回 UI 线程，并拒绝旧绑定／关闭后的回调。组件**不关闭共享 binding/session**，由业务 owner 负责。侧栏只在挂载且可见时主动请求读取；表格优先使用 summary-only API。管理弹窗的 anchor 应为稳定 editor／面板，避免刷新表格重建行时把弹窗误关掉。
