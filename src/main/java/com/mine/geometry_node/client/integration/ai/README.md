# AI 命令到当前工作区的适配

`feature/ai` 只定义命令、参数、结果、目标能力和 MCP 传输。本目录决定这些能力对应哪个当前文档、surface 和线程。

| 路径/文件 | 职责 |
| --- | --- |
| `mcp/MinecraftClientMcpGateway` | 接入 Minecraft 客户端/ModernUI 调度，提供节点目录、工作区 surface、历史和图操作能力。 |
| `history/HistorySurfaceQueries` | 将可用 surface 的历史目标转换为命令查询结果。 |
| `graph/ViewportGraphContextService` | UI 线程上解析并描述 surface、文档、viewport，是选择当前图目标的统一入口。 |
| `graph/ViewportGraphTargetResolver` | 单次 MCP 调用解析目标，并在 run 范围复用绑定目标；关闭释放缓存与租约。 |
| `graph/BoundGraphScope`、`BoundGraphTarget` | 固定 session/子图范围并再次校验目标是否有效；不能在后台重新选择别的当前图。 |
| `graph/GraphEditorCommandTarget`、`NodeCatalogQueryTarget` | 将实际图修改/查询和无打开图时的节点目录适配为协议能力。 |
| `graph/GraphReadSnapshot`、`GraphReadSnapshotCache`、`PreparedGraphQueryTarget` | UI 线程捕获文档内容，后台建立不可变查询索引，按 revision 有界缓存。 |
| `graph/PreparedGraphPatchTarget`、`GraphPatchTransactionService` | 先准备能力与试算，再回 UI 线程校验 session/scope/revision 并一次性提交可撤销补丁。 |
| `graph/GraphPatchIdempotencyStore` | 同一 run 的补丁幂等状态，避免重试重复修改。 |

查询后台不得读取正在修改的 EditorContext；修改不能绕开历史与文件 claim。准备阶段通过不代表提交时仍授权，surface 关闭、文档切换、取消和 revision 变化均需重新校验。图试算沿用真实 FrameBoundsPolicy，但独立拥有试算历史和通知者。
