# 对话树后端实现简明方案

## 范围

本文档只讨论后端：蓝图 VM、节点、会话、网络包、文本资源、图入口和数据格式。不讨论前端 UI、样式绘制、编辑器交互细节。

目标是让服务端可以通过蓝图节点打开对话、等待玩家选择、恢复执行流，并支持对话文本外置。

## 后端核心流程

```text
EntityInteractEntity
  -> OpenDialogueTree 或 ShowDialoguePage
  -> DialogueSessionManager 创建 session
  -> PacketOpenDialogue 下发对话数据
  -> PacketDialogueChoice 回传选择
  -> DialogueSessionManager 校验并恢复 GraphProcess.ExecutionThread
  -> 从 choice_1 / choice_2 / continue / closed 继续执行
```

## 新增文件

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialogueSessionManager.java`

服务端对话会话管理器。

职责：

- 创建 session id。
- 保存玩家 UUID、speaker UUID、graph id、当前等待线程。
- 保存 choice id 到执行输出端口的映射。
- 接收 `PacketDialogueChoice` 后校验并恢复 VM。
- 处理关闭、超时、玩家下线、实体失效、图热更新。

MVP 可只保存在内存中，不做 NBT 持久化。

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialogueSession.java`

单个会话的数据对象。

建议字段：

```text
UUID sessionId
UUID playerUuid
UUID speakerUuid
String graphId
Object graphIndexIdentity
GraphProcess.ExecutionThread waitingThread
Map<String, String> choiceOutputPorts
long createdGameTime
long expireGameTime
```

`choiceOutputPorts` 示例：

```text
"continue" -> "continue"
"choice_1" -> "choice_1"
"choice_2" -> "choice_2"
"closed" -> "closed"
```

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialoguePagePayload.java`

服务端向客户端发送的一页对话数据。

建议字段：

```text
UUID sessionId
String speakerText
String text
String styleId
List<DialogueChoicePayload> choices
```

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialogueChoicePayload.java`

单个选项的数据。

建议字段：

```text
String choiceId
String text
boolean enabled
```

不可见选项不进入列表；不可用选项保留 `enabled = false`。

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialogueTextManager.java`

服务端文本解析器。

职责：

- 从 world 目录加载对话文本。
- 按玩家语言选择文本。
- key 缺失时返回 fallback。
- 支持重载或缓存失效。

MVP 可先只返回 fallback，但接口应保留 key 解析能力。

### `src/main/java/com/mine/geometry_node/core/engine/dialogue/DialogueGraphLauncher.java`

独立对话图启动工具。

职责：

- 根据 `graph_id` 获取 `RuntimeGraphIndex`。
- 查找 `DialogueEntry(entry_id)`。
- 创建或复用 `GraphProcess`。
- 注入 `player`、`speaker`、`entry_id` 事件数据。
- 启动对话图执行线程。

如果不想新增此类，也可以把这部分放进 `GraphEngine`，但独立类更清晰。

## 新增节点文件

建议新建目录：

```text
src/main/java/com/mine/geometry_node/core/node/nodes/dialogue/
```

### `DialogueEntry.java`

类型：`EVENT`

用途：独立对话图入口。

输入：

```text
entry_id: STRING = "root"
```

输出：

```text
flow_out: EXECUTION
player: ENTITY
speaker: ENTITY
entry_id: STRING
```

说明：

`OpenDialogueTree(graph_id, entry_id)` 需要一个真实入口节点，不能只靠概念字段。

### `OpenDialogueTree.java`

类型：`ACTION`

用途：从普通蓝图启动独立对话图。

输入：

```text
flow_in: EXECUTION
player: ENTITY
speaker: ENTITY
graph_id: STRING
entry_id: STRING = "root"
style_id: STRING = "geometry_node:rpg_default"
```

输出：

```text
flow_out: EXECUTION
failed: EXECUTION
```

执行逻辑：

- 校验 player 是否为 `ServerPlayer`。
- 校验 graph id 是否存在。
- 调用 `DialogueGraphLauncher` 启动目标图。
- 成功走 `flow_out`，失败走 `failed`。

### `ShowDialoguePage.java`

类型：`ACTION`

用途：展示一页对话并让 VM 外部等待。

输入：

```text
flow_in: EXECUTION
player: ENTITY
speaker: ENTITY
speaker_key: STRING
speaker_fallback: STRING
text_key: STRING
fallback_text: STRING
style_id: STRING = "geometry_node:rpg_default"
choice_text_key_1: STRING
choice_fallback_1: STRING
choice_visible_1: BOOLEAN = true
choice_enabled_1: BOOLEAN = true
...
choice_text_key_4: STRING
choice_fallback_4: STRING
choice_visible_4: BOOLEAN = true
choice_enabled_4: BOOLEAN = true
```

输出：

```text
continue: EXECUTION
choice_1: EXECUTION
choice_2: EXECUTION
choice_3: EXECUTION
choice_4: EXECUTION
closed: EXECUTION
failed: EXECUTION
```

MVP 固定 4 个 choice。后续再做动态端口。

执行逻辑：

- 读取 player、speaker、文本 key、fallback。
- 用 `DialogueTextManager` 解析文本。
- 计算每个 choice 的 visible / enabled。
- 创建 `DialoguePagePayload`。
- 返回 `ExecutionResult.DialogueWait` 或 `ExternalWait`。

### `DialogueSequence.java`

类型：`ACTION`

用途：连续展示无分支文本。

输入：

```text
flow_in: EXECUTION
player: ENTITY
speaker: ENTITY
text_keys: LIST 或多行 STRING
fallback_texts: LIST 或多行 STRING
style_id: STRING
```

输出：

```text
flow_out: EXECUTION
closed: EXECUTION
failed: EXECUTION
```

MVP 可以先不做，优先实现 `ShowDialoguePage`。

### `CloseDialogue.java`

类型：`ACTION`

用途：关闭玩家当前对话 session。

输入：

```text
flow_in: EXECUTION
player: ENTITY
```

输出：

```text
flow_out: EXECUTION
```

### `ResolveDialogueText.java`

类型：`DATA`

用途：把 key 解析成文本。

输入：

```text
key: STRING
fallback: STRING
args: LIST
```

输出：

```text
text: STRING
```

## 需要修改的文件

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/ExecutionResult.java`

