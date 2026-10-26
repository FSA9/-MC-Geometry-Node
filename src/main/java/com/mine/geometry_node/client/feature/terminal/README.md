# 终端会话与进程核心

本目录提供线程安全的终端会话、进程字节通道、VT 屏幕模型与输入编码。它不依赖 AI、图文档、Minecraft 窗口或 ModernUI。命令模式的 CLI 执行由外部适配处理，shell 模式才使用进程 backend。

## 根目录与子系统

| 文件/目录 | 职责 |
| --- | --- |
| [TerminalSession.java](TerminalSession.java) | 单个终端的身份、标题、模式、profile、backend 和运行状态所有者；控制启动、写入、resize、中断、停止和销毁。 |
| [TerminalBackend.java](TerminalBackend.java) / [TerminalBackendListener.java](TerminalBackendListener.java) | 进程通道及启动/输出/退出通知契约。backend 的关闭必须幂等且不抛异常。 |
| [TerminalSessionListener.java](TerminalSessionListener.java) | 会话筛选过的状态、输出和退出通知；不绑定 UI 调度器。 |
| [ResourceOwnedTerminalBackend.java](ResourceOwnedTerminalBackend.java) | 将额外 `AutoCloseable` 资源绑定到 backend，在启动失败、退出或关闭时仅释放一次，可供 integration 绑定 MCP 等运行资源。 |
| [ProcessLaunchSpec.java](ProcessLaunchSpec.java) | 不可变 argv、环境、工作目录及初始尺寸；校验空程序、参数和环境项，使用参数列表而非待拼接的 shell 命令串。 |
| [TerminalMode.java](TerminalMode.java) / [TerminalRunState.java](TerminalRunState.java) | `COMMAND` / `SHELL` 模式与运行状态，统一判断能否输入和是否仍持有活动 backend。 |
| [TerminalSize.java](TerminalSize.java) / [TerminalExit.java](TerminalExit.java) | 终端尺寸与进程退出原因/结果值。 |
| [pty](pty/README.md) | backend 的异步字节 I/O、退出协调与真实 PTY 实现。 |
| [emulator](emulator/README.md) | 有界 VT/xterm 屏幕和不可变快照；不直接绘制。 |
| [shell](shell/README.md) | PowerShell 启动策略与 session、emulator、输入队列的协调。 |
| [input/TerminalInputEncoder.java](input/TerminalInputEncoder.java) / [TerminalKey.java](input/TerminalKey.java) | 文本/IME 提交、按键、控制字符、鼠标滚轮与粘贴到 xterm 字节的转换。按 emulator 当前模式选择序列；粘贴最多 1 MiB，并支持 bracketed paste。 |

`input` 只有编码器与键枚举，由本页覆盖。剪贴板读取、字体、选区、滚动位置和像素坐标换算由 UI 完成。

## 会话生命周期

状态从 `IDLE` 经 `STARTING` 到 `RUNNING`，正常结束或失败进入 `EXITED` / `FAILED`；停止经过 `STOPPING`，关闭永久进入 `DISPOSED`。`COMMAND` 模式不能启动进程 backend；活动 backend 存在时不能切换模式/profile。停止后可以为同一 session 启动新的 backend，已销毁 session 不可复用。

每次启动创建新的 run ID，通知必须同时匹配 backend 身份和 run ID；控制 revision 与启动代次处理启动过程中发生的 stop/close。旧进程的迟到输出或退出不能覆盖新运行。输入及输出字节跨边界复制，事件序号防止迟到状态倒退。监听器不保证运行在 UI 线程，宿主必须自行转发。

## 装配与扩展

[integration.terminal.TerminalRuntime](../../integration/terminal/TerminalRuntime.java) 管理异步准备、启动取消和一组 session/coordinator 的释放；factory 决定是否附带 MCP 等额外资源。UI 消费终端快照与 observer，不直接接触原生进程。

新增进程实现优先实现 `PtyProcessFactory` / `PtyProcessHandle`；完全不同的通道实现 `TerminalBackend`。新增 shell 策略产出 `ProcessLaunchSpec`，无需让终端核心识别 AI 或业务文档。释放宿主时要同时终止 session 和停用 coordinator，不能只移除屏幕 observer。
