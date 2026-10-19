# UI 组件选用索引

新增界面先在此查找组件。尺寸与文字使用 `UIUtils` 的项目缩放，颜色使用 `UiTheme`；业务查询、数据写入、历史命令和服务器交互由调用方负责。

## 按用途选择

| 用途 | 推荐入口 | 职责与使用方式 |
| --- | --- | --- |
| 文字动作按钮 | [UiActionButton](button/UiActionButton.java) | `Role` 选择语义配色，`Density` 选择文字密度；统一悬停、按下、禁用、焦点反馈。切换开关使用 `Palette.toggle(selected)`，只调用 `setSelected()` 不会替换调色板。 |
| 图标动作按钮 | [UiIconButton](button/UiIconButton.java) | 包装图标并统一命中尺寸、背景和状态；不要直接把 SVG 当按钮。 |
| 左右分段按钮 | [UiSplitButton](button/UiSplitButton.java) | 一个外框、两个独立动作；如吸附开关＋配置下拉。组合 `UiActionButton`；外层 `setEnabled()` 同步控制两侧按钮。 |
| SVG／简单矢量图标 | [SvgIconView](icon/SvgIconView.java)、[VectorIconView](icon/VectorIconView.java) | 仅绘制；统一着色使用 [UiIconColors](icon/UiIconColors.java)。 |
| 文本输入、错误提示 | [UiTextInput](input/UiTextInput.java) | 输入框视觉与 `setErrorMessage()`；输入草稿、解析、提交和撤销仍由调用方决定。 |
| 搜索输入 | [UiSearchInput](input/UiSearchInput.java) | 继承文本输入视觉，增加单行约束与 `setOnQueryChanged()`；程序设置文本也会通知。 |
| 输入＋方形查找按钮 | [UiSearchBar](input/UiSearchBar.java) | 组合搜索输入和 32×32 逻辑像素按钮；点击或 Enter 提交，键入不自动查询；外层禁用同步控制输入和按钮，保留草稿。 |
| 多输入共享搜索提交 | [UiSearchSubmit](input/UiSearchSubmit.java) | `bind(button, action, inputs...)` 统一普通／小键盘 Enter、过滤重复按键；不改草稿、不执行筛选。资产浏览器双输入使用此入口。 |
| 复选框 | [UiCheckBox](input/UiCheckBox.java) | `setChecked()`／`setIndeterminate()` 静默同步勾选／半选状态；用户点击半选时全选并通知监听器。数据集合由调用方维护。 |
| 数值拖动、步进、内联编辑 | [UiNumericInput](input/UiNumericInput.java) | 通过 `Binding`、`Spec` 接入数据与数值规则；普通设置数值文本也可直接组合 `UiTextInput` 和校验。 |
| 下拉触发按钮 | [UiSelectButton](input/UiSelectButton.java) | 仅显示当前值和箭头；菜单内容与选中数据由调用方提供。 |
| 颜色块预览与选择 | [ColorSwatchView](input/ColorSwatchView.java) | 透明棋盘背景、颜色选择器；通过 supplier 读取颜色、回调提交，组件不绑定节点或历史。 |
| 渐变显示与色标拖动 | [UiGradientStopEditor](input/UiGradientStopEditor.java) | `Style` 控制尺寸，`Binding` 分离临时预览与最终提交；取消／禁用／移除／窗口失焦时回到已提交值，调用方校验提交快照并维护撤销。 |
| 颜色选择 | [ColorPickerDialog](overlay/ColorPickerDialog.java) | 完整颜色编辑弹层，使用自身展示入口管理生命周期。 |
| 名称＋数量导航行 | [UiNavigationRow](list/UiNavigationRow.java) | 组合一个按钮及被动文字层；通过 `button()` 绑定操作，`setPalette()` 更新状态。 |
| 单选、Ctrl／Shift 多选 | [UiListSelection](list/UiListSelection.java) | 基于稳定键与当前显示顺序计算选择；复选框独立增减集合，不当作普通行单选。 |
| 标签＋值＋末端操作 | [UiPropertyRow](layout/UiPropertyRow.java) | 只负责对齐和窄布局；调用方传入值控件、复制按钮和 `Layout` 尺寸。不会自动读写对象。 |
| 固定尺寸边栏／上下分栏 | [UiFixedSplitLayout](layout/UiFixedSplitLayout.java) | 保存独立逻辑尺寸，剩余区域伸缩；适合数据库目录和属性栏，避免多分割线比例联动。 |
| 分割线输入与比例分栏 | [ResizableDivider](layout/ResizableDivider.java) | 通用拖动处理；`weighted()` 用于按比例分配的兄弟面板。`Orientation` 表示拖动轴。 |
| 细线外观分割线 | [ThinResizableDivider](layout/ThinResizableDivider.java) | 继承同一输入逻辑；细线常态、宽命中区、悬停蓝条。 |
| 自动换行布局 | [TagFlowLayout](layout/TagFlowLayout.java) | 只排列已有子控件，不负责标签数据或点击。 |
| 表格里的只读文字 | [UiDisplayText](display/UiDisplayText.java) | 保留完整值提示并向外层行透传点击，防止文字提示独占选择手势。 |
| 固定行高的标签展示 | [UiTagStrip](display/UiTagStrip.java) | 单行只读标签及溢出提示；多行排列使用 `TagFlowLayout`。 |
| 纵／横滚动 | [UiScrollView](scroll/UiScrollView.java)、[UiHorizontalScrollView](scroll/UiHorizontalScrollView.java) | 共用内部 `UiScrollBehavior`，统一滚动条交互边界；轨道与滑块使用边界内填充绘制，避免缩放后描边越界与淡出残影。 |
| 编辑器右侧栏 | [EditorSidebar](sidebar/EditorSidebar.java)、[SidebarLayoutController](sidebar/SidebarLayoutController.java) | 面板标签、展开／收起与布局联动；窗口失焦取消标签拖动，延迟滚动只定位当前仍在目录中的标签；可扩展面板通过 [sidebar/api](sidebar/api) 注册。 |

