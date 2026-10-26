# PTY 字节传输与原生进程

这一层把平台进程包装成 `TerminalBackend`，只运输字节，不解释 VT 输出，不执行 CLI 业务命令，也不认识 MCP。

| 文件 | 职责 |
| --- | --- |
| [PtyProcessFactory.java](PtyProcessFactory.java) | 按 `ProcessLaunchSpec` 启动进程的替换点，可由测试或其他平台实现。 |
| [PtyProcessHandle.java](PtyProcessHandle.java) | 输入/输出流、异步退出码、resize、中断、终止和资源释放的窄接口。 |
| [PtyTerminalBackend.java](PtyTerminalBackend.java) | 一次启动的 backend：虚拟线程读取输出、串行输入、通知排序、停止及进程退出/输出耗尽协调。 |
| [pty4j/Pty4jProcessFactory.java](pty4j/Pty4jProcessFactory.java) | 用 pty4j 创建真实终端，应用 argv、环境、工作目录和尺寸；Windows 请求 ConPTY，并在挂起启动回调中加入 Job Object。 |
| [pty4j/Pty4jProcessHandle.java](pty4j/Pty4jProcessHandle.java) | 将 pty4j 进程操作适配为公共 handle，处理退出等待、平台中断、终止和流关闭。 |
| [pty4j/WindowsJobObject.java](pty4j/WindowsJobObject.java) | Windows 子进程树的 Job Object 所有者，使用关闭时杀死 job 的策略；启动阶段验证已分配，避免标签关闭后遗留后代进程。 |

`pty4j` 是本层的平台叶子实现，JNA/pty4j 细节只进入这里；上层依赖 factory/handle 接口。

## 退出和关闭

backend 只能启动一次。启动期间关闭后，即使原生进程才返回，也会关闭该进程而不重新进入运行态。启动失败清理部分创建的进程和 Job Object。

正常进程退出后先等待输出流耗尽，再报告退出，防止末尾输出丢失；若输出长时间不结束，使用有界 drain 等待。用户关闭则发起异步终止，并经过有限宽限处理。原子退出标记保证多条路径至多发布一次退出；输入锁保护写入与中断。

新增平台实现需保留这些终止、末尾输出和一次退出语义。不要在读输出线程等待 UI，也不要把输出文本当作业务控制事件；屏幕解释由 `emulator` 承担。
