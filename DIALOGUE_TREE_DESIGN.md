# 对话树节点系统技术方案

## 目标

本文档用于确定 Geometry Node 蓝图体系中“对话树”功能的实现路线。当前阶段只确定流程、节点模型、存储格式、UI 打开方式和与现有 VM 的兼容边界，不包含具体代码实现。

项目当前基础：

- 蓝图图资产使用 `NodeGraph` / `NodeData` 保存为 JSON。
- 运行时由 `RuntimeGraphIndex` 编译图，再由 `GraphProcess.ExecutionThread` 执行。
- 执行流是 push model，数据流是 pull model。
- 节点分为 `EVENT`、`ACTION`、`DATA`、`FLOW_CONTROL`、`LOGIC` 等。
- 实体右键入口已经存在：`EntityInteractEntity` 输出 `trigger_entity` 和 `entity`。
- 网络层已有 C2S/S2C 包注册模式，客户端已有 ModernUI 编辑器和普通 Minecraft `Screen` 示例。

## 核心结论

1. 对话树适合用节点实现。

   对话流程本质是“事件入口 -> 条件判断 -> 展示文本 -> 玩家选择 -> 后续动作”的有向图，和现有蓝图 VM 高度匹配。不要另起一套独立对话执行器，除非未来要做完全脱离蓝图的轻量剧情脚本。

2. 对话节点不应该是 UI 控件节点。

   节点描述语义内容和流程，例如 speaker、text key、choices、condition、style id。客户端 UI 根据 `style_id` 渲染 ModernUI 或 Screen。这样内容、逻辑、表现三者分离，同一棵对话树可以换不同皮肤。

3. 图里保存 text key，不保存大段正文。

   `NodeGraph` 适合保存节点拓扑、端口、坐标、条件连线；对话正文应该独立成文本资源，便于翻译、diff、批量替换和资源包覆盖。

4. 条件判断应复用现有节点体系。

   choice 的 `visible` / `enabled` 应是普通 `BOOLEAN` 输入端口。`GetEntityAttribute`、`GetEntityTags`、`ListHasValue`、`IF`、`Switch` 等都可以参与条件判断，但不是所有返回值都能直接当条件。

5. UI 打开应由节点发起，但事件入口仍由代码分发。

   例如玩家右键实体时，NeoForge 事件分发到 `EntityInteractEntity`。之后是否打开对话、打开哪棵对话树、用什么样式，应该交给蓝图节点决定。

## 当前架构兼容性

### 图格式

继续使用现有图 JSON：

```json
{
  "graph_name": "blacksmith_dialogue",
  "version": "1.0",
  "tags": ["dialogue"],
  "nodes": {},
  "frames": {}
}
```

需要注意：

- `NodeGraph` 已经有 `tags` 字段。
- `GraphJsonIO` 当前手写根 JSON，尚未读写 `tags`。如果需要用 `tags: ["dialogue"]` 做资产筛选，必须补齐序列化和反序列化。
- `RuntimeGraphIndex` 当前不依赖 root `tags`，对话图不能把 `tags` 当作运行时必要条件。

### 节点和端口

现有节点定义方式可以直接承载对话节点：

- `BaseNode.execute(context)` 用于 action / flow 节点。
- `BaseNode.compute(context, portName)` 用于 data 节点。
- `NodeDef.rows()` 定义输入、输出和执行端口。
- `NodeData.inputs` 保存静态输入。
- `NodeData.execOutputs` 保存执行流。
- `NodeData.outputs` 保存数据流。

建议对话节点优先使用扁平端口，例如：

- `text_key`
- `speaker_key`
- `choice_text_key_1`
- `choice_visible_1`
- `choice_enabled_1`
- `choice_1`

短期不建议把 choice 保存成嵌套 JSON 对象，因为 `RuntimeGraphIndex.unwrapJsonElement` 当前只处理 primitive 和 array，不处理 object。

### 动态端口限制

