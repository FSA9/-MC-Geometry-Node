# 终端与 MCP 的共同生命周期

| 文件 | 职责 |
| --- | --- |
| `TerminalRuntimeFactory` | 为单个终端 tab 创建运行装配的接口，供 UI 注入。 |
| `McpTerminalRuntimeFactory` | 实际组合终端会话、PowerShell/PTY 和 Minecraft MCP gateway。 |
| `TerminalRuntime` | 同时拥有一个 tab 的终端 session、协调器和控制通道，管理异步启动 attempt、取消、停止及关闭。 |

PTY/模拟器和进程协议在 `feature/terminal`；MCP 命令协议在 `feature/ai`；当前文档的 UI 线程适配在 `integration/ai`；终端 View 在 UI。异步启动完成前就关闭 tab 时，晚到的 PreparedRun 也必须释放，不能只移除 tab。
