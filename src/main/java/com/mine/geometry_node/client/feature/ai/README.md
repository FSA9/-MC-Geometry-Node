# AI 命令、协议与 MCP 传输

本目录保存运行环境中立的 AI 契约、CLI/工具命令目录、图补丁格式和本地 MCP 服务。它不持有编辑器窗口、活动标签或可变工作区集合，也不实现聊天界面或具体模型提供商客户端。

| 子目录 | 定位与关键入口 |
| --- | --- |
| [command](command/README.md) | `CommandCatalog.registry()` 是 CLI 与 Agent 共用的生产命令目录；`CommandSpec` 描述参数、schema、效果、可见性和处理函数，目标通过能力接口注入。 |
| [protocol](protocol/README.md) | `AiProtocol`、`ProviderCapabilities`、`ToolContract` 及 schema 校验器，定义消息、流事件、能力和工具契约。 |
| [graph](graph/README.md) | `GraphPatch` 声明式图修改协议、显式编解码、引用顺序检查、端口值转换与能力限制。 |
| [mcp](mcp/README.md) | 经过认证的本地 HTTP/JSON-RPC 入口、工具投影、取消/超时、调用审计，以及与 PowerShell 运行资源的绑定。 |

## 依赖方向

`integration.ai / UI → feature.ai → core、通用契约`。命令通过 `GraphCommandTarget`、`GraphPatchCommandTarget`、`GraphQueryTarget`、`UiSurfaceQueryTarget`、`HistoryQueryTarget` 调用宿主能力；这些接口不反向导入具体窗口或文档管理器。`NodeCatalogIndex` 读取 core 节点目录，MCP 的 PowerShell 生命周期桥接可以依赖 `feature.terminal`；终端核心不依赖 AI。

具体工作区适配在 [integration.ai.graph](../../integration/ai/graph)：目标绑定、读快照、图查询、补丁规划、幂等缓存和最终提交。历史表面查询在 `integration.ai.history`，客户端线程/窗口绑定网关在 `integration.ai.mcp`。涉及“当前哪个 viewport、哪份 session、哪个组作用域”的实现应进入这些目录。

## 从命令到修改

CLI 文本先解析为命令与类型化参数，MCP 从同一目录投影模型可见工具。registry 检查调用来源、取消、目标能力和 schema 后调用注入目标，统一返回 `CommandResult`。

模型可见的图写入入口为 `apply_graph_patch`。补丁携带 session、scope、期望 revision 和幂等键，适配层绑定目标、在独立图上规划及验证，重新检查目标和 revision 后作为一次可撤销编辑提交。这里的协议校验只证明请求结构有效，不代表工作区仍然有效或修改已经提交。

新增命令应先补充目录契约及目标能力，再由 integration 实现；新增补丁操作同时更新模型、codec、契约校验和事务适配。协议字段、短节点类型 ID、命令名和 MCP 环境变量属于兼容边界，不能随 Java 目录迁移改名。
