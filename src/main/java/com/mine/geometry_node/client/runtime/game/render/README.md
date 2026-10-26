# 视口渲染与帧发布

本目录拥有 Minecraft 世界渲染及 GPU 帧生产。ModernUI/Arc3D 纹理包装和 Canvas 录制位于 [integration/game](../../../integration/game/README.md)，通过 `ViewportFramePublication` 接入。UI 绘制回调只读取已发布画面，不执行世界渲染。

| 文件 | 职责 |
| --- | --- |
| `EditViewportRuntime` | 保存可见请求和每个编辑相机的 session，冻结相机/场景/选择，执行准入与源调度，切换世界 target，捕获画面并清理退出的 session。 |
| `ViewportFrameScheduler` | 每个客户端帧最多选择一个完整世界 pass；优先交互视图，并按后台帧率截止时间轮转其他源。无到期源时可跳过世界更新/提取/绘制。 |
| `ViewportResolution`、`ViewportFrameSize` | 分辨率需求量化与延迟缩小策略；尺寸值表示 framebuffer 或纹理像素，不是 GUI 缩放单位。增长和窗口尺寸变化立即响应。 |
| `EditorRenderCamera`、`PresentedViewportFrame` | 将编辑相机接入 MC，保存实际用于这一图像的相机、投影、场景世代、选择目标和吸附几何。 |
| `EditViewportRenderContext` | 每个编辑视口拥有独立 `LevelRenderer`、terrain 状态和跨帧资源池；作用域内安装，退出时恢复玩家 renderer/资源池，并转发世界更新、resize 和关闭通知。 |
| `EditViewportShaderScope` | 渲染线程局部的基础着色作用域，嵌套退出后恢复；不改变区块工作线程或 shader pack 设置/重载所见状态。 |
| `SharedGameFrame` | 正常 GUI 提交边界上的 GAME/HUD 捕获、工作区底色合成和共享资源退出。HUD 与编辑器提交使用独立顶点缓冲环，GUI 缓存清理等待两次提交都标记完资源。 |
| `ViewportFrameSurface` | 单个源的生产者，唯一持有其发布通道；分配/复制纹理、移交释放回调并管理 EDIT 发布池。 |
| `ViewportFramePublication`、`ViewportFramePublications` | 与消费后端无关的发布契约及应用安装的工厂；注册处只保存工厂，不保存第二份帧或 owner 映射。 |
| `FramePublicationPool` | 有界、可退役的资源租约；仍被消费者/GPU 使用的旧尺寸资源继续占用容量。 |
| `GameFrameCleanup` | 丢弃未绘制帧的提取结果，释放粒子组、清除临时 gizmo，保留 GUI 预览需要的相机及持久 gizmo。 |
| `ViewportSurfaceGrid` | 为 terrain shader 提供地表网格 GLSL 与 uniform 绑定，使用当前相机的小数坐标稳定远距离网格；嵌套绘制后恢复启用状态。 |

## 一帧的边界

1. `EditViewportRuntime.beginFrame` 在渲染线程冻结请求，处理窗口/世界变化与退出的 session，决定本帧源，并冻结相机、场景和选择。
2. `SharedGameFrame.beginFrame` 固定宿主身份、背景色和本帧世界预算。之后 UI 的挂载变化不能反向更改已开始帧的合成策略。
3. 正常 Minecraft 更新、提取和世界渲染在选中的 context 内运行。编辑世界 target 在窗口 resize 之后启用，在 GUI 提交前恢复。
4. 正常 GUI 边界捕获 GAME 的玩家画面与 HUD，或选中的 EDIT 画面。编辑帧把选择/吸附几何一同绑定到发布元数据。随后按宿主策略处理背景/深度，再提交编辑器 GUI。
5. UI 录制消费已发布纹理。`drawPresented` 返回实际保留图像的元数据；尺寸不符或通道已清空时跳过绘制。每个 renderer 的提交后资源退休仍遵循原版边界。

## 两种纹理生命周期

GAME 源复用一张发布纹理。首次捕获或尺寸变化时分配，每次有效捕获复制一次源画面，仅新纹理需要发布。成功发布后，生产者可继续写像素但不能自行关闭该纹理；撤销发布后，由消费者最后引用的释放回调回到 MC 线程关闭。因此 GAME 是共享实时画面，不具备 EDIT 的不可变历史像素语义。

EDIT 源每个 session 使用容量为 3 的 `FramePublicationPool<GpuTexture>`。一次捕获取得一个匹配尺寸的空闲 lease，将世界 target 复制一次后发布。成功发布的像素与 `PresentedViewportFrame` 在消费者引用退休前保持绑定。池满时不扩容、不覆盖仍使用中的纹理，保留上一张已发布图像。

发布成功转交一份纹理使用权。具体桥接持有一个发布引用，UI 录制与 GPU 命令各自保留所需引用；替换或 `clear()` 只撤销发布引用。全部消费引用退休后，桥接调用生产者提供的回调，生产者调度到 MC 线程：GAME 关闭纹理，EDIT 归还 lease。

resize/clear 时，池将 lease 标记为 retired。空闲资源立即关闭；仍在使用中的资源保留在池中并继续占用容量，直到回调释放后关闭。重复清理不能忘记这些 lease，否则慢 UI/GPU 会导致无界分配。窗口变化、世界切换、session 退出和 renderer 关闭均使用这套生命周期，不为 UI 再建一份租约池。

复制或发布失败时，尚未成功移交的 GAME 新纹理由生产者关闭；EDIT lease 归还原池。`publish` 返回 false 或抛错表示没有接收使用权，不能调用释放回调。已有 GAME 纹理复制失败时撤销发布，让消费者引用完成正常释放。

## 工厂与并发契约

`ViewportFrameSurface` 仅在有效捕获前创建通道，之后跨 clear/resize 保留同一实例。工厂未安装或返回 null 会明确失败；检查发生在发布纹理分配和复制之前。测试可以用 `ViewportFramePublications.installFactory`，或在同包使用 surface 的工厂构造参数注入实现。工厂每次必须生成独立通道；更换工厂不会替换已有 surface 的通道。

捕获、发布与清理属于 MC 渲染线程。UI 的尺寸查询通过通道锁读取一致快照；EDIT 的 `withFramePublication` 同时保持 session 准入锁。消费实现必须把“尺寸检查、选定当前帧、增加图像引用”放在同一个临界区，才能在并发 resize/clear 后继续安全录制原图像。

扩展后端只实现发布与消费适配，不移动世界提取/渲染，不增加 UI 回调中的 GPU 复制、读回或全局等待。现有验证包括 GL 像素/方向/alpha、Canvas 顺序/变换/裁剪、两通道与尺寸隔离、帧元数据、录制后延迟释放，以及租约容量、resize、失败重试和隐藏帧清理。
