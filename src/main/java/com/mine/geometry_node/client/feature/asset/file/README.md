# 本地资产文件操作与结构事务

本目录处理通用文件系统变更，不解析图文件内容，不持有图会话。调用方提供任务上下文，并根据返回结果刷新收藏、目录、文档引用和 UI。

| 文件 | 职责 |
| --- | --- |
| [LocalAssetService.java](LocalAssetService.java) | 删除、复制或剪切粘贴、移动、新建、重命名，以及收集上传源；返回成功项、移动映射和失败路径。新建文件可传入初始文本，默认内容由外层决定。 |
| [AssetFileOperations.java](AssetFileOperations.java) | 提供替换移动、新目标移动和同级临时路径；优先尝试原子移动，文件系统不支持时回退。 |
| [AssetFileTransactionManager.java](AssetFileTransactionManager.java) | 使用公平读写锁串行化结构变更，并通过 `MutationParticipant` 协调外部状态。 |

## 任务与提交阶段

服务在准备、扫描和复制等可取消阶段检查 `AssetTaskContext`，通过 `progress()` 报告进度；实际结构提交前调用 `enterCommitPhase()`。上下文的实现负责明确提交后取消的处理方式，不能因视图消失而跳过已经发生的文件变更的收尾。

批量返回值可能同时包含成功项与准备失败项，调用方应使用实际成功列表更新状态。上传源收集只生成本地路径与远端目标映射，发送由 [transfer](../transfer/README.md) 完成。

## 事务参与者

`mutate(plan, fileMutation)` 在结构写锁内依次执行：参与者 `prepare()`、文件变更 `commit()`、参与者 `commit()`。出现 I/O 或运行时异常时，逆序回滚已准备的参与者；若已尝试文件提交，再回滚文件变更，并将回滚异常附加到原异常。`MutationPlan` 统一规范化移动与删除路径。

`PreparedMutation.rollback()` 与 `FileMutation.rollback()` 必须能应对部分提交；文件回滚应保持幂等。删除使用同级临时位置暂存后再清理，为事务失败时恢复源文件留出空间。这是协作式回滚协议，不是跨进程或断电恢复事务。

`mutateStructure()` 仅提供结构写锁，不执行参与者准备与回滚；`readStructure()`、`readStructureIO()` 为需要一致结构的读取提供读锁。新增操作必须按是否影响既有资产身份选择合适入口。

## 扩展边界

需要追踪文件移动或删除的文档服务通过 `registerParticipant()` 注册窄参与者；参与者通常随应用服务存在，目前没有注销接口。通用文件层不得反向调用 UI 或图文档实现。操作完成后的通知、弹窗、开图、收藏路径刷新仍由上层装配处理。
