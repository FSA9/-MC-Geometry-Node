# 游戏视口 Area

`GameViewportWindow` 是 `GAME_VIEWPORT` Area 入口，组合工具栏、`GameViewportView`、菜单和注册侧栏，并作为输入 owner、历史 surface 和 surface registration 接收者。`ViewportMode/ViewportModeState` 管理本窗口的游戏/飞行/编辑模式。画面生产、相机运行时、输入转发和场景业务位于 `client/runtime/game`、`client/feature/scene`。

| 直接子目录 | 关键文件与职责 |
| --- | --- |
| `gizmo` | ViewportOrientation 定义方向显示/命中，ViewportOrientationOverlay 绘制相机方向覆盖。 |
| `input` | GameViewportShortcuts 声明本视口可用的快捷键动作，借助公共 ScopedKeyManager 执行。 |
| `layout` | GameViewportLayout 计算画面适配与输入映射，GameViewportPixelDemand 计算需要的渲染像素尺寸。 |
| `scene` | ViewportSceneInteraction 处理选择与场景手势，ViewportTransformGesture/Number 处理变换拖动与数字输入，ViewportMoveSnap/SnapPanel 管理吸附交互，ViewportSceneMenus 组装模式/可视化/创建菜单，SceneCreateRegistry 提供 UI 创建项。 |
| `selection` | ViewportSelectionOverlay 绘制场景选择覆盖。 |
| `sidebar` | GameViewportSidebarPanel 展示视口设置，ScenePropertiesPanel 展示场景属性；`properties/ScenePropertyControl` 绑定具体属性控件与编辑动作。 |

`GameViewportView` 显示共享游戏帧或编辑相机帧，维护画面内坐标映射、输入捕获和覆盖绘制；通过 `client/integration/game/GameFrameImage` 使用已发布帧。UI 持有展示适配与输入 owner，framebuffer、帧发布及 GPU 资源仍由 runtime/render 管理。场景选择、变换提交、查询和历史能力通过 feature/runtime 接入，UI 不建立第二份世界对象仓库。

视图、模式菜单、数字输入及展示草稿在 MUI UI 线程操作；游戏输入与世界/相机更新经 runtime 的客户端线程入口，渲染线程生产帧。飞行输入回调先排入 UI 队列以保留按下/释放顺序；展示刷新可以 `View.post()`，已提交场景操作的完成与资源收尾必须由独立 executor/业务会话处理。

`onShow/onHide` 更新可见性与注册状态；Area 停用、窗口失焦或 detach 时归还游戏/飞行输入，取消未完成手势和临时菜单。真正销毁后禁止重新注册，surface 身份由 workspace 管理。窗口只取消自己拥有的交互，不关闭共享游戏帧生产者或其他视口资源。

新增视口 UI 交互放相应子目录，场景写入规则放 `feature/scene`，相机/帧/输入运行时放 `runtime/game`，跨工作区能力放 `client/integration/game`。新增 sidebar 按 GAME_VIEWPORT scope 注册；通用菜单、按钮和分栏直接复用公共组件。
