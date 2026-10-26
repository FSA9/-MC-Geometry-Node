# 本地 MCP 传输与运行资源

这里把共享命令目录暴露为经过认证的本地 MCP 服务。协议线程只处理 JSON 和命令结果，通过 `McpCommandGateway` 访问宿主；实际线程切换、工作区绑定与图提交在 `integration.ai.mcp` / `integration.ai.graph`。

| 文件 | 职责 |
| --- | --- |
| [McpHttpServer.java](McpHttpServer.java) | 共享的 loopback HTTP 宿主与每次运行的独立 bearer 注册；校验请求来源、认证、大小、并发和速率。最后一个注册关闭时释放服务和执行器。 |
| [McpRequestDispatcher.java](McpRequestDispatcher.java) | JSON-RPC 初始化/版本协商、`tools/list`、`tools/call`、取消通知、重复请求预算、调用超时及结果大小限制。 |
| [McpToolCatalog.java](McpToolCatalog.java) | 从 `CommandRegistry` 截取模型可见且为只读或支持事务规划的可逆图写命令，生成工具 JSON 和名称索引；不是第二份手写命令定义。 |
| [McpCommandGateway.java](McpCommandGateway.java) | 接收命令、参数和取消令牌，异步返回 `CommandResult` 的窄接口；实现方负责在正确线程调用目标。 |
| [McpResultMapper.java](McpResultMapper.java) | 将命令结果及诊断映射到 MCP 工具结果。 |
| [McpJsonLimits.java](McpJsonLimits.java) | 协议 JSON 结构边界检查，补充 HTTP 字节大小限制。 |
| [McpPowerShellRun.java](McpPowerShellRun.java) | 为一次 PowerShell 运行持有 MCP 注册、启动描述与审计，将连接信息注入子进程环境；失败或关闭时释放资源。 |
| [McpToolEvent.java](McpToolEvent.java) / [McpToolEventListener.java](McpToolEventListener.java) | 可信控制通道发出的开始/成功/失败事件，约束长度并清理 bearer/token 文本。 |
| [McpRunAudit.java](McpRunAudit.java) | 一次运行的有界审计，最多保留 256 条事件；关闭后清空并停止接收。 |

## 生命周期和线程

`McpPowerShellRun.start` 建立目录、dispatcher、HTTP 注册和 PowerShell 启动描述。宿主将该运行资源与 terminal backend 绑定；进程退出、启动失败或标签关闭均应释放注册。dispatcher 关闭会置取消标志、清理活动调用并关闭 gateway，不能让旧运行继续操作后来的工作区。

初始化完成前拒绝普通工具请求。支持的协议版本以 `McpRequestDispatcher` 常量和协商集合为准；同一 dispatcher 不重复初始化。当前 HTTP 入口处理 POST，不提供独立 SSE GET 通道。

工具调用通过异步 gateway 执行；读取与图写入采用不同超时。取消通知、超时、中断和关闭设置同一调用的取消状态，适配层仍须在规划及提交边界检查。取消返回不能视为撤销已经发生的提交，应依据最终事务结果和幂等键处理重试。

## 稳定边界与扩展

本地地址由代码固定为 `127.0.0.1:37654/mcp`；子进程使用 `GEOMETRY_NODE_MCP_URL` 与 `GEOMETRY_NODE_MCP_TOKEN`。每个运行共享地址但拥有不同认证绑定，不能把关闭一个运行实现成关闭其他运行。

新增工具从 `command` 目录注册后投影，新增宿主接入实现 gateway。终端输出只是 VT 字节流，不能用其中的文本伪造 `McpToolEvent` 或推断工具已成功；审计只接受可信 MCP 控制通道事件。