新增外部等待结果。

推荐：

```text
record ExternalWait(String waitType, Object payload) implements ExecutionResult
```

或专用：

```text
record DialogueWait(DialoguePagePayload payload, Map<String, String> outputPorts) implements ExecutionResult
```

建议 MVP 使用专用 `DialogueWait`，实现更直接。

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/GraphProcess.java`

修改 `ExecutionThread.handleExecutionResult`。

新增处理：

- 收到 `DialogueWait` 时，不回收 thread。
- 把 thread 注册到 `DialogueSessionManager`。
- 设置 thread 状态为 `WAITING` 或新增 `EXTERNAL_WAITING`。
- 清空当前执行位置，等待外部选择恢复。

还需要新增恢复方法，供 `DialogueSessionManager` 调用：

```text
resumeFromExternalWait(String outputPort)
```

恢复逻辑：

- 根据等待节点和 outputPort 找到下一个 `IntFlowTarget`。
- 设置 `currentFlowId` 和 `currentEntryPort`。
- state 改为 `RUNNING`。
- 调用 `run()`。

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/GraphProcessSerializer.java`

MVP 可不持久化对话外部等待。

策略：

- 保存时不保存外部等待 session。
- 服务器关闭或读档后，对话 session 全部失效。
- 后续需要中途恢复时，再扩展 serializer。

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/RuntimeGraphIndex.java`

建议新增通用查询方法：

```text
findNodesByTypeAndStaticInput(String nodeType, String inputName, Object expected)
```

用于查找：

```text
DialogueEntry.TYPE_ID + entry_id
```

也可以先在 `DialogueGraphLauncher` 中用 `findNodesByType("dialogue_entry")` 后逐个读取 `getNodeStaticInput(nodeId, "entry_id")`。

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/GraphEngine.java`

新增对话图启动入口，或暴露足够能力给 `DialogueGraphLauncher`。

建议方法：

```text
startDialogueGraph(ServerLevel level, ServerPlayer player, Entity speaker, String graphId, String entryId, String styleId)
```

职责：

- 获取 index。
- 获取或创建 `GraphProcess`。
- 设置环境。
- 启动 `DialogueEntry` 对应 node。

### `src/main/java/com/mine/geometry_node/core/engine/blueprint/execution/storage/DynamicGraphManager.java`

热更新动态图时通知对话系统。

修改点：

```text
saveAndHotReload(...)
  -> oldIndex = dynamicIndexCache.put(...)
  -> GraphEngine.refreshGraphSubscriptions(...)
  -> DialogueSessionManager.closeByGraph(graphId)
```

原因：等待中的 runtime node id 在热更新后可能失效。

### `src/main/java/com/mine/geometry_node/core/network/NetworkHandler.java`

注册对话网络包。

新增 S2C：

```text
PacketOpenDialogue
PacketCloseDialogue
```

新增 C2S：

```text
PacketDialogueChoice
```

后端处理重点是 C2S：

```text
PacketDialogueChoice -> DialogueSessionManager.handleChoice(player, payload)
```

### `src/main/java/com/mine/geometry_node/core/network/packet/s2c/PacketOpenDialogue.java`

服务端下发对话页。

字段：

```text
UUID sessionId
String speakerText
String bodyText
String styleId
List<Choice>
```

`Choice` 字段：

```text
String choiceId
String text
boolean enabled
```

### `src/main/java/com/mine/geometry_node/core/network/packet/s2c/PacketCloseDialogue.java`

服务端要求客户端关闭指定对话。