## 菜单与弹层

| 需求 | 推荐入口 | 组合关系 |
| --- | --- | --- |
| 可搜索文字选项 | [UiSearchableMenu](menu/UiSearchableMenu.java) | `showAt(anchor, host)`；固定搜索区、限高滚动列表、锚点随节点变换跟随。 |
| 图片／预览卡片选择 | [UiGridMenu](menu/UiGridMenu.java) | 正方形预览＋可选底部文字；`Style` 控制卡片宽度、列数和间距，窄空间自动折行。 |
| 勾选、输入、多状态选择等任意组合 | [UiPopover](menu/UiPopover.java) | 传入内容 View，调用 `showBelow()` 或 `showAtRoot()`；组合公共弹层底座和滚动面板。 |
| 扇形选择 | [UiRadialMenu](menu/UiRadialMenu.java) | 开关由调用方决定；[HeldMenuKey](menu/HeldMenuKey.java) 提供按住／松开触发协议。保留键盘焦点以接收松开，不套普通弹层的抢焦点方式；禁用会关闭并清除按压／悬停，重新启用后须显式打开。 |
| 搜索菜单的标题与搜索区 | [UiSearchMenuLayout](menu/UiSearchMenuLayout.java) | `addHeader()` 统一标题、搜索和间距，可关闭搜索；不执行查询、不创建弹层。 |
| 菜单条目与卡片状态 | [UiMenuItems](menu/UiMenuItems.java) | `row()`、`card()`、`decorate()` 共用选中／悬停／禁用视觉，保持背景对象稳定。 |
| 滚动菜单面板及边界测量 | [UiMenuPanelLayout](menu/UiMenuPanelLayout.java) | 统一对称内边距、限高测量和位置约束；锚点跟随、上下翻转和级联策略由具体菜单持有。 |
| 特殊普通弹层实现 | [UiPopupOverlay](menu/UiPopupOverlay.java) | 底层负责缩放输入、外部点击、Esc、焦点、Area 生命周期和安全移除。仅需要自定义摆放／多面板级联时扩展；通常直接组合 `UiPopover`。 |

`UiSearchBar` 是工具栏搜索，`UiSearchMenuLayout` 是菜单内容布局，`UiSearchableMenu` 是完整选择菜单，三者职责不同。类别导航、业务权限和选项回调不进入弹层底座。

## 表格

[UiTableColumns](table/UiTableColumns.java) 保存列定义和逻辑宽度；[UiTableRow](table/UiTableRow.java) 让表头、数据行使用同一列模型并处理列边界拖动；[UiTableViewport](table/UiTableViewport.java) 提供横向溢出与测量更新；[UiTableStyle](table/UiTableStyle.java) 提供共享行、单元格、配色和固定序号列。

自适应数据行使用 `WRAP_CONTENT` 并设置最小行高。`UiTableRow` 先按内容测量，再让 `MATCH_PARENT` 单元格填满最终高度。有背景按钮、选中条或边线等覆盖层时，使用 [UiTableRowFrame](table/UiTableRowFrame.java) 让它们跟随内容高度，避免覆盖层撑满剩余空间。

两种数据库表格共用 `UiTableStyle` 的 30dp 表头、44dp 普通行、12dp 正文字号、38dp 结果栏和 28dp 状态栏；选择列统一通过 `selectionColumn()` / `selectionCell()` 创建，使用 32dp 列宽和 16dp 复选框。

