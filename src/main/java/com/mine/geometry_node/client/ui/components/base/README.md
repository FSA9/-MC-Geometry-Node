# 基础组件

这里提供单一职责的输入、显示、绘制、布局和弹层基础机制。依赖 ModernUI、主题、UI 工具及必要的输入/生命周期协议，不导入 `components/composite` 或 `components/integration`，不拥有业务提交逻辑。完整交互契约见[组件总览](../README.md)。

| 子目录 | 关键文件与职责 |
| --- | --- |
| `button` | `UiActionButton` 管理文字按钮的 Palette、状态与预设；`UiIconButton` 统一图标动作的尺寸、命中区、主题和禁用反馈。 |
| `display` | `UiDisplayText` 提供只读文字显示与提示，适合可透传行点击的单元格。 |
| `icon` | `SvgIconView` 加载 SVG，`VectorIconView` 绘制代码图标，`UiIconColors` 处理状态着色；内容原色与 UI 装饰色分开。 |
| `input` | `UiTextInput` 管理文本编辑与手势保护；`UiNumericInput` 管理数值草稿和拖动；`UiCheckBox`、`UiSelectButton` 提供选择显示。`UiSearchInput`、`UiSearchSubmit` 处理搜索输入与提交，不负责业务检索。 |
| `layout` | `ResizableDivider`、`ThinResizableDivider` 提供拖拽分隔线；`TagFlowLayout` 提供标签流布局。 |
| `list` | `UiListSelection` 计算单选、范围和批量选择，不持有业务列表仓库。 |
| `menu` | `UiMenuItems` 提供行状态装饰，`UiMenuPanelLayout` 排版，`UiMenuFocus` 保存/归还焦点，`UiPopupOverlay` 绑定 owner、Area、关闭与延迟移除。 |
| `overlay` | `UiInputBarrier` 阻止弹层输入穿透；完整弹窗在上层组合。 |
| `scroll` | `UiScrollView`、`UiHorizontalScrollView` 和 `UiScrollBehavior` 统一滚动、手势和主题配色。 |

控件在 UI 线程维护焦点、按压和未提交草稿；失焦、禁用、移除与手势取消必须归还输入捕获。调用方提供 supplier/回调，负责确认、取消与业务校验；刷新展示不应触发提交。图标/弹层等内部资源遵循控件自身 attach/detach 与 handle 生命周期，调用方不关闭共享缓存。

新增原子控件放相应子目录；一旦需要完整菜单、搜索栏、模态编辑器或业务对象，分别进入 `composite`、`integration` 或所属 Area。主题用 `Style.forTheme`/`createThemed`，单位转换用 `UIUtils`。
