# 模型定义、激活和实例所有权

外部命令与世界适配使用 `ClientModelService.INSTANCE`，而不是自行建 repository 或 GPU asset。服务区分可恢复的小定义、待完成激活和实际活动实例。

| 文件组 | 保留什么 |
| --- | --- |
| `ModelDefinition` | 会话来源、场景、变换、可见性、材质覆盖、小型场景边界；不保留完整 mesh、解码图片或资产 lease。 |
| `ModelActivation` | 某个 owner incarnation 的激活 ticket；失效的旧 ticket 不能重新成为当前请求。 |
| `ModelGeometryStatistics`、`ModelResidencyBounds` | worker 一次计算的源几何计数及避免世界坐标 float 损失的绝对边界。 |
| `ClientModelService` | 定义、pending、repository lease、上传队列、活动实例、取消和失败状态。 |
| `ClientModelCollisions` | 客户端预测及集成服务端的独立碰撞构建和发布。 |
| `ModelServerResidency`、`ModelPublicationQueue` | 服务端 owner 线程的驻留状态，以及每个世界/id 的最后一次期望发布/撤销次序。 |

## 从导入到绘制

服务变更 API 检查渲染线程并核对当前世界。导入/解码/CPU 准备交给有界 `GeometryNode-ModelImport` worker；完成后回到客户端线程，重新检查 epoch、请求与激活 ticket、世界及驻留状态，才排队 GPU 上传。`processRenderUploads()` 按每帧字节/时间预算推进，不能在 IO 完成线程直接创建 GL 资源。

repository 由公共模型 runtime 提供，服务持有每个活动/待完成使用者的 lease；实例对资产的使用通过明确的关闭回调归还。`LoadedAsset` 的 CPU preparation 与渲染资源按自身所有权统一关闭。`renderSnapshots()` 提供帧输入快照，renderer 借用它，不接管 lease。

离开驻留范围会挂起实际实例和资源，保留小定义以供恢复；恢复重新获取来源，不把休眠定义变成完整 CPU 模板缓存。来源身份与 diagnostic path 分开，本地绝对路径只用于诊断，不作为跨服务器资产身份。

## 碰撞与异步撤销

`ClientModelCollisions` 使用独立、有界的碰撞生成 worker。客户端预测和 `ServerLevel` 的发布分别受各自 owner 线程与驻留规则约束；服务端对象退出或 level unload 后旧构建不能再次发布。`ModelPublicationQueue` 把 remove/rollback 也作为顺序中的发布意图，避免慢任务覆盖新状态。

`remove`、部分 lifetime clear、完整 clear、resource invalidation 和 shutdown 含义不同：移除使对应 ticket 失效并关闭其 lease；部分清理不取消无关实例；重载可保留定义、变换和导入数据而重建 GPU；完整 clear 丢弃世界状态；shutdown 再关闭 repository 与两类 worker。新增异步步骤必须在完成处重新检查有效性，并在取消、失败和被新请求覆盖时释放自己持有的 lease。
