# 弹层与逻辑窗口生命周期

这里定义弹层公共协议，供工作区、独立弹窗和组件复用；实际 MainUI 层堆栈由 `shell/layer/MainUiLayerManager` 实现。

| 文件/子目录 | 职责 |
| --- | --- |
| `OverlayHost` | 从 anchor 定位宿主，提供 overlay root、模态及 tooltip 挂载入口。 |
| `UiOverlay`、`OverlayHandle`、`OverlayCloseReason` | 描述弹层 View/显示关闭回调、可关闭句柄及关闭原因；调用者保留 handle，不以 View 仍有 parent 判断可操作性。 |
| `OverlayMount` | 管理一次挂载的 owner、Area、窗口生命周期、可见/关闭及焦点归还。 |
| `OverlayEscapeCapture` | 允许子编辑器先消费 Esc，例如快捷键录制或弹窗内部菜单。 |
| `UiWindowLifetime` | 逻辑窗口的 `HIDDEN/VISIBLE/CLOSED` 状态，独立于 View detach 或布局重建；观察者需自行读取初始状态。 |
| `ephemeral` | `TransientOverlay` 与 `TransientOptions` 定义瞬态弹层、锚点和关闭行为。 |
| `modal` | `UiModal` 与 `ModalOptions` 定义模态窗口、owner、遮罩和关闭选项。 |

生命周期转换、订阅和挂载均由 UI 线程拥有。owner/Area 失效时解除交互与监听，安全时再移除 View；逻辑关闭不可逆，隐藏可以恢复。临时 detach 不应擅自关闭保留中的逻辑窗口或丢弃业务写入。焦点只归还给仍属于同一有效 owner/Area 的控件，并尊重用户主动转移。

完整弹窗内容放 `components/composite/overlay` 或对应业务视图；基础 popup 机制在 `components/base/menu`；本目录只扩展公共生命周期与挂载协议。异步操作完成时先投递 UI，再确认 handle/session 是否仍有效；写入完成本身由业务会话处理。
