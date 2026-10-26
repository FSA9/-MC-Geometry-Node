# 游戏视口运行时

本目录负责游戏画面与编辑相机视口的 Minecraft 运行状态：输入捕获、相机、区块、世界渲染、帧发布，以及与已显示画面对应的选择和吸附数据。场景文档与编辑业务由 `client.feature.scene` 提供；具体 View、Canvas、ModernUI/Arc3D 适配由 `client.ui` 和 [integration/game](../../integration/game/README.md) 承担。运行时通过发布接口和宿主策略接收装配，不反向引用这些 UI 实现。

目录职责如下。较小的相机、区块和单文件目录在本页说明，其余目录有独立说明。

| 目录 | 职责与关键文件 |
| --- | --- |
| `camera` | `EditViewportCamera` 保存每个视口的轨道/正交相机，提供同步修改与不可变 `Snapshot`；`EditViewportFlight` 处理飞行导航数学；`EditViewportProjection` 统一渲染与裁剪使用的投影约定。 |
| `chunk` | `EditCameraChunkClient` 向服务端节流发布编辑相机观察位置并接收观察范围；`EditChunkCache` 保存玩家区块环以外收到的区块；`EditChunkCacheAccess` 是原版区块缓存接入契约；`ViewportChunkReadiness` 为每个 terrain cache 独立保留就绪/失效通知。 |
| `diagnostic` | `GameViewTiming` 提供可选的 CPU 阶段计时与隐藏帧积压诊断，开关为 `geometry_node.gameView.profile`；数值可能包含驱动等待，不是异步 GPU 执行耗时。 |
| `host` | `ViewportHostPolicy` 接收工作区身份、背景色及是否替换保留工作区的能力。具体屏幕类型由应用装配识别。 |
| [input](input/README.md) | 玩家游戏输入和编辑相机飞行输入的互斥捕获、取消、释放及原版屏幕切换。 |
| `interop` | `UiRenderInterop` 记录渲染线程上的外部 UI GL 状态变更版本，供接入层使 Blaze3D 状态绑定失效；不负责绘制或持有纹理。 |
| [render](render/README.md) | 每帧预算、独立 terrain context、MC 世界/HUD 提交、发布纹理和帧租约生命周期。 |
| [scene](scene/README.md) | 场景对象到世界绘制、诊断、显示图层与创建落点的运行时适配。 |
| [selection](selection/README.md) | 与发布图像匹配的目标身份/几何、选择源、世界选中标记和动作反馈。 |
| [snap](snap/README.md) | 有预算的几何捕获、帧内吸附数据与移动求解。 |

## 线程与快照

Minecraft 客户端/渲染线程负责真实世界访问、原版输入状态、网络发送、渲染提取/提交、GPU 分配与资源关闭。下文涉及这些操作时均指这个所属线程。terrain 的后台工作线程仍由 Minecraft 或兼容渲染器管理。

UI 线程发布视口可见性、分辨率需求、选择标识和显示设置，并在相机同步接口上执行轨道导航。飞行导航与首次相机/世界绑定在客户端线程执行。渲染线程冻结相机和场景快照，形成 `PresentedViewportFrame`；UI 拾取、拖动和投影使用实际录制图像返回的这一帧，不使用刚更新但尚未显示的相机。

`EditViewportRuntime` 的 session monitor 保护请求、准入及 session 退出。`withFramePublication` 在同一 monitor 内调用读取者；读取者必须在返回前完成图像引用保留，不保存通道或管理其生命周期。发布通道自己的锁保护帧替换、尺寸校验和增加引用。GPU 操作保持在渲染线程，`synchronized` 不代表这些操作可以从任意线程调用。

## 区块与失效

`EditCameraChunkClient` 的观察位置同步独立于 UI 绘制帧率。没有有效可见编辑视口时释放观察声明，世界切换时重置订阅状态；服务端仍决定区块订阅与数据下发。

`EditChunkCache` 的负数槽位避免与原版玩家环的非负槽位混淆。它是实际收到的区块存储，不是可随时重算的缓存，不能接入用户的通用“清理缓存”。已填充槽位由原版区块卸载、网络和世界生命周期管理，并保留加载/卸载/光照回调。

`ViewportChunkReadiness` 在渲染线程上维护每个 terrain cache 的已知区块与失效记录。快视口处理一次通知不能消耗慢视口还需要的通知；世界和读取者通过弱引用关联，避免关闭后被注册表保留。

## 安装、扩展与退出

应用先调用 `WorkspaceViewportIntegration.install()`：安装独立发布通道工厂、配置宿主策略，再配置原生场景订阅条件。运行时状态查询允许尚无通道；有效帧捕获时工厂必须已安装，缺失会在分配发布纹理前抛出异常。具体发布契约、释放顺序和测试注入见 [render](render/README.md) 与 [integration/game](../../integration/game/README.md)。

新增世界交互目标通过 `ViewportSelectionSources` 扩展，几何吸附通过 `ViewportSnapSources` 扩展；外观与诊断几何通过 `ScenePresentations` 注册。扩展结果以不可变身份/几何进入帧，活的世界或实体对象不交给 UI。具体 UI 菜单和属性表单继续由 UI/功能层管理。

退出时，输入侧负责取消待接受的捕获并释放当前所有者，运行时所属线程负责清理 session、观察声明、世界 target、terrain context 与帧发布。撤销发布不等于立即关闭纹理：已录制 UI/GPU 工作保留的引用仍按发布回调完成最终释放。
