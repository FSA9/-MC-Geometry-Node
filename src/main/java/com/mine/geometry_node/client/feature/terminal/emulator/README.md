# 有界 VT 屏幕模型

这里将 PTY 字节流转换成终端单元格和模式状态，不依赖 UI、原生 PTY 库或业务命令。`ShellTerminalCoordinator` 输入字节并读取响应，界面读取快照后自行绘制。

| 文件 | 职责 |
| --- | --- |
| [VtTerminalEmulator.java](VtTerminalEmulator.java) | 增量 UTF-8 与 VT/xterm 控制序列解析、光标/样式、主屏/备用屏、滚动区域、回滚缓冲、resize 及设备响应。同步入口允许输出线程更新、呈现线程读取。 |
| [TerminalSnapshot.java](TerminalSnapshot.java) | 带 revision、尺寸、行数据、光标及输入相关模式的不可变快照，构造时复制行集合。 |
| [TerminalCell.java](TerminalCell.java) | 单元格文本、样式和显示宽度，供 Unicode 宽字符等布局使用。 |
| [TerminalStyle.java](TerminalStyle.java) | 前景/背景色、粗体、下划线、反色及默认样式值。 |

## 数据流与边界

`accept` 接收任意分块字节，解析器保留跨块 UTF-8/控制序列状态；`snapshot` 暴露当前屏幕和主屏回滚数据。`revision` 供呈现方判断是否需要刷新。新进程运行前调用 `reset` 清理屏幕和解析状态，同时保留当前尺寸；不能让上一运行的输入模式泄漏给新进程。

回滚最多 2000 行并受 100000 单元格预算限制，控制序列长度也有界。主屏 resize 进行内容重排，备用屏维护自己的尺寸/光标状态。`drainReplies` 返回终端设备响应，由 coordinator 送回进程，不能作为可见业务输出。

应用光标键、bracketed paste、鼠标追踪和 SGR 鼠标模式影响 `TerminalInputEncoder` 的编码。OSC/DCS 中的剪贴板、超链接、文件传输等副作用不启用。新增转义序列时应更新解析、状态重置、快照/输入协同及边界处理；系统剪贴板或外部文件操作不能由原始进程输出直接驱动。
