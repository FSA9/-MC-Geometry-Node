# 主题与样式边界

| 文件 | 职责 |
| --- | --- |
| `UiTheme` | 不可变颜色角色集合，按 Surface、Text、Border、Selection、Control、Status、Scroll、Graph、Decoration、Buttons 分类。 |
| `UiThemes` | 提供 `EDITOR` 和 `HUD` 两个显式预设；实例传入主题，无需切换全局可变主题。 |
| `UiMetrics` | 共享字号、圆角、边框、透明度和图标尺寸等视觉指标；不存布局策略、交互阈值或当前密度。 |

本目录没有子目录。它依赖基础值类型，供组件、Area、Screen、HUD 使用，不持有 View、业务状态、线程池或需要关闭的资源。不可变预设可以共享；控件自己的焦点/禁用/错误状态由各组件在 UI 线程维护。

当前不存在集中式 `ui/style` 目录，旧 `UIConstants` 不再作为入口。项目密度由 `utils/UIUtils` 私有管理，统一通过 `density/setDensity/syncFixedDensity` 和 dp/sp 转换访问。局部 `Style` 留在所属组件或职责包，例如 `GraphUiStyle`、`AreaStyle`、`ValuePreviewStyle`、`SettingsEditorStyle`、`QuestScreenStyle`。

新增跨组件颜色角色先扩展 `UiTheme` 并同时补齐预设；新增共享视觉指标放 `UiMetrics`。仅某个实例使用的排版参数放该组件 Style。按钮、输入、菜单、滚动条和弹窗需完整传递同一主题，不能只替换背景；图标原色、颜色编辑内容及业务分类色不被主题覆盖。详见[组件使用约定](../components/README.md)及[主题设计](../../../../../../../../../docs/ui/themes.md)。
