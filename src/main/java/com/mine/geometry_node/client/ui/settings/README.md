# 设置界面

本目录把 `client/config` 的注册项和 `ConfigDraft` 展示为设置页，不持有第二份配置权威值。默认设置窗口由 shell 创建的 `SettingsWindowController` 打开。

| 直接子目录 | 子层级与关键文件 |
| --- | --- |
| `window` | `SettingsWindowController` 管理本 MainUI 的设置入口与单个窗口；`SettingsWindow` 持有草稿、搜索、页缓存及应用/取消；`SettingsNavigationView/ContentHost` 负责导航与内容容器。`SettingsWindowEnvironment` 将选择器、提示及子弹层接到实例服务。 |
| `page` | `api` 中的 SettingsPage/Context/Definition/Factory/Registry 定义页面扩展；`builtin/BuiltinSettingsPages` 注册参数、路径、键位和缓存页。BuiltinSettingsProjection 与 ConfigCategoryTree 投影配置分类，ConfigEntriesSettingsPage 组合普通配置项；CacheSettingsPage 显示缓存状态并发起清理。 |
| `editor` | `ConfigEntryEditor`、Provider、Factory 选择字段控件；AbstractConfigEntryEditor 共用校验显示。Boolean/Choice/Number/Path/PathList/Shortcut 各类编辑器操作草稿；SettingsChoice、SettingsEditorEnvironment 隔离外部选项与选择器，SettingsEditorStyle 定义局部外观。 |

窗口和页缓存、字段编辑器、搜索与草稿在 UI 线程维护。页面切换可以保留实例；窗口真正关闭时 dispose 页面和环境、取消录制及子弹层、解除监听。应用由配置管理器校验并提交草稿，取消不应用未提交值；恢复 UI 不应触发配置写入。缓存扫描/清理等异步操作使用 UI executor 回传，检查 disposed 后再更新 View；共享缓存由各 cache owner 管理。

新增配置定义与持久化规则放 `client/config`；新增普通字段类型放 `editor` 并注册 provider；新增专用页实现 `page/api/SettingsPage`，在注册表装配。文件系统/资产选择通过 environment 接口，避免字段编辑器硬编码 shell 或 Minecraft 访问。