`Switch` 已经展示了动态输出端口的方向，但对话节点第一阶段不建议直接做无限动态 choice。

原因：

- `GraphFlattener` 当前使用 `logic.getDefaultDefinition()` 烘焙默认输入，没有按 `NodeData` 调用 `getDefinition(instanceData)`。
- 动态端口名必须进入 `RuntimeGraphIndex` 的 key dictionary，否则运行时 `findInputSource` / `findFlowTarget` 可能找不到。
- `RuntimeGraphIndex.hasPort` 当前检查 `inputs`、`outputs`、`execution`，没有检查 `exec_outputs`。

MVP 建议固定最大 choice 数，例如 4 或 6。每个 choice 端口都在默认定义里出现。后续再统一修正动态端口编译逻辑，再开放无限 choice。

### 条件判断

不能简单说“任意节点都能作为条件”。准确规则是：

- `IF` 的条件端口是 `BOOLEAN`。
- `TypeConverter` 当前支持 `Boolean`、数字 `> 0`、字符串 `"true"` / `"false"` 转布尔。
- `List`、`Map`、`Entity`、`ItemStack`、`Vec3` 不能直接转布尔。

因此：

- `GetEntityAttribute` 输出 `ANY`。如果属性值本身是 boolean、数字或 `"true"` / `"false"`，可直接接 choice 条件。
- `GetEntityTags` 输出 `LIST`。它应先接 `ListHasValue`，再把 `BOOL` 输出接 choice 条件。
- 当前 `Equal` 的 `getDefaultDefinition()` 返回 `null`，会被注册器跳过；`Contain` 是空类且未注册。对话系统落地前，应补齐通用逻辑节点，而不是在对话节点里内置一套条件语言。

建议补齐的通用逻辑节点：

- `Equal`
- `NotEqual`
- `Contains`
- `And`
- `Or`
- `Not`
- `CompareNumber`
- `IsNull` / `IsNotNull`

这些应放在通用 `logics` 分类，不属于对话专属节点。

## 推荐架构

推荐方案是：

```text
普通蓝图图
  -> 对话专用 ACTION / EVENT / DATA 节点
  -> DialogueSessionManager 管理服务端会话
  -> 网络包打开客户端对话 UI
  -> 玩家选择回包
  -> 服务端恢复等待中的蓝图执行流
```

### 服务端组件

#### DialogueSessionManager

职责：

- 创建对话会话。
- 记录 session id。
- 记录玩家 UUID。
- 记录 speaker/entity UUID。
- 记录 graph id。
- 记录等待中的 VM continuation。
- 记录 choice id 到执行输出端口的映射。
- 处理关闭、超时、玩家下线、实体消失、距离过远、图热更新。

#### DialogueContinuation

建议作为独立概念，而不是把 UI 状态塞进节点。

需要记录：

- `graphId`
- `graphIndexIdentity` 或版本标识。
- 等待节点的 runtime node id。
- 等待节点的原始 string node id。
- 等待线程或可恢复线程句柄。
- 可选输出端口集合，例如 `continue`、`choice_1`、`closed`。
- 会话级临时数据。

如果图热更新导致 index 不一致，MVP 应直接关闭 session，不要强行恢复到新图。

#### DialogueTextManager

职责：

- 根据 text key 和玩家语言解析文本。
- 支持 fallback。
- 支持 world 动态文本资源。
- 支持资源包 / 数据包发布时的 lang 文本。
- 提供缺失 key 诊断。

MVP 可以先只用 `fallback_text` 或 key + fallback，不阻塞 UI 与 VM 闭环。

### 客户端组件

#### PacketOpenDialogue

服务端发送到客户端。

内容建议：

- `session_id`
- `speaker_text`
- `speaker_key`
- `body_text`
- `body_key`
- `choices`
- `style_id`
- `portrait_id`
- `sound_id`
- `flags`

MVP 中服务端应解析最终 literal 文本后下发，避免客户端没有动态 world 文本资源。

