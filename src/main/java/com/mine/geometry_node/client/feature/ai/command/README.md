# CLI 与模型工具的共享命令目录

此目录定义“可调用什么、参数如何表达、结果如何返回”。实际图和 UI 上下文由能力接口注入；目录不定位窗口，不直接修改活动文档。

## 定义、解析与执行

| 文件 | 职责 |
| --- | --- |
| [CommandCatalog.java](CommandCatalog.java) | 建立并公开唯一生产 registry；注册 `clear`、`help`、CLI 图操作、查询/历史目录与 `apply_graph_patch`。 |
| [CommandRegistry.java](CommandRegistry.java) | 有序注册和别名查找、参数补全、默认参数、输入/输出 schema 校验及处理函数调用。拒绝未注册的 spec、Agent 调用 `CLI_ONLY` 命令和执行前已取消的请求。 |
| [CommandSpec.java](CommandSpec.java) | 单条命令的名称、别名、说明、用法、参数、结果 schema、效果/风险、暴露范围及 handler；从同一份定义生成工具 schema。 |
| [CommandArgumentSpec.java](CommandArgumentSpec.java) | 位置参数的类型、必填/默认值、schema 及补全提供器。 |
| [CliCommandParser.java](CliCommandParser.java) | 引号/转义处理、类型转换及部分输入解析；结果为已解析命令，部分输入用于补全。 |
| [CommandInvocationContext.java](CommandInvocationContext.java) | 调用来源 `CLI` / `AGENT`、当前能力目标与取消令牌；目标实现由具体适配持有。 |
| [CommandResult.java](CommandResult.java) | 成功状态、错误码、结构化数据、诊断、变更关联及客户端动作；渲染和动作执行由调用方处理。 |

## 查询与能力边界

| 文件 | 职责 |
| --- | --- |
| [GraphCommandTarget.java](GraphCommandTarget.java) | CLI 图操作和节点/端口补全需要的窄接口。 |
| [GraphPatchCommandTarget.java](GraphPatchCommandTarget.java) | 将批准的图补丁交给事务适配执行。 |
| [GraphQueryTarget.java](GraphQueryTarget.java) | 图统计、节点、端口、节点目录等只读查询能力。 |
| [UiSurfaceQueryTarget.java](UiSurfaceQueryTarget.java) | UI 表面和 viewport 上下文查询契约，不暴露 View。 |
| [HistoryQueryTarget.java](HistoryQueryTarget.java) | 按表面引用查询历史状态；具体历史仍由图或场景领域持有。 |
| [QueryCommandCatalog.java](QueryCommandCatalog.java) | 只读工具定义、分页参数和查询目标分派，注册进同一个生产 registry。 |
| [HistoryCommandCatalog.java](HistoryCommandCatalog.java) | 历史状态查询定义及 `HistoryState` 到命令结果的转换；不维护另一套 undo/redo 栈。 |
| [NodeCatalogIndex.java](NodeCatalogIndex.java) | 将 core 节点菜单树投影为目录、条目和工具说明，处理模型短类型 ID 与内部规范类型 ID 的转换。 |

## 扩展与生命周期

registry 在初始化时注册，命令名和别名冲突立即拒绝。每次调用独立接收参数副本和 context，执行前先应用默认值并校验输入，成功结果再检查输出 schema；处理函数的普通运行时异常转为结构化内部错误。取消令牌必须继续传给异步目标，不能只在命令入口检查一次。

新增功能时先决定 `Exposure`、效果和风险，再定义参数及输出 schema；已有目标能力可复用，需要新宿主能力时增加窄接口，由 `integration.ai` 实现。CLI 与 MCP 必须共享命令定义，避免两边参数、帮助文本和验证规则分叉。不要把 UI 激活或目标重选埋进通用 handler；后台标签也应按显式 session 绑定。
