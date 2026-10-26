# 工作区基础设施

本层管理编辑器的容器、身份和展示状态，通过工厂及 surface 协议承载具体 Area；内置编辑器由 `shell/registration` 注册。业务图、仓库、世界和渲染资源不由布局树拥有。

| 直接子目录 | 关键文件与职责 |
| --- | --- |
| `area` | `AreaLayoutRoot` 恢复、分割、关闭、交换及保存布局树；`AreaNode/LeafNode/SplitNode` 保存树与叶子缓存的窗口；对应 LeafView/SplitView 负责容器显示。`AreaEditorWindow/Factory/Registry/Type` 定义扩展协议；`AreaEditorMenu/IconButton/Style` 提供容器操作与局部样式；`AreaDragPayload` 表示区域内容交换。`AreaLifecycle`、`AreaTransientUi` 处理激活/隐藏/内容变更和短暂弹层，`SurfaceRegistrationAware` 接收注册身份。 |
| `drag` | `WorkspaceDragService` 统一 begin/update/finish/cancel；State 保存当前拖拽，Payload 为数据协议，Operation 是源端操作，Gesture 处理手势，DropRegistry 路由目标并处理 modal 阻断。`payload/DataLibraryDragPayload` 是数据库到其他 Area 的公共数据协议。 |
| `surface` | `UiSurfaceType/Id` 定义用户可定位的表面；`UiSurfaceRegistry` 发布 visibility、交互序号及 generation；`ViewportSurface`、`HistorySurface` 提供图视口/历史访问协议。 |
| `state` | `UiStateKey/Lifetime/Context` 提供版本化状态槽、清洗和快照；`UiStateScopes` 发布连接身份；`BuiltinUiStateKeys` 维护内置键与旧数据迁移；`EditorSidebarState` 封装侧栏偏好。 |
| `persistence` | `EditorSessionState` 是布局与状态槽的磁盘 DTO；`EditorSessionStore` 校验并读写布局 JSON；`EditorStateSlotsAdapter` 处理状态槽序列化。 |

## 生命周期与所有权

布局和窗口调用都在 UI 线程。叶子按编辑器类型缓存窗口，切换类型只隐藏旧窗口；删除叶子或关闭根布局才逐个 `onDispose`。交换区域内容连同窗口缓存和状态 owner 一起移动。`AreaLayoutRoot.close()` 清理生命周期、销毁窗口并保存最终布局。

`AreaLifecycle` 属于区域容器，发出激活、停用、内容变化、隐藏、detach 和 dispose 事件；订阅不自动重放初始状态，消费者需查询现状并在销毁时关闭订阅。surface 注册由承载层管理，编辑器只接收 registration 并发布交互信息；`Registration.close()` 撤销身份，编号复用后由 generation 区分，异步目标使用前校验 lease。注册表同步不意味着其 owner View 可以跨线程调用。

拖拽状态只传输当前操作，不保存业务权威值。源隐藏/detach、modal 阻断或手势取消时结束源端捕获，目标验证 payload 与当前业务状态后才提交。新增跨 Area 数据格式放 `drag/payload`，各业务的手势与落点规则仍归各自编辑器。

## 状态与持久化

`UiStateContext` 以稳定 `ownerId + scope + key` 存储分离快照。`MEMORY` 跨 UI 实例保留到本进程结束，`PERSISTENT` 写入布局状态槽；普通 UI teardown 保留 owner 内存，明确删除区域才 `forgetOwner()` 撤销旧上下文。键定义包含版本、默认值、清洗和迁移；不能存 View、监听器、业务会话或无限增长的对象 ID scope。

`UiStateScopes` 在 Minecraft 客户端线程发布连接身份；监听在发布线程执行，消费者应 post 到 UI。世界/玩家身份经哈希形成 scope，断线使用 `disconnected`，不要从 UI 线程自行读取世界来构造身份。

`EditorSessionStore` 的 load/save 是同步文件操作；`AreaLayoutRoot` 当前在 UI 队列中合并延迟保存（300ms），并非后台自动保存器。保存复制快照、校验树和体积，用临时文件替换；较新版本文档不覆盖。组件只报告用户变更，编辑器决定写哪个键；先恢复、后安装监听，不能将自动测量反写成用户偏好。

新增容器机制放 `area`，新身份协议放 `surface`，通用状态机制放 `state/persistence`；某一 Area 专用状态键优先放该编辑器的 state 包。图命令、撤销历史与图文件 IO 放 `feature/graph`、`client/history`，不扩展工作区布局存储。