#### PacketDialogueChoice

客户端发送到服务端。

内容建议：

- `session_id`
- `choice_id`
- `action`: `select` / `continue` / `close`

服务端必须校验：

- session 是否存在。
- 玩家是否是 session owner。
- choice 是否仍然可选。
- speaker/entity 是否仍然有效。

#### DialogueScreen / DialogueOverlay

这是游戏内对话 UI，不应复用编辑器资产库弹窗。

可选实现：

- 普通 Minecraft `Screen`，类似 `PlayerInventoryPickerScreen` 的方向。
- ModernUI Fragment/Screen，类似 `MuiModApi.openScreen(new MainUI())` 的打开方式，但 UI 类应独立于编辑器。
- 后续可做 HUD overlay，但 MVP 用 Screen 更简单，输入焦点和关闭行为更清晰。

## VM 等待模型

对话展示不能阻塞服务器 tick 线程。节点执行到 `ShowDialoguePage` 时，应返回“外部等待”结果，让 VM 暂停该执行线程。

推荐新增类似概念：

```text
ExecutionResult.DialogueWait(payload, outputPortMap)
```

或更通用：

```text
ExecutionResult.ExternalWait(waitType, payload)
```

执行语义：

1. `ShowDialoguePage` 读取文本 key、speaker、choice 条件。
2. 节点过滤不可见 choice。
3. 节点创建会话 payload。
4. VM 把当前执行线程交给 `DialogueSessionManager`。
5. 服务端给客户端发送 `PacketOpenDialogue`。
6. 玩家选择或关闭 UI。
7. 客户端回包。
8. `DialogueSessionManager` 校验后恢复线程。
9. VM 从对应输出端口继续执行。

不推荐的替代方案：

- 在节点里阻塞等待客户端回包。
- 让客户端直接改世界状态。
- 每个 choice 都重新派发一个全新事件来代替 continuation。这个方案可以做极简版，但会丢失当前执行栈、局部变量和同步流程语义，不适合作为专业对话树基础。

MVP 可不持久化外部等待。玩家下线、服务器关闭、图热更新时关闭 session 即可。后续如果需要保存对话中途状态，再扩展 `GraphProcessSerializer` 保存外部等待线程。

## 节点设计

### MVP 节点

#### DialogueEntry

类型：`EVENT`

用途：独立对话图入口。

静态输入：

- `entry_id`，默认 `root`

输出：

- `flow_out`
- `player`
- `speaker`
- `entry_id`

说明：

`OpenDialogueTree(graph_id, entry_id)` 不能只保存一个概念字段。当前 `GraphEngine` 主要按节点类型启动事件，因此需要明确入口机制。

推荐两种实现路线：

- 专业路线：新增 `DialogueEntry`，并给 `GraphEngine` 增加按 `entry_id` 启动对话入口的内部能力。
- 兼容路线：复用 `ReceiveBlueprint.frequency`，例如 `dialogue/root`。实现简单，但语义不如 `DialogueEntry` 清晰。

推荐使用 `DialogueEntry`，因为后续编辑器可以识别对话图入口、检查缺失入口、做跳转导航。

#### OpenDialogueTree

类型：`ACTION`

用途：从普通蓝图打开一张对话图。

输入：

- `flow_in`
- `player`
- `speaker`
- `graph_id`
- `entry_id`
- `style_id`

输出：

- `flow_out`，成功启动。
- `failed`，找不到图、入口、玩家或 speaker。

说明：

典型用法：

```text
EntityInteractEntity
  -> GetEntityAttribute(entity, "dialogue_graph")
  -> OpenDialogueTree(player = trigger_entity, speaker = entity, graph_id = ...)
```

如果对话内容就在当前图里，MVP 可以不使用 `OpenDialogueTree`，直接从 `EntityInteractEntity` 连到 `ShowDialoguePage`。

#### ShowDialoguePage

类型：`ACTION`

