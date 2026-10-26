# 客户端 UI

本树负责 ModernUI 视图、编辑器交互、游戏内页面和 HUD 展示。`MainUI` 是 Fragment 入口：创建 `MainUiShell`，销毁视图时释放游戏输入并销毁 shell；`main()` 提供独立启动入口。业务仓库和图编辑命令位于 `client/feature`，网络协议位于 `core/network`，客户端接收装配位于 `client/network`，渲染运行时位于 `client/runtime`；`client/platform` 提供线程、路径等基础宿主能力，跨能力装配位于 `client/integration`。

## 目录导航与依赖方向

| 直接子目录 | 职责与入口 |
| --- | --- |
| [components](components/README.md) | 可复用组件，分为基础 `base`、组合 `composite` 和游戏/业务适配 `integration`；公共资产 UI 在 `components/integration/asset`。 |
| [document](document/README.md) | `DocumentManager` 提供标签激活门面，`GraphSessionViewState` 保存文档的共享视图默认值；权威会话和命令在 `feature/graph`。 |
| [editor](editor/README.md) | `graph`、`game`、`asset`、`datalibrary`、`terminal` 各 Area 的具体视图和交互。 |
| [workspace](workspace/README.md) | Area 布局树、跨区域拖拽、surface 标识、状态作用域与编辑器布局持久化；通过工厂协议承载具体编辑器。 |
| [shell](shell/README.md) | 一个 MainUI 的装配根，组装菜单、设置、Area 工厂与弹层；具体内置实现由这里注册。 |
| [overlay](overlay/README.md) | 弹层宿主、挂载、关闭原因、瞬态/模态协议和逻辑窗口生命周期；具体层容器由 shell 实现。 |
| [screen](screen/README.md) | 游戏内对话、商店和任务页面；由运行时的展示桥接打开，不是工作区 Area。 |
| [hud](hud/README.md) | 标记投影和模型诊断 HUD，读取运行时快照并绘制。 |
| [settings](settings/README.md) | 设置窗口、页面扩展协议、内置页及配置项编辑器；配置权威值由 `client/config` 管理。 |
| `shortcut` | `UiInputBindings` 将 MUI 按键/鼠标事件转为 `client/input/binding` 的绑定；`ScopedAction`、`ScopedKeyManager` 进行作用域动作匹配；`ControlKeyRecorder` 记录编辑控件中的快捷键。这里只做 UI 输入适配，动作由宿主执行。 |
| [theme](theme/README.md) | 不可变主题角色 `UiTheme`、`UiThemes.EDITOR/HUD` 和共享视觉指标 `UiMetrics`。 |
| `utils` | `UIUtils` 管理项目密度及 dp、sp、坐标转换；`ItemTooltipProxy` 桥接物品提示显示。这里放跨视图的小型 UI 工具，不放业务仓库或编辑器控制器。 |

常见调用方向为 `MainUI → shell → workspace 协议 + editor 实现 → components`，编辑器和集成组件再调用 `feature/runtime`；需要连接多个能力时通过 `client/integration` 注入。`base` 不依赖 `composite/integration`，`composite` 不应承接业务仓库。组件可以使用 `overlay`、工作区生命周期等公共协议。此处描述职责边界，具体装配入口以源码中的接口和注册器为准。

## 线程、状态与资源

- ModernUI UI 线程拥有视图树、焦点、菜单、输入草稿、Area 生命周期和标签激活状态。仓库/网络通知可能来自其他线程：可丢弃的展示刷新可以 `View.post()`，执行时检查连接作用域、generation 和 owner；已提交操作的确认、事务收尾及资源释放必须走独立于 View 挂载的 UI executor/业务会话调度通道，避免 detach 后任务滞留或丢失。
- Minecraft 客户端线程负责游戏 Screen 切换、世界访问与游戏输入；渲染线程消费发布的帧、预览变换和可见性快照。UI 不能直接接管运行时的 framebuffer、纹理或世界实体生命周期。
- `GraphDocumentManager` 拥有图会话；单个 Viewport 拥有独立相机和选择状态。关闭编辑器视图不等于关闭文档。`UiStateContext` 保存展示偏好，不能作为业务仓库。
- Area 隐藏、View detach 和逻辑窗口关闭有不同含义。隐藏时解除展示输入和临时菜单；真正销毁时解除订阅、取消未完成的读取并释放所拥有的 handle。已提交写入或文件事务的完成由相应业务会话继续处理，不能因视图消失而丢弃确认。
- MainUI 实例服务由 `MainUiServices` 统一关闭；Area 布局树、surface 注册、子窗口由 workspace 管理；预览注册和缩略图引用由具体 View/缓存管理；终端 runtime 由终端标签关闭。

## 样式位置与新增代码

当前树没有 `ui/style` 实现目录，旧 `UIConstants`/集中式 style 路径已退役。全局颜色角色进入 `theme`，共享视觉尺寸进入 `UiMetrics`；组件的实例样式留在本组件的 `Style`，Area 专用布局/绘制样式留在对应职责包，如 `workspace/area/AreaStyle`、`editor/graph/GraphUiStyle`、`settings/editor/SettingsEditorStyle`、`screen/quest/QuestScreenStyle`。密度和单位换算只走 `UIUtils`。

新增 Area 在 `editor/<能力>` 实现 `AreaEditorWindow`，在 `shell/registration/BuiltinAreaEditors` 装配；新增属性侧栏通过 `SidebarPanel` 协议和 `BuiltinSidebarPanels` 注册。新增可复用视图按组件三层放置，业务数据处理进入 `feature`，跨能力路由进入 `client/integration`。新增目录时同步更新最近一级 README 的导航，无须为每个小叶子单建文档。
