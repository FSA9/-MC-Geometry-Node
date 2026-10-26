# 图文档与编辑应用核心

本目录负责客户端图文档的打开、所有权、编辑历史、保存和共享查询。图的数据结构、节点定义与编译仍在 `core`；标签页、相机、选择框和控件布局在 `client.ui`。这里不依赖 ModernUI 或具体编辑器窗口。

## 目录与关键文件

| 位置 | 职责与调用方 |
| --- | --- |
| [document](document/README.md) | `GraphSession` 持有文档，`GraphDocumentManager` 统一注册、保存与关闭；`GraphDocumentLoader` 加载本地文件，`GraphDocumentOpenController` 编排远程开图。 |
| [edit](edit/README.md) | `EditorContext`、`GraphController` 和 `CommandManager` 实现图编辑、作用域与历史事务；`commands` 按编辑对象分类。 |
| [persistence](persistence/README.md) | `GraphJsonIO`、`GraphTagIO`、`LocalDraftManager` 处理格式和草稿；`file` 处理稳定文件身份、原子写入、claim 与文件结构事务。 |
| [metadata/GraphTagMetadata.java](metadata/GraphTagMetadata.java) | 向通用 metadata 体系提供标签能力：打开文档使用 `SessionTarget`，关闭文件使用 `FileTarget`。前者受编辑所有者线程约束并进入命令历史，后者只允许修改未被文档 claim 的文件。 |
| [query/NodeSearchService.java](query/NodeSearchService.java) | 对调用方传入的节点定义集合生成共享搜索结果与分页，供节点搜索界面和 AI 查询适配使用。 |
| [query/NodeCommentTextBuilder.java](query/NodeCommentTextBuilder.java) | 生成节点说明文本，使搜索和查询复用同一份节点说明逻辑。 |

`metadata` 与 `query` 是小型能力目录，其入口已在上表说明。

## 依赖与装配

依赖方向为 `UI / integration → feature.graph → core`。本特性还使用 `client.history` 的通用历史、`feature.asset` 的任务上下文、类型和文件事务能力，以及 `core.metadata` 的元数据契约。

[integration.graph.GraphDocuments](../../integration/graph/GraphDocuments.java) 装配实际任务界面、下载后端和 `FrameBoundsPolicy`；[UI DocumentManager](../../ui/document/DocumentManager.java) 只负责标签激活及视图状态门面。它们消费同一个 `GraphDocumentManager`，不能维护第二份文档成员集合。AI 的工作区绑定和补丁提交适配位于 `integration.ai.graph`，AI 补丁契约位于 `feature.ai.graph`。

## 生命周期与扩展

文件先由 loader 读取内容和文件 revision，解析后按该 revision 取得 claim，再创建 session。装配层将 session 交给 manager 注册，UI 随后激活标签。编辑必须经过 `CommandManager`，保存从 session 当前图状态序列化，关闭释放历史订阅和文件 claim。文件移动时保留文件引用身份，删除通过文件事务及文档关闭流程协同处理。

新增图编辑行为放入 `edit/commands`，新增可复用查询放入 `query`；涉及 UI、下载进度展示或具体工作区定位的代码放到装配层。新增标签能力时保留 `geometry_node:graph_asset_session`、`geometry_node:graph_asset_file` 这两个 metadata 存储 ID。Java 包名不决定图类型 ID、JSON 键或草稿 ID，重构时应保持它们稳定。