用途：展示一页对话，并等待玩家继续、选择或关闭。

输入：

- `flow_in`
- `player`
- `speaker`
- `speaker_key`
- `speaker_fallback`
- `text_key`
- `fallback_text`
- `style_id`

固定 choice 输入，MVP 建议 4 或 6 组：

- `choice_text_key_1`
- `choice_fallback_1`
- `choice_visible_1`
- `choice_enabled_1`
- `choice_text_key_2`
- `choice_fallback_2`
- `choice_visible_2`
- `choice_enabled_2`

输出：

- `continue`，无 choice 时点击继续。
- `choice_1`
- `choice_2`
- `choice_3`
- `choice_4`
- `closed`
- `failed`

执行语义：

- 所有 `choice_visible_i` 默认 true。
- 所有 `choice_enabled_i` 默认 true。
- 不可见 choice 不发送到客户端。
- 不可用 choice 可以显示为禁用，也可以由 style 决定是否隐藏。
- 玩家选择后，从对应 `choice_i` 输出端口恢复执行。

#### DialogueSequence

类型：`ACTION`

用途：展示一串没有中途分支的连续文本，减少节点数量。

输入：

- `flow_in`
- `player`
- `speaker`
- `text_keys`
- `fallback_texts`
- `style_id`

输出：

- `flow_out`
- `closed`
- `failed`

说明：

支持两种输入形态：

- `LIST`：`["line_001", "line_002", "line_003"]`
- 多行 `STRING`：每行一个 text key

MVP 建议客户端每页点击都回包，服务端保持同一个 session，直到序列结束再走 `flow_out`。这样关闭、距离检查、超时和热更新处理更一致。

不建议用特殊字符在原文里切段，例如 `---`。翻译人员很容易破坏结构。如果需要 txt 导入，可以在导入阶段转换成 list。

#### CloseDialogue

类型：`ACTION`

用途：主动关闭某个玩家当前对话 UI。

输入：

- `flow_in`
- `player`

输出：

- `flow_out`

#### ResolveDialogueText

类型：`DATA`

用途：把 key 解析成当前语言文本，或返回 fallback。

输入：

- `key`
- `fallback`
- `args`

输出：

- `text`

说明：

短期输出 `STRING`，兼容现有端口体系。长期可考虑新增 `COMPONENT` 端口类型，用于颜色、hover、click event、富文本参数。

### 后续节点

#### DialogueStyle

类型：`DATA`

输出：

- `style_id`

用途：让样式也能由图动态选择，例如根据 NPC 类型、任务阶段、玩家设置切换样式。

#### SetDialogueVariable / GetDialogueVariable

类型：`ACTION` / `DATA`

用途：仅对当前对话 session 有效的临时变量。

如果变量需要跨 session 持久化，应继续使用实体属性、全局属性或未来任务系统，不要塞进对话 session。

#### BuildDialogueChoiceList

类型：`DATA`

用途：高级动态 choice，例如根据任务系统或商店系统生成选项。

前提：

- 补齐端口对复杂对象 / dict 的运行时支持。
- 或定义专门的 `DialogueChoice` DTO 序列化协议。

MVP 不需要。

#### OnDialogueClosed

类型：`EVENT`

用途：统一处理玩家关闭 UI 后的清理。

MVP 中 `ShowDialoguePage.closed` 输出已经足够，暂不需要独立事件。

## 文本资源格式

### 原则

- 图里保存 key。
- 节点可保存 fallback，方便缺失文本时预览和调试。
- 正式文本放外部资源。
- 服务端动态文本资源不要放进 `geometry_nodes`，因为 `DynamicGraphManager` 会递归加载该目录下所有 `.json` 并尝试编译为图。

### 推荐运行时格式：world JSON

目录建议：

```text
<world>/geometry_dialogue_text/zh_cn/quest/blacksmith.json
<world>/geometry_dialogue_text/en_us/quest/blacksmith.json
```

