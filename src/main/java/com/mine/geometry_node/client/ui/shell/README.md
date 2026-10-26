# MainUI 外壳与装配

`MainUiShell` 是单个 MainUI 的装配根及 `OverlayHost`：创建主菜单、`AreaLayoutRoot`、瞬态/模态/tooltip 三层，连接 modal 阻断与游戏视口输入。`MainUiServices` 持有本实例的 layer/menu/settings registry 和关闭动作；`find/require(anchor)` 沿视图祖先定位本实例，不提供跨窗口的全局 shell 单例。

| 子目录 | 关键文件与职责 |
| --- | --- |
| `layer` | `MainUiLayerHost` 提供层容器；`MainUiLayerManager` 管理弹层堆栈、键盘优先级、焦点、输入阻断与关闭，使用 `overlay` 的公共协议。 |
| `menu` | `MainMenuBar`、`MainMenuPopup` 显示菜单，`MainMenuController` 管理展开、更新与关闭。`api` 的 MainMenuDefinition/ItemDefinition/ItemType/Registry 描述菜单；`builtin/BuiltinMainMenus` 装配内置动作。 |
| `registration` | `BuiltinAreaEditors` 注册 Area factory、surface 类型并注入 `UiStateContext`、资产绑定和终端 runtime factory；`BuiltinSidebarPanels` 注册图属性、资产传输、游戏/场景属性和数据库详情侧栏。 |

依赖从 shell 指向 workspace、settings、具体 editor 和 `client/integration` 装配器；公共编辑器协议及组件不应依赖某个具体 shell 才能工作。确需共享窗口能力时优先通过 `OverlayHost` 和注入接口访问。

所有层操作在 UI 线程执行。`MainUiServices` 按注册逆序清理，关闭幂等，单个关闭动作异常不会阻止其他动作；`MainUiShell.destroy()` 先关服务和弹层，再关闭 Area 根，最终解除游戏输入阻断。服务只拥有本实例对象，图会话、共享仓库和渲染运行时不随 shell 自动销毁。

新增内置 Area/侧栏在 `registration` 装配，具体实现放 `editor`；新增菜单协议放 `menu/api`，内置菜单动作放 `menu/builtin`；新增弹层类型先扩展 `overlay` 协议，实际层堆栈策略放 `layer`。不要将业务仓库或游戏渲染逻辑放入装配根。
