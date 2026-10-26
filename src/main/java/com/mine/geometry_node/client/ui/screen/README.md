# 游戏内整页界面

本目录提供游戏内对话、商店和任务 ModernUI Fragment，独立于编辑器工作区。运行时维护业务状态并通过展示接口调用 UI；它们不是 `AreaEditorWindow`，不进入 Area 布局持久化。

| 直接子目录 | 关键文件与职责 |
| --- | --- |
| `dialogue` | `DialogueStyleRenderer` 根据会话打开、刷新和关闭 Screen；`ClientDialogueStyleRegistry` 将样式 ID 映射到客户端 renderer。`RpgDialogueFragment` 显示 RPG 对话，`ShopMenuFragment` 显示商店；`VanillaComponentView` 通过 Minecraft surface 显示原版 Component。 |
| `quest` | `QuestScreenRenderer` 打开/关闭/刷新任务 Screen，`QuestScreenFragment` 展示任务快照、目标、奖励和提示；`QuestProgressBar` 绘制进度，`QuestScreenStyle` 组织页面样式。 |

Screen 安装和关闭在 Minecraft 客户端线程执行，已有 Fragment 的刷新通过 `MuiModApi.postToUiThread` 进入 UI 线程。对话 renderer 按 session ID 追踪当前页面与待关闭会话；不要让旧会话关闭新页面。Fragment 读取 `runtime/dialogue/ClientDialogueState` 或 `runtime/quest/ClientQuestScreenState` 并将用户操作交给运行时，不能把 Screen/View 存进业务快照。

Fragment 拥有自己的视图树、临时弹层和原生文字 surface，随页面生命周期释放；权威任务/对话状态及网络确认归 runtime。主题显式使用 HUD，原版文字和物品内容色保留。新增游戏内页面放新的能力子目录，展示桥接放 `client/integration/runtime`，业务协议和状态留在 runtime/core；可复用内容预览放 `components/integration`。