示例：

```json
{
  "version": 1,
  "locale": "zh_cn",
  "entries": {
    "speaker.quest.blacksmith": {
      "text": "铁匠"
    },
    "dialogue.quest.blacksmith.intro.001": {
      "text": "欢迎来到铁匠铺。",
      "comment": "第一次对话的开场白"
    },
    "dialogue.quest.blacksmith.intro.choice.trade": {
      "text": "我想看看你的货物。"
    }
  }
}
```

选择 JSON 而不是 properties 的原因：

- 更适合多行文本。
- 更适合 comment、speaker、voice、portrait 等扩展字段。
- 转义规则比 properties 更清晰。
- 后续可以增加 schema 版本。

如果短期实现要更简单，也可以支持 properties，但必须明确 UTF-8、换行转义和占位符规则。

### 资源包 lang 模式

适合正式发布内容：

```text
assets/<namespace>/lang/zh_cn.json
assets/<namespace>/lang/en_us.json
```

示例：

```json
{
  "speaker.quest.blacksmith": "铁匠",
  "dialogue.quest.blacksmith.intro.001": "欢迎来到铁匠铺。",
  "dialogue.quest.blacksmith.intro.choice.trade": "我想看看你的货物。"
}
```

限制：

- 动态上传到服务器世界目录的图，不一定能保证客户端已有对应 lang 文件。
- 因此动态对话 MVP 应由服务端解析最终文本并下发客户端。

### 翻译导入导出格式：TSV

给翻译人员的编辑格式建议用 TSV，而不是直接手写运行时 JSON。

```tsv
key	zh_cn	en_us	comment
speaker.quest.blacksmith	铁匠	Blacksmith	NPC display name
dialogue.quest.blacksmith.intro.001	欢迎来到铁匠铺。	Welcome to the forge.	First greeting
dialogue.quest.blacksmith.intro.choice.trade	我想看看你的货物。	Show me your goods.	Choice button
```

TSV 是编辑器导入导出格式；运行时仍转换为 JSON 或资源包 lang。

如果要支持用户所说的“本地 txt”，建议只作为导入导出格式，不作为运行时唯一格式。例如：

```text
[dialogue.quest.blacksmith.intro.001]
欢迎来到铁匠铺。

[dialogue.quest.blacksmith.intro.choice.trade]
我想看看你的货物。
```

这种格式适合人工写，但不适合长期承载 comment、多语言、富文本、语音和头像等扩展信息。

### key 命名规范

建议：

```text
speaker.<module>.<character>
dialogue.<module>.<character>.<scene>.<line_id>
dialogue.<module>.<character>.<scene>.choice.<choice_id>
```

示例：

```text
speaker.quest.blacksmith
dialogue.quest.blacksmith.intro.001
dialogue.quest.blacksmith.intro.choice.trade
dialogue.quest.blacksmith.intro.choice.leave
```

## UI 与样式

### 内容和表现分离

对话节点输出语义数据：

- speaker
- text
- choices
- portrait id
- voice id
- style id

客户端根据 `style_id` 选择布局：

- RPG 底部文本框。
- 左右头像对话框。
- 中央选项菜单。
- 旁白模式。
- 任务交付模式。

这意味着每个 `ShowDialoguePage` 不是一个具体 UI 控件，而是一个“请求展示对话页并等待结果”的 action 节点。

### 样式资源

MVP 可以写死几个 style：

- `geometry_node:rpg_default`
- `geometry_node:narration`
- `geometry_node:choice_center`

后续资源化：

```text
data/<namespace>/dialogue_styles/<style>.json
```

示例字段：

```json
{
  "layout": "bottom_box",
  "speaker_visible": true,
  "portrait": "left",
  "choice_mode": "vertical",
  "typewriter": true,
  "history_enabled": true
}
```

样式资源不应影响图执行结果，只影响客户端展示。

## 一段话一个节点，还是多段话一个节点

推荐两者都支持。

