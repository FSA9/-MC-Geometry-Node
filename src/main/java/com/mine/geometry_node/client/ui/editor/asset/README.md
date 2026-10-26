# 资产浏览 Area

本目录只保留工作区窗口与扩展绑定，公共浏览器、picker、对话框和缩略图都在 [components/integration/asset](../../components/integration/asset/README.md)。

| 文件/子目录 | 职责 |
| --- | --- |
| `AssetBrowserWindow` | `ASSET_BROWSER` Area：组装导航、文件浏览、侧栏、快捷键和会话状态；处理远端能力可用性以及激活/隐藏。 |
| `AssetBrowserAreaBindings` | 向 Area 注入文档动作、属性绑定、传输控制和文档保存订阅；内部 `Properties/Transfers` 协议隔离具体集成实现。 |
| `sidebar/transfer/AssetTransferPanel` | 显示资产传输任务快照和任务操作，作为注册 sidebar 面板；保留分区、操作按钮及未变化历史行，快照更新不打断输入。 |

`BuiltinAreaEditors` 注入 `client/integration/asset/WorkspaceAssetBrowserBindings`；它把 `feature/asset`、图文档与传输能力连接起来。Area 不重新实现资产仓库或图文件事务。

窗口在 UI 线程修改视图。`onShow` 激活浏览、传输展示和任务，并安装捕获本次 generation 的保存监听。`onHide` 与临时 detach 取消远端请求、使旧 generation 失效、解除保存监听并停用浏览、侧栏和可取消任务，之后仍可 `onShow`。`onDispose` 是幂等的永久释放：提交属性面板的待处理编辑、释放传输控制器，之后不再接受 `onShow`。已经提交的文件操作和传输任务由业务服务管理；文档引用、缓存维护和临时文件清理独立于 owner 的展示回调，隐藏或释放窗口不撤销这些收尾。

恢复远端位置时先显示本地 fallback；`restoring` 仅保护初始化和自动恢复的同步导航通知。用户从导航栏或浏览器内部主动导航会取消旧 pending 并记录新位置，迟到的能力响应不能覆盖它。能力请求超时或同步发送失败会清除 pending/restoring，并记录当前 fallback；临时隐藏期间尚未完成的恢复意图可在下次显示时继续。

新增 Area 工具栏/布局放此包，新增可复用资产 View 放公共 asset UI，文档/图联动放 `client/integration/asset`，仓库/路径/文件事务放 `client/feature/asset`。旧 `editor/asset/action`、`browser`、`dialog`、`drag`、`image`、`model`、`navigation`、`preview`、`properties`、`repository`、`schematic`、`service`、`task` 若留下空目录，均为迁移残留，不作为新代码位置。
