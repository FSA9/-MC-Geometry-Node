# 文档注册、加载与开图

这里是图文档的应用生命周期层，不依赖 UI/ModernUI。它知道文件、图、编辑历史和任务契约；具体窗口及任务控件通过外部适配接入。

| 文件 | 职责 |
| --- | --- |
| [GraphSession.java](GraphSession.java) | 一个打开文档的运行时身份、`GraphFileReference`、`EditorContext`、dirty 状态及历史订阅。`sessionId` 标识本次会话，`revision()` 来自编辑历史，不是文件内容 revision。`close()` 幂等释放历史资源和文件 claim。 |
| [GraphDocumentManager.java](GraphDocumentManager.java) | 所有打开文档成员和顺序的唯一注册表；提供按文件身份查找、注册、关闭、移动顺序、保存以及变更/保存通知。`getSessions()` 返回不可修改的成员快照。 |
| [GraphDocumentLoader.java](GraphDocumentLoader.java) | 验证文件类型，报告读取/解析进度，通过 `GraphDocumentStore` 读快照并取得 claim，最后构造 session。构造时必须提供 `FrameBoundsPolicy`。 |
| [GraphDocumentOpenController.java](GraphDocumentOpenController.java) | 远程 ID 到本地草稿、下载、加载、激活的异步编排。`Backend` 提供连接身份、后台任务、下载及停用接口；文档所有权仍由这里处理。 |

## 单一文档所有权与 UI 状态

manager 的成员操作、保存以及 session 的图编辑应在编辑所有者线程串行执行；后台加载完成后由装配层切回所有者线程再注册。`openSession` 按 `GraphFileReference` 的对象身份复用已注册文档；调用者仍负责关闭没有被采用的重复加载结果。不能直接修改成员快照，也不能绕开 manager 为同一文件另建打开文档集合。

标签激活、相机、缩放和选择等视图状态由 `ui.document.DocumentManager` 与 `GraphSessionViewState` 持有。UI 可以为已有 session 保存视图状态，但文档存活、dirty、历史和文件路径以这里为准。视图状态键的迁移属于 UI 兼容性，不应写进图文件。

## 加载、取消与连接代次

loader 在读取前、解析前和取得 claim 前检查 `AssetTaskContext` 的取消状态。读快照返回内容及文件 revision；解析后 CAS claim 失败会重新读取，最多尝试三次。创建 session 失败时会释放已取得的 claim。已被其他文档持有的文件不能再被 claim。

open controller 优先激活已打开文档，避免从磁盘覆盖未保存编辑。下载采用 `FAIL_IF_EXISTS`；下载竞争后会重新检查本地文件。每个异步续步检查请求代次、原连接对象身份和目标可用性。新请求取代旧请求或 controller 关闭后，旧结果不能激活文档；已加载但未注册的 session 必须关闭。已经开始的下载可能留下本地文件，失效回调不代表撤销已落盘结果。

`Outcome` 区分已打开、无编辑器、ID 错误、连接变化、加载失败和下载失败。被新请求取代的操作不会再发送普通完成通知。宿主销毁时关闭 controller，由 `Backend.deactivate()` 撤销该宿主的任务入口。

## 保存与关闭语义

`saveSession` 先检查历史可保存，序列化并完成原子写入，再标记保存点、通知监听者。返回 `false` 表示持久化未完成；写盘后的普通监听器 `RuntimeException` 会记录日志，不应让成功保存变成失败。保存点转换异常和监听器致命 `Error` 继续传播，不能据此假设磁盘未写入。

异步保存后关闭应使用 `closeSessionIfSaved(session, savedRevision)`：它再次确认成员关系、历史空闲、无 dirty 且 revision 完全一致，防止保存等待期间的新编辑被关闭。文件删除后的关闭/引用刷新也经过 manager。

扩展新开图来源时实现或复用 `Backend` 并在 `integration.graph.GraphDocuments` 装配；不要让 controller 查询全局窗口服务，也不要在资产 Area 内创建另一套开图权威。
