# 图编辑、作用域与事务历史

这一层把用户或自动化发起的编辑转换为图状态变化。它依赖 `core.node` 图模型和 `client.history`，不依赖 View、ModernUI 或具体工作区。调用方负责在图的所有者线程执行。

## 核心文件

| 文件 | 职责 |
| --- | --- |
| [EditorContext.java](EditorContext.java) | 持有根图、当前节点组作用域、图控制器、命令管理器及图事件；历史恢复后修复失效的组作用域，批量发布编辑事件。 |
| [GraphController.java](GraphController.java) | 节点、连线、端口、frame、节点组及 reroute 的底层结构操作，维护相关联的图数据。应用编辑应由命令管理器调用这些操作。 |
| [CommandManager.java](CommandManager.java) | 对接 `HistorySession`，统一执行、undo/redo、快照回滚、dirty、保存点、revision 与 `changeId`。最多保留 50 个历史步骤。 |
| [ICommand.java](ICommand.java) | 编辑命令入口；执行描述具体修改。历史回放恢复图快照，不依靠重新执行命令或命令持有的旧对象。 |
| [IdentifiedCommand.java](IdentifiedCommand.java) | 为命令附加外部变更身份，供一次补丁事务等调用方关联 `changeId`。 |
| [FrameBoundsPolicy.java](FrameBoundsPolicy.java) | 注入已提交 frame 包围盒的测量能力，使分组、布局和补丁规划使用实际布局规则。 |
| [history/GraphHistorySnapshot.java](history/GraphHistorySnapshot.java) | 脱离活动对象的完整内存快照，保存嵌套组、输入值、端口配置及顺序、frame 和元数据；恢复时创建独立对象，不通过 JSON 往返归一化。 |

## commands 目录

各叶子目录按修改对象归类。命令只实现编辑行为；UI 事件、确认框、工作区选择和网络请求由调用方负责。

| 子目录 | 文件与具体行为 |
| --- | --- |
| `connection` | `CmdConnect` 建连、`CmdDisconnect` 断连、`CmdInsertRerouteOnConnection` 在线上插入 reroute。 |
| `document` | `CmdPasteElements` 粘贴图元素，`CmdReplaceGraphState` 一次替换规划后的图状态，`CmdSetGraphMetadata` 修改图元数据。 |
| `frame` | `CmdAddFrame` 新建，`CmdGroupIntoFrame` 收纳元素，`CmdRemoveFrames` 删除，`CmdSetFrameProperty` 修改 frame 属性。 |
| `group` | `CmdGroupIntoNodeGroup` 创建节点组，`CmdDissolveNodeGroup` 解组，`CmdAddGroupVirtualPort` / `CmdRemoveGroupVirtualPort` 维护组边界端口，`CmdSetGroupNodeProperty` 修改组节点属性。 |
| `layout` | `CmdChangeParent` 改变父 frame，`CmdMoveElements` 批量位移，`CmdSetElementPositions` 设置明确位置。 |
| `node` | `CmdAddNode` 新建节点，`CmdRemoveNodes` 删除节点及关联结构，`CmdChangeInputValue` 修改输入值。 |
| `port` | `CmdAddBranch` / `CmdRemoveBranch` 维护动态分支，`CmdRenamePort` 修改端口名称。 |

## 事务与扩展约束

一次执行先预约历史并捕获修改前快照，再执行命令、比较修改后状态并提交。无实际状态变化不推进历史。执行失败恢复图及作用域；回滚也失败时使历史失效，不能继续假装状态可靠。操作和通知期间阻止重入修改，调用方不能在历史监听器内再次发起编辑。

`prepareEdit` 执行脱离活动图的准备工作，期间阻止嵌套命令、保存和回放，不创建历史步骤或发出通知；它本身不复制图或回滚任意直接写入。`EditorContext.fork` 使用调用方提供的独立图数据创建新的历史和监听器，并保留同一个 `FrameBoundsPolicy`，调用方负责先准备脱离活动对象的图。装配层必须注入真实布局测量，不能为自动化单独使用估算几何规则，否则规划与提交的 frame 结果会分歧。

`clearHistory` 清理历史步骤但保留当前保存点和 revision 语义；确有外部图变化时使用 `recordExternalMutation` 建立新的 dirty 基线；文件写入完成后才调用 `markSaved`。新增命令应进入上述事务入口，确保关联结构、undo/redo、失败回滚和组作用域仍完整。涉及 UI 的交互状态放在 UI 层，跨文档目标解析放在 integration 层。
