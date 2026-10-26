# 对话与任务的具体呈现装配

本目录只有 `RuntimeUiIntegration`，把 `runtime/dialogue/DialoguePresentation` 和 `runtime/quest/QuestScreenPresentation` 绑定到 `ui/screen` 的实际 renderer。状态和网络动作仍归 runtime，Fragment、屏幕与 UI 线程调度仍归呈现实现；integration 不另存一份会话。

`GeometryNodeClient` 构造时先调用 `install()`，再执行 `ClientNetworkReceiverRegistry.init()`。两个 adapter 都是固定实例，因此重复 install 幂等，不会更换活动屏幕的 owner。runtime 拒绝安装不同实例，未安装就调用有效呈现操作会明确抛错。

`tick()` 转交对话 renderer，保留两 tick 关闭宽限及同样式刷新行为。logout 由客户端入口调用两个 state 的 reset，adapter 把 clear 传给屏幕实现；保留安装绑定，下一连接继续复用。任务屏幕活动检查、打开/刷新、关闭分别通过窄接口完成，不用 ModernUI 类型污染状态包。

装配本身不分配 GPU、文件或 View；真正的 Minecraft 主线程调度、ModernUI UI 线程 refresh、返回前一屏幕和鼠标恢复都在具体 renderer。新增对话样式通过 UI 样式 registry 的 create/refresh/title 扩展，新增 marker renderer 走 `ui/hud/marker` 的统一 render/preview 注册表，不在本目录再造另一套注册系统。

viewport 的宿主/绘制装配属于 `integration/game`，不由本类承担。
