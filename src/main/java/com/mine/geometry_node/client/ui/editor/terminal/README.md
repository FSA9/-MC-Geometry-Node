# 终端 Area

`TerminalWindow` 实现 `TERMINAL` Area，拥有终端标签、每个标签的 `TerminalSession`、View 和注入工厂创建的 `TerminalRuntime`。会话模型、shell 进程与命令协议位于 `client/feature/terminal` 和 `client/feature/ai`，工作区/MCP 装配位于 `client/integration/terminal`。

| 文件 | 职责 |
| --- | --- |
| `TerminalTabBar`、`TerminalChrome` | 标签栏动作和终端视觉装饰。 |
| `ConsoleView` | 内置命令输入、补全和输出展示。 |
| `ShellTerminalView` | shell 终端字符显示、键盘输入和激活/停止展示订阅。 |
| `ConsoleCommandRegistry` | 对共享 `CommandCatalog.registry()` 的 UI 门面，不维护独立命令定义。 |
| `ConsoleCommandAdapter` | CLI 解析、结构化结果到 UI 日志/清屏动作的转换，通过 `GraphEditorCommandTarget` 提供图命令目标。 |

UI 线程拥有标签和文本显示；进程 IO 与命令运行由 runtime/feature 处理，输出回 UI 后再更新视图。`onHide` 保存标签状态并停用当前 shell View，保留标签 runtime；`onDispose` 逐个关闭标签，先 `shellView.dispose()` 再 `runtime.close()`，清理视图和监听。持久化只记录可恢复的标签配置，不保存活进程或 View。

新增终端视觉/交互放本目录，新增共享命令放 `feature/ai/command`，shell 行为放 `feature/terminal`，外部工具和工作区目标的装配放 `client/integration`。本目录无子目录。
