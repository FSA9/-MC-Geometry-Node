# 文档的 UI 门面

此包仅保留标签激活与视图状态，不拥有第二份文档仓库。

| 文件 | 职责与依赖 |
| --- | --- |
| `DocumentManager` | UI 单例门面：打开/切换/关闭标签、记录最后打开会话及序号、通知标签变更；文档列表、保存、关闭和路径刷新委托 `client/feature/graph/document/GraphDocumentManager`。 |
| `GraphSessionViewState` | 按 `GraphSession` 保存标签名称、视口位置/缩放及节点/Frame 选择的共享默认值；使用弱键，并随已关闭会话清理。具体视口仍有自己的 `ViewportSessionState`。 |

标签激活和共享视图字段在 UI 线程访问；弱引用不提供跨线程同步。窗口监听者必须配对移除标签/保存监听。Area 隐藏或 Viewport 销毁只保存/解除自己的视图状态，不能擅自关闭共享图会话。权威图、编辑上下文、事务与历史归业务会话管理。

原 `ui/document/edit`（包括 `commands` 各分类与 `history`）已经迁往 `client/feature/graph/edit`，历史基础设施位于 `client/history`。磁盘上残留的空目录不是扩展入口。新增图命令、序列化、事务和会话生命周期代码写入 `feature/graph`；只有标签投影或跨视口的 UI 默认值才放这里。
