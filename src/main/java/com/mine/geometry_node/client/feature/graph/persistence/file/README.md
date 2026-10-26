# 图文件身份、claim 与原子事务

本层协调图内容访问和资产文件结构操作。它依赖 `feature.asset.file.AssetFileTransactionManager` 的结构锁与事务参与者契约；上层通过文件引用访问，不将路径字符串当作文档身份。

| 文件 | 职责 |
| --- | --- |
| [GraphFileReference.java](GraphFileReference.java) | 一个文件的稳定对象身份；路径可改变，状态为 `ACTIVE` / `MUTATING` / `DELETED`，同时记录内容 revision 与是否被文档 claim。被删除或正在移动的引用不能作为活动路径使用。 |
| [GraphFileRegistry.java](GraphFileRegistry.java) | 将规范化绝对路径映射到引用，作为资产结构事务参与者准备受影响引用、提交移动/删除，失败时恢复路径、状态和 claim。 |
| [GraphDocumentStore.java](GraphDocumentStore.java) | 使用结构锁和按文件分配的锁协调内容读写，防止移动/删除与保存相互穿透；在锁内检查路径状态、claim 和 revision，统一临时文件替换及失败清理。 |

## 两类所有权与两个 revision

`readSnapshot` 在同一内容访问边界返回文本和文件 revision。loader 解析后用 `claimDocument(reference, expectedRevision)` 作 CAS：revision 不一致则重读，已有 claim 则拒绝建立第二个文档。claim 成功后由 session 持有，关闭时释放。这里的文件 revision 追踪经过 store 的内容变更，不是外部文件监视器，也不同于 `CommandManager` 的编辑历史 revision。

`writeStringAtomic` 需要已有文档 claim；`writeUnclaimedStringAtomic`、`readUnclaimedString` 和局部更新入口要求没有打开文档。`updateStringAtomicIfChanged` 在锁内读取、转换和比较文本；相同内容不写盘、不增加 revision。转换函数中不能重入同一文件修改。

原子写使用同目录临时文件及替换策略，避免直接截断目标文件。文件移动和删除走资产结构事务，引用在准备期间进入 `MUTATING`，提交后更新路径或变为 `DELETED`，回滚恢复之前状态。上层随后通过文档 manager 刷新引用或关闭受影响 session。

新增保存、标签编辑、草稿导入等路径应选用对应所有权入口，不能直接 `Files.write*` 绕过保护。新增文件结构操作应接入同一事务参与者机制；不要在应用层自行更换引用对象来表示移动后的同一文档。