表头与数据行必须共用同一个列模型。序号表示筛选、排序后的显示顺序，不作为对象 ID。尺寸变化只更新测量布局，不触发业务查询或重建整张表。

一个列模型由对应的 `UiTableViewport` 管理宽度监听；调用方不再覆盖 `setOnWidthsChanged()`。viewport 构造时建立监听，detach 时仅解除自己的监听，attach 时重新绑定并同步已有行。列边界与分割线按局部坐标拖动；取消、禁用、移除或窗口失焦均结束当前手势，重新获得焦点后的移动／松键不会续接旧拖动。

## 原生游戏内容与专用覆盖层

| 用途 | 入口 |
| --- | --- |
| 物品展示 | [ItemSlotView](valuepreview/ItemSlotView.java) |
| 实体模板／实体引用展示与转动预览 | [EntityTemplatePreviewView](valuepreview/EntityTemplatePreviewView.java) |
| 表格中的只读实体值 | [EntityPreviewCell](valuepreview/EntityPreviewCell.java)；64dp 正方形框，纵向留白12dp，窄列自动等比缩小 |
| 实体／物品内容缩放 | [PreviewZoomController](valuepreview/PreviewZoomController.java)，通过两个预览组件的 `zoom()` 获取 |
| 标记类型预览 | [MarkerTypePreview](valuepreview/MarkerTypePreview.java) |
| 游戏背包物品选择 | [InventoryItemPickerOverlay](overlay/InventoryItemPickerOverlay.java) |
| 物品提示覆盖层 | [ItemStackTooltipOverlay](overlay/ItemStackTooltipOverlay.java) |
| 长文本／富文本展开编辑 | [ExpandedTextInputOverlay](overlay/ExpandedTextInputOverlay.java) |
| 自定义原生渲染预览 | [ViewportNativePreviewView](nativepreview/ViewportNativePreviewView.java)；通过 [NativePreviewHost](nativepreview/NativePreviewHost.java) 注册，由共享 Layer 管理渲染 |

节点中的 `UIItemSlot`、`UIEntityTemplatePreview` 仅负责端口取值、提交与编辑器入口；底层展示已复用上述公共组件，不要在节点包内复制绘制实现。

`nativepreview` 是 ModernUI 与 Minecraft 渲染的桥接层，`valuepreview` 是具体内容控件；已有物品／实体控件优先复用。模态编辑覆盖层通过项目的 `OverlayMount` 管理，与局部菜单生命周期不同。

实体展示通过 `setDisplayTemplate()` 绑定保存的模板，或通过 `setDisplayReference(UUID, dimension)` 绑定当前世界实体（空维度表示当前维度）。模板实体在原生渲染线程创建；实体引用按 UUID 和维度校验，不持有或销毁真实实体。状态通知在 UI 线程交付，并忽略旧数据源／移除前的过期渲染结果。

`EntityPreviewCell` 组合该预览与状态文字，保持只读、固定朝向、缩放关闭，点击和普通滚轮交给行／滚动容器。通过 `setTemplate()` / `setReference()` 更新已存在的单元格，并以 `setVisibilityRow(row)` 绑定所属数据行；空值与没有可渲染外观的值留空，未加载和渲染失败保留状态提示，不要再用序列化字符代替预览。

原生实体绘制不继承 MUI 父级滚动容器裁剪。表格使用基类 `setFullVisibilityAnchor(row)`：UI 线程在滚动及每次绘制前检查祖先裁剪，只有数据行纵向完整可见、预览框横纵向均完整可见时才提交原生绘制。横向表格允许行宽超过视口。独立 surface 在 MUI `updateSurface()` 读取 alpha 时把裁剪结果编码为零／原透明度，由 MUI 与位置、尺寸一起同步；渲染回调只消费该帧的 alpha、当前绘制 pose 和 `onSurfaceChanged` 尺寸，不比较最新 UI 坐标，也不在渲染线程读取 View 几何，避免连续滚动时错误跳帧造成闪烁。普通节点预览默认不启用整行策略。共享 `NativePreviewHost` 路径仍通过不可变快照校验外部提供的变换，移除组件时注销监听；独立 surface 直接复用 MUI 自身的滚动／绘制监听，不额外注册每个预览的位置监听。

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

- `UiNumericInput` 的拖动只在松键时提交；普通编辑器失焦提交有效草稿。取消手势、禁用、移除及窗口失焦由组件处理；原生移除中先于 detach 的失焦也不会提交旧草稿。`UiGradientStopEditor` 在同类中断时回退预览；调用方只提供绑定和业务提交校验，无需复制生命周期防护。
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
