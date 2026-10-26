# 工作区视口与 ModernUI 接入

本目录连接 [游戏视口运行时](../../runtime/game/README.md)、具体工作区宿主和 ModernUI/Arc3D。世界渲染、纹理分配/复制、session 和 EDIT 租约池由 runtime 拥有；这里负责宿主分类及外部纹理到 Canvas 的消费适配，不保存第二份 owner→帧映射或租约池。

| 文件 | 职责 |
| --- | --- |
| `WorkspaceViewportIntegration` | 应用启动时安装发布工厂、工作区宿主策略和原生场景订阅条件。 |
| `WorkspaceWorldObjectNavigation` | 在动作发生时查找仍有效的可见视口，将查询结果定位到场景；隔离数据库与具体游戏 Area 的交叉引用。 |
| `GameFrameImage` | GAME 画面查询/绘制和 EDIT 的 `drawPresented` 入口；从 runtime 借用已有通道，保持 session 准入与录制锁范围。 |
| `GameFrameImage.Channel` | `ViewportFramePublication` 的具体实现，将 MC 的外部 GL 纹理包装成 Arc3D 图像，管理发布引用和 Canvas 录制。 |

## 安装顺序与宿主能力

应用的已有启动入口调用一次 `WorkspaceViewportIntegration.install()`，不从 View 绘制回调安装。该方法按顺序执行：

1. `ViewportFramePublications.installFactory(GameFrameImage.Channel::new)`，为每个 runtime surface 提供独立通道。
2. `ViewportHostPolicy.install(...)`，识别 `MuiScreen`/`MainUI`，并读取工作区背景色。
3. `SceneEditBackends.configureNativeSubscription(...)`，让原生实体订阅取决于当前工作区及视口显示需求。

`MainUI` 宿主同时是工作区并替换保留工作区；其他 `MuiScreen` 只声明替换保留工作区；普通原版屏幕返回 `Snapshot.NONE`，允许暂停后恢复原编辑器。runtime 输入代码消费这些能力，不判断具体 ModernUI 类型。

工厂必须早于任何有效帧捕获安装。surface 在首次捕获前惰性创建通道，普通尺寸/可用性查询不会创建通道；未安装工厂时捕获在分配发布纹理前明确抛出异常。工厂不得把一个 Channel 返回给多个 surface。替换工厂只影响尚未创建通道的 surface，不迁移已有帧。测试可安装自己的工厂，或在 runtime 同包通过 surface 构造参数注入，并在结束时恢复共享安装状态。

## GL 纹理、录制与释放

`Channel.publish` 仅在 MC 渲染线程执行，当前实现接受有效的 `GlTexture`、RGBA8 格式和可用的 Arc3D GL device。非支持后端返回 false，不接管纹理使用权；线程错误直接抛出。后端失败与“工厂未安装”是两个独立边界。

包装后的 Arc3D 对象不直接删除 Minecraft 拥有的外部 GL 名称。成功发布持有一份引用，替换/清除时撤销该引用。UI 录制在通道锁内检查预期尺寸并增加当前图像引用；随后用下原点、`RGB1` 不透明采样及线性过滤放入正常 Canvas 绘制顺序，继承 Canvas 的变换、裁剪和后续覆盖。

每次录制保留自己的图像引用。`GraniteImage`/view 的临时引用在录制后释放，但 recording/GPU 仍可持有资源；直到最后引用退休才运行 runtime 提供的释放回调，由回调回 MC 线程关闭 GAME 纹理或归还 EDIT lease。UI 的 detach、resize、clear 不直接关闭仍在使用的纹理。

EDIT 的 `GameFrameImage.drawPresented` 通过 `EditViewportRuntime.withFramePublication` 在原 session monitor 内录制，通道锁再保证尺寸检查与保留引用原子完成。返回元数据来自实际录制的图像。并发替换后仍使用旧帧的相机、选择与吸附信息，不拼接最新 runtime 状态；失配尺寸或空通道返回未绘制。

## 扩展与验证

新增后端时实现 runtime 的 `ViewportFramePublication` 契约，并提供与该实现匹配的消费绘制入口。当前 `GameFrameImage` 会明确拒绝非自身 Channel 的绘制，不能只替换工厂却继续使用不匹配的 Canvas 适配。世界/HUD 提取、GPU copy、三槽 EDIT 池和最终关闭回调继续归 runtime。

相关 GL 测试仍位于 `src/test/java/com/mine/geometry_node/client/runtime/game/render/GameFrameImageGlTest.java`，通过 `geometry_node.test.gl` 开启。覆盖实际像素、上下方向、不透明 alpha、Canvas 顺序/变换/裁剪、两通道尺寸和像素隔离、清除后录制资源存活及最终释放；它们不等价于完整 MC 客户端或输入路由验证。租约容量/resize、发布失败、分辨率与隐藏帧清理另由 runtime 测试覆盖。