### 一段话一个 ShowDialoguePage

适合：

- 该段前后有条件分支。
- 该段会触发动作，例如给物品、改属性、播放音效。
- 该段需要不同 speaker、portrait 或 style。
- 需要精确调试流程。

缺点是节点数量多。

### 多段话一个 DialogueSequence

适合：

- 连续旁白。
- NPC 连续说几句，中间没有选择。
- 希望降低节点数量。

限制：

- 不适合中间插入动作。
- 不适合中间有条件变化。
- 不适合每段 speaker/style 不同的场景，除非后续扩展成结构化 sequence。

推荐编辑器提供“拆分为多个 ShowDialoguePage”和“合并为 DialogueSequence”的工具，兼顾专业流程控制和制作效率。

## 典型流程

### 实体右键打开绑定对话

实体上保存属性：

```text
dialogue_graph = quest/blacksmith.json
dialogue_entry = root
```

蓝图：

```text
EntityInteractEntity
  trigger_entity -> OpenDialogueTree.player
  entity -> OpenDialogueTree.speaker

GetEntityAttribute(entity, "dialogue_graph")
  -> OpenDialogueTree.graph_id

GetEntityAttribute(entity, "dialogue_entry")
  -> OpenDialogueTree.entry_id
```

### 对话图内部

```text
DialogueEntry(entry_id = "root")
  -> ShowDialoguePage(text_key = dialogue.quest.blacksmith.intro.001)

choice_1 -> TriggerBlueprint("open_trade")
choice_2 -> ShowDialoguePage(text_key = dialogue.quest.blacksmith.quest.001)
choice_3 -> CloseDialogue
closed   -> CloseDialogue
```

### choice 条件

玩家有标签才显示交易选项：

```text
GetEntityTags(player)
  -> ListHasValue("can_trade")
  -> ShowDialoguePage.choice_visible_1
```

任务阶段等于某值才启用选项：

```text
GetEntityAttribute(player, "quest.blacksmith.stage")
  -> Equal("started")
  -> ShowDialoguePage.choice_enabled_2
```

注意：当前 `Equal` 节点未完成注册，正式做对话前应先补齐。

## 分阶段实现计划

### 阶段 0：前置修正

目标：让对话节点依赖的通用能力可靠。

建议完成：

- 修复 `Equal` 节点定义。
- 实现 `Contains` / `And` / `Or` / `Not` / `CompareNumber`。
- 补齐 `GraphJsonIO` 对 `NodeGraph.tags` 的读写。
- 明确动态端口编译策略；MVP 若采用固定 choice，可暂缓。

### 阶段 1：最小可用对话

目标：右键实体打开一页对话，支持按钮选择并恢复执行流。

需要实现：

- `ShowDialoguePage`
- `CloseDialogue`
- `DialogueSessionManager`
- 外部等待结果或专用 DialogueWait。
- `PacketOpenDialogue`
- `PacketDialogueChoice`
- `DialogueScreen` 或 `DialogueOverlay`
- `BuiltinNodesPlugin` 注册 `dialogue` 分类节点。

文本：

- 先用 `fallback_text`。
- 同时保留 `text_key`，为阶段 2 做兼容。

choice：

- 固定 4 或 6 个 choice。
- 每个 choice 有 `visible`、`enabled`、`text_key`、`fallback`。

### 阶段 2：文本资源化

目标：图里只存 key，支持本地和服务器世界文本资源。

需要实现：

- `DialogueTextManager`
- world 目录 `geometry_dialogue_text`
- JSON 文本读取。
- TSV 导入导出。
- `ResolveDialogueText`
- 缺失 key 诊断。

注意：

- 不要把文本 JSON 放进 `geometry_nodes`。
- 如果资产浏览器要管理文本资源，应扩展独立的文件服务，而不是复用只面向 graph 的 remote graph 服务。

### 阶段 3：独立对话图

目标：对话树作为独立 graph 资产管理和绑定。