字段：

```text
UUID sessionId
String reason
```

### `src/main/java/com/mine/geometry_node/core/network/packet/c2s/PacketDialogueChoice.java`

客户端回传选择。

字段：

```text
UUID sessionId
String action
String choiceId
```

`action` 建议值：

```text
select
continue
close
```

### `src/main/java/com/mine/geometry_node/core/node/BuiltinNodesPlugin.java`

注册对话节点：

```text
registry.register("dialogue", new DialogueEntry());
registry.register("dialogue", new OpenDialogueTree());
registry.register("dialogue", new ShowDialoguePage());
registry.register("dialogue", new CloseDialogue());
registry.register("dialogue", new ResolveDialogueText());
```

`DialogueSequence` 可后续注册。

### `src/main/java/com/mine/geometry_node/core/node/port/StandardPorts.java`

可选修改。

如果希望统一端口常量，可增加：

```text
PLAYER
SPEAKER
GRAPH_ID
ENTRY_ID
STYLE_ID
TEXT_KEY
FALLBACK_TEXT
```

也可以先在对话节点内直接使用字符串端口，减少全局端口膨胀。

### `src/main/java/com/mine/geometry_node/core/node/nodes/logics/Equal.java`

修复 `getDefaultDefinition()`，否则对话条件示例无法成立。

建议端口：

```text
A: ANY
B: ANY
bool: BOOLEAN
```

### 新增通用逻辑节点

建议目录：

```text
src/main/java/com/mine/geometry_node/core/node/nodes/logics/
```

优先级：

```text
And.java
Or.java
Not.java
CompareNumber.java
Contains.java
```

这些不是对话专属节点，但对话条件会大量依赖。

## 文本资源格式

### world 动态文本目录

```text
<world>/geometry_dialogue_text/<lang>/<module>/<file>.json
```

示例：

```text
<world>/geometry_dialogue_text/zh_cn/quest/blacksmith.json
<world>/geometry_dialogue_text/en_us/quest/blacksmith.json
```

### JSON 格式

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
      "comment": "开场白"
    },
    "dialogue.quest.blacksmith.intro.choice.trade": {
      "text": "我想看看你的货物。"
    }
  }
}
```

### key 命名

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
```

### 不放入 `geometry_nodes`

对话文本 JSON 不得放入：

```text
<world>/geometry_nodes
```

原因：`DynamicGraphManager` 会递归加载该目录下所有 `.json` 并尝试编译为蓝图。

## 图数据格式

对话图仍然是普通 `NodeGraph`。

示例：

```json
{
  "graph_name": "blacksmith_dialogue",
  "version": "1.0",
  "tags": ["dialogue"],
  "nodes": {
    "entry_root": {
      "node_type": "dialogue_entry",
      "inputs": {
        "entry_id": "root"
      },
      "exec_outputs": {
        "flow_out": {
          "target_node": "page_intro",
          "target_port": "flow_in"
        }
      }
    },
    "page_intro": {
      "node_type": "show_dialogue_page",
      "inputs": {
        "text_key": "dialogue.quest.blacksmith.intro.001",
        "fallback_text": "欢迎来到铁匠铺。",
        "choice_text_key_1": "dialogue.quest.blacksmith.intro.choice.trade",
        "choice_fallback_1": "我想看看你的货物。",
        "choice_visible_1": true,
        "choice_enabled_1": true
      }
    }
  },
  "frames": {}
}
```

## 实体绑定数据

建议用实体持久属性。

```text
dialogue_graph = quest/blacksmith.json
dialogue_entry = root
dialogue_style = geometry_node:rpg_default
```

蓝图读取方式：

```text
EntityInteractEntity.entity
  -> GetEntityAttribute("dialogue_graph")
  -> OpenDialogueTree.graph_id
```

## MVP 实现顺序

1. 修复 `Equal`，补最少逻辑节点。
2. 增加 `DialogueSessionManager`、`DialogueSession`、payload 数据类。
3. 增加 `ExecutionResult.DialogueWait`。
4. 修改 `GraphProcess` 支持外部等待和恢复。
5. 增加 `PacketOpenDialogue`、`PacketDialogueChoice`、`PacketCloseDialogue`，并在 `NetworkHandler` 注册。
6. 实现 `ShowDialoguePage` 和 `CloseDialogue`。
7. 注册对话节点到 `BuiltinNodesPlugin`。
8. 用 `fallback_text` 跑通最小闭环。
9. 实现 `DialogueTextManager` 和 world JSON 文本。
10. 实现 `DialogueEntry`、`OpenDialogueTree`、`DialogueGraphLauncher`。

## 暂不做

MVP 后端暂不做：

- 动态无限 choice 端口。
- 对话 session NBT 持久化。
- 富文本 `Component` 端口。
- 复杂 choice DTO 端口。
- 任务系统专用节点。
- 对话样式资源的后端校验。

这些可以在最小闭环稳定后再做。
