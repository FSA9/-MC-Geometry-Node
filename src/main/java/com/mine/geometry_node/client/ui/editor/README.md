# 工作区编辑器（Area）

本目录实现 `workspace/area/AreaEditorWindow`。布局树通过注册工厂创建并缓存窗口；类型切换走 `onShow/onHide`，真正销毁走 `onDispose`。Area 工厂集中装配于 `shell/registration/BuiltinAreaEditors`，表面标识由 workspace 注册，不由子页自行申请。

| 直接子目录 | Area 入口与职责 |
| --- | --- |
| [graph](graph/README.md) | `GraphEditorWindow`：图标签、画布、节点/连线/Frame、交互及图属性侧栏。 |
| [game](game/README.md) | `GameViewportWindow`：游戏画面、编辑相机、场景选择和变换交互。 |
| [asset](asset/README.md) | `AssetBrowserWindow`：将公共资产浏览组件装配为 Area，注入文档、属性和传输能力。 |
| [datalibrary](datalibrary/README.md) | `DatabaseWindow`：世界对象、数据仓库和全局数据三页及属性侧栏。 |
| [terminal](terminal/README.md) | `TerminalWindow`：控制台/系统 shell 标签、输入输出和 runtime 生命周期。 |

`PERFORMANCE` 当前由 `BuiltinAreaEditors` 创建占位视图，树内没有独立 performance Area 实现目录。

编辑器依赖公共 `components`、`workspace` 协议及业务 `feature/runtime`；跨能力操作通过 `client/integration` 或注入接口接入。不要从具体 Area 之间互相借用内部 View；共享资产、实体、装备等 UI 进入 `components/integration`。

## 状态和资源约定

View、草稿与交互在 UI 线程维护，异步返回先投递再检查有效性。`onHide` 解除输入捕获、菜单和可见页订阅，保存展示偏好；`onDispose` 解除永久监听、终止批量挂载并关闭本窗口拥有的 runtime/读取会话。View detach 可以只是布局重建，不能一律当作业务会话关闭。

图会话归 `GraphDocumentManager`，游戏渲染与世界数据归 `runtime`，仓库归 `feature`；Area 只拥有其展示绑定、surface 协议引用与订阅。已提交业务操作的完成处理必须独立于 View 是否存在。

新增 Area 在对应新子目录实现窗口，在注册处装配 factory、surface 类型及状态；新增侧栏实现 `SidebarPanel`，按 scope 注册。UI 状态使用稳定键与 owner/连接 scope，业务命令留在 `feature`。
