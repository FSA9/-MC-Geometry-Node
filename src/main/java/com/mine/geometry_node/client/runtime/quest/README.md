# 任务屏幕的运行状态

`ClientQuestScreenState` 保留最后一次任务快照和是否处于本地预览；它通过 `QuestScreenPresentation` 操作屏幕，不持有 Fragment。具体任务布局、进度条、样式和屏幕管理位于 `ui/screen/quest`。

服务端快照的接纳规则是：`openScreen=true` 可以打开屏幕；否则仅当呈现接口报告屏幕仍活动时才接纳并刷新。被忽略的更新不改当前快照或预览标记。被接纳的服务端快照会退出预览模式。`openPreview` 不发送 OPEN，请求为空时直接返回 false。

预览期间 ACCEPT、SUBMIT、ABANDON 均返回 false，不发送网络动作；关闭预览清空本地快照且不发送 CLOSE。普通任务屏幕仅在活动时发送动作；关闭后保留最后一次服务端快照，`reset()` 才完整清空。logout 的 reset 调用 `clear()`，不会发送玩家主动关闭协议。

呈现接口由 `integration/runtime` 在网络注册前安装。缺少安装会明确报错；同一实例重复安装不重开屏幕，不同实例再次安装被拒绝。reset 不卸载它，防止旧屏幕的关闭和刷新被交给另一个 presenter。

包接收在客户端线程执行，ModernUI 回调只使用状态 API。快照/预览标记使用 `volatile` 发布，复合状态更新仍应按客户端与界面既有调用链协调；呈现层负责 Minecraft 主线程和 ModernUI UI 线程之间的切换。
