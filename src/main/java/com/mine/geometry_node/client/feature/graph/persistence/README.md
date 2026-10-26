# 图格式、元数据与本地草稿

这里处理图文件表示及草稿访问；打开文档的生命周期在 `document`，底层文件并发与所有权在 [file](file/README.md)。依赖 `core` 的图类型、节点模型、quest 与标签规范，以及 `feature.asset` 的类型/路径策略，不依赖编辑器界面。

| 文件 | 职责 |
| --- | --- |
| [GraphJsonIO.java](GraphJsonIO.java) | 读写完整图 JSON，包括 `graph_kind`、`tags`、`comment`、`quest`、`version`、`nodes` 和 `frames`。读取时恢复节点树 ID、父组关系、文档默认值、输入连接状态及 reroute 类型。 |
| [GraphTagIO.java](GraphTagIO.java) | 读取图根对象、标签和轻量元数据；局部修改标签或元数据，保留未涉及的图内容。标签使用 `TagPolicy.GRAPH_LEGACY` 规范化。文件修改通过 store 的未 claim 更新入口执行。 |
| [LocalDraftManager.java](LocalDraftManager.java) | 依据资产草稿根目录和 `GraphPathMapper` 将图 ID 映射成路径，枚举草稿 ID、读取草稿、保存关闭状态的草稿。解析路径校验不能越出草稿根目录。 |
| [file/GraphDocumentStore.java](file/GraphDocumentStore.java) | 内容快照、claim 校验及原子写入的统一入口。 |
| [file/GraphFileReference.java](file/GraphFileReference.java) / [GraphFileRegistry.java](file/GraphFileRegistry.java) | 维护移动后仍稳定的文件身份，并参与资产移动/删除事务。 |

## 文档写入与文件写入

打开文档的完整保存由 `GraphDocumentManager.saveSession` 从当前内存图调用 store 的 claimed 写入口。`LocalDraftManager.saveDraft` 使用 `writeUnclaimedStringAtomic`，会拒绝覆盖已经打开的文档。资产元数据操作同样不能绕过 claim 去修改磁盘，再把旧磁盘状态灌回活动文档。

标签修改使用 `GraphTagIO.updateTags` 的读改写事务；规范化结果未改变时不重写文件，也不递增文件 revision。完整图保存与历史快照承担不同职责：JSON 用于持久化，编辑历史使用 `GraphHistorySnapshot` 保留运行时图状态。

新增持久化字段时同步检查完整读写、轻量元数据路径和历史快照；新增图类型使用 `core.engine.graph.GraphTypeRegistry` 的稳定类型 ID。目录/包迁移不得改变现有 JSON 键、图 ID 到相对路径的映射或资产类型识别规则。
