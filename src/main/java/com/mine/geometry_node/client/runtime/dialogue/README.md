# 对话会话、预览与呈现边界

`ClientDialogueState` 拥有当前 `PacketOpenDialogue`、本地预览页列表及翻页位置。选项、交易和关闭动作在这里决定是否发送公共网络协议；Fragment、富文本转 ModernUI、样式注册和屏幕恢复位于 `ui/screen/dialogue` 与 `ui/components/integration/text`。

`DialoguePresentation` 仅提供 `supports/open/close/clear`。`integration/runtime/RuntimeUiIntegration` 必须在网络接收注册前安装它。未安装便接收有效页面、打开有效预览或执行关闭/reset 会明确抛出装配错误，不会静默丢掉对话。同一实例重复安装幂等；不同实例再次安装被拒绝，即使当前没有活动页面也不能更换客户端生命周期绑定。

预览会复制页列表，只允许已启用的选项前进到下一页；最后一页不自动跳出。预览不发送选择/交易协议，关闭预览立即清空本地状态并调用 `clear()`。服务端页面会接替预览；已安装 presenter 不支持服务端样式时，客户端记录警告并发出关闭动作。

普通服务端关闭按 session UUID 匹配当前状态，并把 UUID 交给呈现层；两 tick 关闭宽限、界面复用和恢复前一屏幕属于呈现层。logout 调用 `reset()` 清除当前页和预览并立即清理呈现，但保留已安装接口，下一次连接继续复用。

网络入口排队进入 Minecraft 客户端线程，UI 的选择回调使用同一状态 API。`volatile` 字段用于读取已发布状态，并不把翻页等复合操作变成通用并发事务；屏幕创建和 ModernUI 线程切换由呈现实现承担。新增样式通过 UI 的 `ClientDialogueStyleRegistry.Renderer.create/refresh/title` 扩展，不要让 runtime 引用 ModernUI。
