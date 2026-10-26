# 游戏输入与绑定语义

| 入口 | 职责 |
| --- | --- |
| `binding/KeyScope` | 公共命令、图、场景、飞行等绑定作用域。 |
| `binding/KeyBinding`、`ControlKeyBinding`、`InputBinding` | 键值/修饰键、按住控制与统一匹配契约，不导入 ModernUI。 |
| `KeyBindings` | 注册 Minecraft 的模组入口和蓝图技能 KeyMapping。 |
| `ClientBlueprintInputManager` | 将游戏内键鼠状态变化同步给服务端蓝图，管理拦截掩码，离开连接时 reset。工作区游戏视口激活时允许游戏输入。 |

设置元数据与持久化属于 `config`；ModernUI 事件、焦点与当前宿主动作分派属于 `ui/shortcut`；游戏视口捕获属于 `runtime/game/input`。共享的复制/粘贴/删除等动作使用公共作用域，不在各编辑器重复注册配置。新增协议键时必须同步服务端的 `PlayerInputKeys`，不能仅改此处数组。
