# Shell 启动策略与会话协调

本目录连接公共终端会话、PTY 和 VT 模型，不依赖 UI 或 AI。异步运行准备及 MCP 资源由 `integration.terminal` 装配。

| 文件 | 职责 |
| --- | --- |
| [PowerShellProfile.java](PowerShellProfile.java) | 当前 Windows PowerShell 启动策略。优先寻找 `pwsh.exe`，回退 Windows PowerShell，设置终端环境并构造 `ProcessLaunchSpec`；使用 `-NoLogo -NoProfile`，不修改用户 shell 配置。非 Windows 当前明确失败。 |
| [ShellTerminalCoordinator.java](ShellTerminalCoordinator.java) | 作为 session listener 将输出交给 emulator；编码输入、发送终端响应、合并原生 resize，并管理有界输入队列及运行代次。 |
| [ShellTerminalObserver.java](ShellTerminalObserver.java) | 屏幕变化、运行状态、退出和错误的呈现通知；没有 View 或 UI 线程依赖。 |

## 绑定与运行

coordinator 通过构造取得初始尺寸和进程 factory，再作为 `TerminalSessionListener` 注入 session，最后 `bind(session)`，一个 coordinator 只能绑定一次。observer 可替换或设为 NOOP；其回调可能来自后台线程，UI 适配负责线程切换。

`start` 使用指定 factory 启动普通 backend；`startManaged` 使用已注入 factory，并把额外运行资源包装进 `ResourceOwnedTerminalBackend`。开始新运行时递增 epoch、清理旧队列并重置 emulator，防止旧输入和原生 resize 落到新进程。

文本、按键、paste 和中断进入有界队列，由单一 worker 串行执行；相邻重复中断会合并。resize 先更新屏幕模型，再由 worker 应用最新原生尺寸。输入/resize 都检查 epoch，并用原生操作锁与启动协调；队列溢出通过 observer 报错。

`stop` 清理待处理操作并停止 session。`dispose` 只取消待处理操作、解除 observer，不关闭 session；宿主完整销毁时还必须调用 `session.close()`。当前 `TerminalRuntime.close()` 负责组合这两步。

扩展 shell 时新增产出启动描述的策略，保持 coordinator 不认识业务。输入编码扩展放到 `terminal.input`，VT 状态扩展放到 `terminal.emulator`；不要在 observer 中解析进程文本来判断外部工具成功。