需要实现：

- `DialogueEntry`
- `OpenDialogueTree`
- 按 `graph_id + entry_id` 启动对话图。
- 图 `tags: ["dialogue"]` 序列化和资产筛选。
- 实体属性绑定对话图。
- 图热更新时关闭或迁移对话 session。

### 阶段 4：编辑器体验

目标：让 RPG 地图作者高效制作。

建议：

- 对话节点专用 inspector。
- text key 自动生成。
- 从选中节点导出 TSV。
- 缺失文本 key 列表。
- 对话图入口检查。
- 死分支检查。
- choice 条件预览。
- 一键把多段文本拆成多个节点。
- 一键把线性节点合并为 `DialogueSequence`。

### 阶段 5：高级功能

可选：

- 打字机效果。
- 语音和音效。
- 头像和立绘。
- 历史记录 / 回看。
- 多人会话隔离。
- 距离过远自动关闭。
- 对话超时。
- 任务系统集成。
- 商店 / 交易 / 奖励节点集成。
- 客户端本地化资源包优先，服务端动态文本兜底。

## 风险与处理策略

### 外部等待线程生命周期

风险：

- 玩家下线。
- speaker 实体消失。
- 玩家距离过远。
- 客户端关闭 screen。
- 图热更新。
- 服务器关闭。

策略：

- 玩家下线立即关闭该玩家所有 session。
- speaker 消失或距离过远时走 `closed` 或直接结束。
- 图热更新时关闭旧 index 上的 session。
- session 设置超时。
- MVP 不持久化外部等待，避免恢复复杂度过高。

### 图热更新与 runtime node id

风险：

等待中的 continuation 如果只保存 runtime node id，图热更新后 node id 可能失效。

策略：

- session 同时保存 runtime node id 和原始 string node id。
- 保存 graph index identity。
- index 不一致时优先关闭 session。
- 后续如果要迁移，必须基于 string node id 重新解析，并验证端口仍存在。

### 文本同步

风险：

客户端可能没有服务器 world 目录里的动态文本。

策略：

- MVP 由服务端解析最终文本并下发。
- 正式资源包内容可以使用客户端 lang。
- 如果使用客户端 lang，仍要支持服务端 fallback。

### 动态 choice 端口

风险：

当前动态端口编译链路不够稳，直接做无限 choice 会牵扯编辑器、序列化、`GraphFlattener`、`RuntimeGraphIndex`。

策略：

- MVP 固定 4 或 6 个 choice。
- 后续先修动态端口基础设施，再改 `ShowDialoguePage` 为可增减 choice。

### 条件系统不足

风险：

对话分支依赖比较、包含、多条件组合，但当前 `Equal` / `Contain` 不可用。

策略：

- 对话节点只接收 boolean，不内置条件语言。
- 先补通用逻辑节点。
- 对话系统文档和示例中明确 `GetEntityTags -> ListHasValue -> choice_visible` 这种用法。

## 最终推荐

采用“普通蓝图图 + 对话专用节点 + 服务端会话管理 + 客户端样式渲染”的方案。

具体原则：

- 对话流程、条件和动作放在节点图里。
- 对话正文放外部文本资源，节点只引用 key。
- UI 样式用 `style_id` 或 `DialogueStyle` 选择，不把 UI 控件塞进每个文本节点。
- `ShowDialoguePage` 是展示并等待的 action 节点，不是 UI widget。
- 线性文本用 `DialogueSequence`，复杂分支用多个 `ShowDialoguePage`。
- choice 条件复用通用节点体系，先补齐通用逻辑节点。
- 独立对话图使用 `DialogueEntry`，不要让 `entry_id` 停留在概念字段。
- MVP 固定 choice 数，后续再做动态端口。

这条路线和当前 `GraphProcess` / `RuntimeGraphIndex` / `NodeRegistry` 体系最兼容，也能给 RPG 地图制作保留足够专业的扩展空间。
