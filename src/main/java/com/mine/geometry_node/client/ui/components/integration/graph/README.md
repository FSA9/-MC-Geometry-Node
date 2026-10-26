# 共享图属性组件

`properties/GraphPropertiesPanel` 为图编辑器和资产浏览器提供同一属性侧栏。`GraphPropertiesTarget/Snapshot` 定义存储边界与分离快照；组件不依赖任何编辑器 Area。图会话适配位于 `client/integration/graph/GraphSessionPropertiesTarget`，本地资产文件适配位于 `client/integration/asset/AssetFilePropertiesTarget`。

| 文件 | 职责与保留的行为 |
| --- | --- |
| `properties/GraphPropertiesPanel` | 绑定目标、异步加载、元数据提交、标签与任务预览入口；target/generation/request 校验淘汰旧加载。 |
| `properties/GraphPropertiesTarget` | 加载、保存、变更监听及成功通知协议，不持有窗口。 |
| `properties/GraphPropertiesSnapshot` | 规范化元数据并复制标签集合，保留任务定义和条件概览。 |
| `properties/common/GraphTypeSelector` | 图类型菜单，关闭同值选项不提交；菜单回调仅属于本次打开。 |
| `properties/quest/QuestPropertiesSection` | 标题、描述、富文本、条件及目标/奖励装配；刷新保留焦点内文本。 |
| `properties/quest/QuestObjectivesEditor` | 目标数量、计数器、提示与顺序；按 entryId 恢复焦点/选择范围和折叠状态。 |
| `properties/quest/QuestRewardsEditor` | 奖励数量、计数器、物品提示与顺序；保留独立草稿和折叠状态。 |
| `properties/quest/QuestHintSelection` | native picker 结果写回捕获的目标及 entryId，读取最新快照后合并，已删除条目不复活。 |
| `properties/quest/QuestHintSelector` | 显式选择展示类型，禁用/关闭/重开使旧菜单回调失效；同值选择仍通知 owner。 |
| `properties/quest/QuestConditionOverviewView` | 只读显示可见、接受、完成条件。 |
| `properties/quest/QuestEntryUi` | 目标和奖励共享的局部布局与选项控件。 |
| `properties/utils/GraphPropertiesUi` | 属性控件的局部工厂和样式，复用公共按钮、输入与菜单组件。 |
| `properties/utils/GraphPropertyNumbers` | 有限非负数解析、失败回退与数值格式化；超出 long 范围使用 double 表示，加载后不截断回写。 |

UI 线程拥有绑定、表单与焦点。保存完成及成功通知使用独立 UI executor，不等待 View 重新挂载；旧目标完成写入仍收到成功通知，当前面板更新受 target/generation 校验。native picker 的业务回写同样不绑定旧控件挂载，保存成功后回到 UI executor。

任务预览调用 `client/integration/graph/GraphEditorPreviews`；该入口也供节点提示的对话/商店预览使用。会话编辑仍经过原命令和历史机制；文件 IO、缓存失效及宿主刷新仍由具体 Target 负责。迁移不改变侧栏注册 ID、图类型 ID、任务字段或资源键。
