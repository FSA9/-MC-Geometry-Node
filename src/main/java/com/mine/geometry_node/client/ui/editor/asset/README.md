# 资产浏览 Area

本目录只保留工作区窗口与扩展绑定，公共浏览器、picker、对话框和缩略图都在 [components/integration/asset](../../components/integration/asset/README.md)。

| 文件/子目录 | 职责 |
| --- | --- |
| `AssetBrowserWindow` | `ASSET_BROWSER` Area：组装导航、文件浏览、侧栏、快捷键和会话状态；处理远端能力可用性以及激活/隐藏。 |
| `AssetBrowserAreaBindings` | 向 Area 注入文档动作、属性绑定、传输控制和文档保存订阅；内部 `Properties/Transfers` 协议隔离具体集成实现。 |
| `sidebar/transfer/AssetTransferPanel` | 显示资产传输任务快照和任务操作，作为注册 sidebar 面板。 |

`BuiltinAreaEditors` 注入 `client/integration/asset/WorkspaceAssetBrowserBindings`；它把 `feature/asset`、图文档与传输能力连接起来。Area 不重新实现资产仓库或图文件事务。

窗口在 UI 线程修改视图。`onShow` 激活浏览、传输展示并安装保存监听；`onHide` 取消远端请求、递增 generation、解除保存监听并停用浏览面板。detach 时提交属性面板的待处理编辑并释放相应展示资源，迟到回调通过 active/generation 检查失效。已经提交的文件操作和传输任务由业务服务管理；窗口只持有展示订阅与控制接口。

新增 Area 工具栏/布局放此包，新增可复用资产 View 放公共 asset UI，文档/图联动放 `client/integration/asset`，仓库/路径/文件事务放 `client/feature/asset`。旧 `editor/asset/action`、`browser`、`dialog`、`drag`、`image`、`model`、`navigation`、`preview`、`properties`、`repository`、`schematic`、`service`、`task` 若留下空目录，均为迁移残留，不作为新代码位置。
