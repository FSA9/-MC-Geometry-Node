# Core Engine Architecture Review

本文档分析 `com.mine.geometry_node.core.engine` 当前架构，覆盖蓝图、对话树、行为树占位以及它们和 node、network、client UI 的边界。结论基于当前代码阅读，不包含实现改动。

## 一句话结论

当前 engine 已经开始抽象 `GraphKind` 和 `GraphRuntime`，方向是对的；但真正的通用图基础设施仍大量堆在 `blueprint/execution` 下面。蓝图 VM、图编译、资源仓库、远程文件服务、Minecraft 事件适配、NBT 持久化、网络视觉广播混在一起，导致行为树很难自然接入，对话树也更像蓝图外部等待服务，而不是独立 runtime。

优先重构目标不是马上搬文件，而是先拆职责边界：

1. 把 graph 文档、图 ID、仓库、编译入口、绑定实例生命周期提升为 `core.engine.graph` 通用层。
2. 让 `blueprint` 只负责蓝图 VM 语义，即执行流、数据流、变量、等待、事件入口匹配。
3. 让 `dialogue` 只负责对话 session、选择、展示网关、生命周期策略。
4. 为 `behavior_tree` 预留独立 tick/status/blackboard 语义，不复用蓝图 `ExecutionResult`。
5. 把 network/client UI DTO 从 `blueprint.execution.storage` 中解耦。

## 当前目录职责地图

```text
core/engine
├─ graph
│  ├─ GraphKind.java
│  └─ runtime
│     ├─ GraphRuntime.java
│     ├─ GraphRuntimeRegistry.java
│     ├─ GraphRuntimeContext.java
│     ├─ GraphExecutionHandle.java
│     └─ ExternalWaitRequest.java
├─ blueprint
│  ├─ BlueprintRuntime.java
│  └─ execution
│     ├─ GraphEngine.java
│     ├─ GraphProcess.java
│     ├─ ExecutionContext.java
│     ├─ ExecutionResult.java
│     ├─ RuntimeGraphIndex.java
│     ├─ GraphFlattener.java
│     ├─ GraphProcessSerializer.java
│     ├─ attachment
│     ├─ datatypes
│     ├─ event_handler
│     ├─ state
│     ├─ storage
│     └─ variables
├─ dialogue
├─ behavior
└─ service
```

### 通用图层

| 文件 | 当前职责 | 主要问题 | 目标职责 |
| --- | --- | --- | --- |
| `graph/GraphKind.java` | 定义 `BLUEPRINT`、`DIALOGUE`、`BEHAVIOR_TREE`、`UNKNOWN`。 | 概念正确，但 `DIALOGUE` 当前更像外部等待处理器，不是真正独立图 runtime。 | 保持为图家族枚举；后续配合 graph document 和 compiler 使用。 |
| `graph/runtime/GraphRuntime.java` | 运行时注册接口，提供 `kind/id/init/beginExternalWait/endExternalWait`。 | 太薄，不能表达 compile、create instance、start、tick、save/load。 | 扩展为真正 runtime contract，至少覆盖编译产物、实例生命周期、tick、等待恢复。 |
| `graph/runtime/GraphRuntimeRegistry.java` | 按 `GraphKind` 注册 runtime。 | 可用，但只能查 runtime，不能管理实例和仓库。 | 保留为 runtime registry；配合 `GraphRepository`、`GraphInstanceStore`。 |
| `graph/runtime/GraphRuntimeContext.java` | 保存 `ServerLevel` 和 owner entity。 | 还没有广泛使用；蓝图仍直接用 `GraphProcess.setEnvironment`。 | 统一 runtime tick/start 上下文。 |
| `graph/runtime/GraphExecutionHandle.java` | 外部等待恢复/关闭句柄。 | `unwrap()` 让其他 runtime 强转蓝图 `ExecutionContext`，抽象泄漏。 | 暴露通用 metadata/tempData/runtime context，去掉跨 runtime 强转需求。 |
| `graph/runtime/ExternalWaitRequest.java` | 外部等待 marker interface。 | 目前主要服务蓝图 `ExecutionResult.ExternalWait`。 | 作为通用 suspend/resume 协议，支持 dialogue、动画、路径、行为树等待。 |
| `service/GraphEngineServices.java` | 空的共享服务占位。 | 没有实际承载端口，导致 runtime 直接依赖 network。 | 放 `VisualSink`、`DialoguePresenter`、`NetworkOutbox`、`TextResolver` 等服务端口。 |

## 蓝图执行链路

1. `GeometryNode` 启动时注册 `BlueprintRuntime`、`DialogueRuntime`、`BehaviorTreeRuntime`，注册 `GraphEventHandler`，并将 `GraphResourceManager` 作为 datapack reload listener。
2. datapack 图通过 `GraphResourceManager.apply -> RuntimeGraphIndex.build` 编译；动态服务器图通过 `DynamicGraphManager.loadAllFromDisk/saveAndHotReload -> RuntimeGraphIndex.build` 编译。
3. `RuntimeGraphIndex.build` 解析 JSON，调用 `GraphFlattener.flatten` 展开 `node_group`，桥接 `group_in/group_out`，烘焙默认输入，构建 int node id、端口字典、执行流数组、数据流数组、类型索引、`receive_blueprint` 频率索引。
4. 命令/API 通过 `BlueprintRuntime -> GraphEngine.bindGraph/bindGlobalGraph` 绑定图。实体绑定写入 `EntityGraphAttachment`，全局绑定写入 `GlobalGraphStorage`，并预热 `GraphProcess`。
5. Minecraft/NeoForge/Architectury 事件由 dispatcher 转成蓝图事件类型，调用 `GraphEngine.dispatchEvent(level, target, eventType, initializer)`；initializer 直接向 `GraphProcess.ExecutionThread` 注入 event data。
6. `GraphEngine` 先跑全局图，再跑目标实体绑定图，按 `RuntimeGraphIndex.findNodesByType(eventType)` 找入口节点，创建或复用 `GraphProcess`，设置环境后 `executeEvent`。
7. `GraphProcess.ExecutionThread.run()` 循环通过 `NodeRegistry` 找 `BaseNode`，调用 `execute(context)`，按 `ExecutionResult` 跳转、等待、外部等待、结束或报错。
8. 数据流是 pull model：节点调用 `context.getInputValue(port)` 时，线程用 `RuntimeGraphIndex.findInputSource` 找上游，递归执行上游 `compute(context, sourcePort)`，并缓存单帧结果。
9. 等待线程进入 `sleepingThreads`，每 tick 由 `GraphEventHandler -> LevelGraphAttachment/EntityGraphAttachment -> GraphProcess.tick` 唤醒。
10. 进程、变量栈、休眠线程、属性通过 `GraphProcessSerializer` 保存到 entity attachment 或 level saved data。

## 蓝图文件职责清单

| 文件 | 当前职责 | 问题 | 建议归属 |
| --- | --- | --- | --- |
| `blueprint/BlueprintRuntime.java` | 蓝图 runtime facade，几乎全部委托 `GraphEngine`。 | 门面很薄，仍暴露 `Consumer<GraphProcess.ExecutionThread>`。 | `blueprint.api`，只暴露稳定 API，不暴露 VM 内部线程。 |
| `blueprint/execution/GraphEngine.java` | 图索引查找、绑定/解绑、事件派发、进程创建、订阅索引维护。 | 过重，混合 repository、binding、dispatcher、process lifecycle、subscription index。 | 拆成 `BlueprintRuntimeService`、`GraphRepository`、`BlueprintBindingService`、`BlueprintEventRouter`。 |
| `blueprint/execution/GraphProcess.java` | 蓝图 VM 进程，含环境、变量栈、线程池、休眠调度、内部 `ExecutionThread`。 | 一个类承载 VM 状态、调度、数据求值、外部等待、持久属性、网络视觉广播。 | `blueprint.runtime.GraphProcess` + 独立 `BlueprintExecutionThread` + 服务端口注入。 |
| `blueprint/execution/ExecutionContext.java` | 节点可见上下文，暴露世界/实体/变量/输入/事件数据/持久属性/调度/视觉广播。 | 太宽；所有节点获得过多特权，行为树也不能复用。 | 拆为小接口：`WorldAccess`、`VariableAccess`、`EventDataAccess`、`SchedulerAccess`、`VisualAccess`。 |
| `blueprint/execution/ExecutionResult.java` | 蓝图节点执行结果协议。 | 适合蓝图，不适合行为树。`ExternalWait` 也被蓝图结果绑定。 | 保持蓝图专用；通用等待协议上移到 graph runtime。 |
| `blueprint/execution/RuntimeGraphIndex.java` | 运行时索引，同时负责 JSON build、校验、值解析。 | 编译逻辑和运行时查询混在一起。 | `blueprint.compiler.BlueprintCompiler` 负责 build；`RuntimeGraphIndex` 只做不可变查询。 |
| `blueprint/execution/GraphFlattener.java` | 展平节点组、桥接执行/数据流、烘焙默认输入。 | 编译层依赖 `NodeRegistry` 和节点默认定义。 | `blueprint.compiler.GraphFlattener`，通过 schema registry 注入默认值。 |
| `blueprint/execution/GraphProcessSerializer.java` | 进程、线程、变量栈、容器属性的 NBT 序列化。 | 直接访问 VM 内部字段，并反向调用 `GraphEngine.getGraphIndex`。 | `blueprint.persistence.BlueprintProcessSnapshotSerializer`，面向 snapshot/accessor。 |
| `attachment/GraphContainer.java` | 复用进程/属性/tick/序列化。 | 名字像通用 graph container，但只持有蓝图 `GraphProcess`。 | `blueprint.persistence.BlueprintProcessContainer` 或通用容器按 `GraphKind` 分桶。 |
| `attachment/EntityGraphAttachment.java` | 实体绑定图 ID 和实体进程。 | 只服务蓝图，但名字像通用图附件。 | 通用 `graph.attachment.EntityGraphAttachment` + blueprint process store 分桶。 |
| `attachment/LevelGraphAttachment.java` | 世界级蓝图进程和属性 saved data。 | 只存蓝图进程。 | 通用 `LevelGraphAttachment` 或 `BlueprintLevelProcessStore`。 |
| `attachment/EntityImmunityAttachment.java` | 实体伤害免疫状态。 | 属于 gameplay capability，不是蓝图执行附件。 | 移到 `core.gameplay.state` 或 `core.engine.blueprint.integration.minecraft.state`。 |
| `datatypes/DynamicData.java` | 动态视觉/表达式相关数据 record。 | 名字泛，实际语义偏视觉/表达式。 | 归到 visual/effect model 或 expression model。 |
| `datatypes/ExpressionData.java` | 表达式数据 record。 | 同上。 | 归到 expression subsystem。 |
| `event_handler/GraphEventHandler.java` | 注册 tick 和各领域 dispatcher，驱动全局/活跃实体进程。 | 事件采集和蓝图 tick 调度耦合。 | `blueprint.integration.minecraft.BlueprintEventBootstrap` + 通用 tick scheduler。 |
| `dispatcher/EntityDispatcher.java` | Entity/Living 事件转蓝图事件节点。 | 直接知道节点类型和 `StandardPorts`，且 `OnEntityTick` 绕过普通派发路径。 | `minecraft.event` 采集原始事件，`blueprint.event` 适配为蓝图事件 payload。 |
| `dispatcher/PlayerDispatcher.java` | Player/interaction/chat/command/xp 等事件转蓝图事件。 | 同上；也直接操作 `PlayerInputStateManager`。 | 同上。 |
| `dispatcher/BlockDispatcher.java` | 方块破坏/放置事件转蓝图事件。 | 同上。 | 同上。 |
| `dispatcher/WorldDispatcher.java` | chunk/explosion/lightning/portal 事件转蓝图事件。 | 同上。 | 同上。 |
| `state/PlayerInputStateManager.java` | 玩家输入状态缓存，派发按键事件。 | 状态服务在 blueprint execution 下，但可能是通用 gameplay input。 | `core.engine.input` 或 `core.gameplay.input`。 |
| `storage/GraphResourceManager.java` | datapack `data/*/graphs` 图加载和编译。 | repository 名义上通用，但只产蓝图 index。 | `graph.storage.ResourceGraphRepository` + runtime-specific compiler。 |
| `storage/DynamicGraphManager.java` | 世界目录动态图加载、保存、热重载。 | 文件仓库和蓝图热重载绑定。 | `graph.storage.DynamicGraphRepository`，根据 `GraphKind` 选择 compiler。 |
| `storage/GlobalGraphStorage.java` | 保存全局绑定图 ID。 | 只保存 ID，不保存 kind；仍在 blueprint storage。 | `graph.binding.GlobalGraphBindingStore`。 |
| `storage/GraphIdMapper.java` | 图 ID 和相对路径映射、安全校验。 | 应是通用 graph file utility。 | `graph.storage.GraphPathMapper`。 |
| `storage/RemoteGraphFileService.java` | 远程文件列表、上传、下载、复制、移动、删除。 | UI/network 文件服务，不是 VM 执行存储；DTO 泄漏到 client/network。 | `graph.storage.remote.RemoteGraphFileService` 或 `core.network.graphfile` DTO。 |
| `storage/RemoteGraphPermissions.java` | 远程图管理权限。 | 和文件服务绑定，放 execution 下不合理。 | `graph.storage.remote.RemoteGraphPermissions` 或 server application service。 |
| `variables/VariableRegistry.java` | Java 对象和 NBT Tag 双向转换。 | 可用但 `isSupported(Number)` 与实际 `toTag` 支持不完全一致。 | `graph.persistence.VariableCodecRegistry`，作为通用服务。 |
| `variables/VariableSerializer.java` | 变量序列化接口。 | 可保留。 | 同上。 |

## 对话树当前链路

当前对话不是独立对话图 VM，而是蓝图节点通过 `ExternalWait` 调用 `DialogueRuntime`：

1. `BeginDialogue` 根据输入、事件数据、owner 推断 player/speaker/target/style/policy，并写入 `ExecutionContext.tempData(DialogueContext.TEMP_KEY)`。
2. `ShowDialoguePage` 解析文本和选项，构造 `DialoguePagePayload`，返回 `ExecutionResult.externalWait(GraphKind.DIALOGUE, new DialogueWaitRequest(player, page))`。
3. `GraphProcess.ExecutionThread` 进入 `EXTERNAL_WAITING`，调用 `DialogueRuntime.beginExternalWait(handle, request)`。
4. `DialogueRuntime` 关闭玩家旧 session，检查实体占用，创建 `DialogueSession`，挂上 `GraphExecutionHandle`、page、context、policy。
5. `styleId == default` 时走 `DefaultDialogueRenderer` 发聊天消息；其他样式发 `PacketOpenDialogue` 给客户端。
6. 玩家选择后，命令或 C2S 包调用 `DialogueRuntime.choose(...)`，runtime 关闭 session，并以 choice id 作为端口名调用 `handle.resume(choiceId)`。
7. 关闭、超时、距离过远、死亡、退出、维度切换统一恢复 `closed` 分支。

## 对话文件职责清单

| 文件 | 当前职责 | 问题 | 建议归属 |
| --- | --- | --- | --- |
| `dialogue/DialogueRuntime.java` | 外部等待适配、session 管理、生命周期策略、展示分发、网络发包、busy 文本、玩家/实体解析。 | 严重过宽；领域层直接依赖 network、packet、renderer；通过 `unwrap()` 读蓝图上下文。 | 拆为 `DialogueExternalWaitRuntime`、`DialogueSessionService`、`DialoguePolicyEvaluator`、`DialoguePresenter`。 |
| `dialogue/DialogueEventHandler.java` | 服务端 tick/logout/death/dimension/server stop 桥接到 runtime。 | 可用，但直接调用 singleton runtime。 | `dialogue.integration.minecraft.DialogueLifecycleBridge`。 |
| `dialogue/context/DialogueContext.java` | 一次执行中的 player/speaker/target/style/policy 变量。 | 持有 `ServerPlayer`，并靠蓝图 temp data 传递。 | 纯值对象，`DialogueWaitRequest` 直接携带，避免 handle unwrap。 |
| `dialogue/session/DialogueSession.java` | 单个对话 session 状态。 | 持有 `GraphExecutionHandle`，close 语义和 runtime close 互相绕开以避免递归。 | Session 保存状态，handle 可放在 service 的 active wait map。 |
| `dialogue/session/DialogueSessionManager.java` | 内存 session 注册表和查询。 | 含部分 policy 查询，还是偏低层 map wrapper。 | `DialogueSessionStore`，policy 查询移到 service/evaluator。 |
| `dialogue/session/DialogueSessionPolicy.java` | 距离、多玩家占用、超时、busy 文本策略。 | 合理。 | 保留到 `dialogue.model`。 |
| `dialogue/session/DialogueCloseReason.java` | 关闭原因字符串常量。 | 字符串无类型约束。 | 改 enum 并提供 packet/string codec。 |
| `dialogue/payload/DialogueWaitRequest.java` | 蓝图向对话 runtime 发起 external wait。 | 不携带 `DialogueContext`，导致 runtime 强转蓝图上下文。 | 携带 page + context + policy snapshot。 |
| `dialogue/payload/DialoguePagePayload.java` | 服务端当前页模型。 | 有 metadata 但 packet 未传输；可变 list/map getter。 | 不可变 `DialoguePage`，传输 DTO 独立。 |
| `dialogue/payload/DialogueChoicePayload.java` | 服务端选项模型。 | `targetNodeId`、metadata 未进入 packet。 | 清理未用字段或补齐协议。 |
| `dialogue/render/DefaultDialogueRenderer.java` | 默认 chat 样式渲染，click command 选择。 | renderer 拼命令，和 server command 反向耦合；runtime 知道 renderer。 | `ChatCommandDialoguePresenter`。 |
| `dialogue/text/DialogueTextManager.java` | 文本 key-value 管理。 | 节点直接访问 `DialogueRuntime.INSTANCE.getTextManager()`。 | `DialogueTextResolver` 服务，注入给节点 factory/runtime。 |
| `dialogue/launcher/DialogueGraphLauncher.java` | 预留独立对话图启动接口。 | 与当前 `ExternalWait` 路线并存但未接上。 | 暂时隔离到 `dialogue.future` 或删除；做独立对话树时再实现。 |
| `dialogue/launcher/NoopDialogueGraphLauncher.java` | launcher 空实现。 | 只为未完成路线服务。 | 同上。 |

## 行为树现状和接入建议

`behavior/BehaviorTreeRuntime.java` 当前只是 `GraphRuntime` 占位，没有编译、执行、tick、黑板、节点语义。

行为树不能复用蓝图 `ExecutionResult`，因为两者语义不同：

- 蓝图是事件触发后的执行流跳转和数据流求值。
- 行为树是持续 tick，节点返回 `RUNNING/SUCCESS/FAILURE`，需要 blackboard、abort/reset、selector/sequence/decorator 语义。

建议未来包结构：

```text
core/engine/behavior_tree
├─ BehaviorTreeRuntime.java
├─ execution
│  ├─ BehaviorTreeProcess.java
│  ├─ BehaviorTreeContext.java
│  ├─ BehaviorStatus.java
│  ├─ BehaviorBlackboard.java
│  └─ BehaviorTreeIndex.java
├─ nodes
│  ├─ BehaviorNodeExecutor.java
│  ├─ BehaviorTask.java
│  ├─ BehaviorComposite.java
│  └─ BehaviorDecorator.java
└─ integration
   ├─ BehaviorTreeBindingService.java
   └─ BehaviorSensorBridge.java
```

行为树节点接口建议：

```java
interface BehaviorNodeExecutor {
    BehaviorStatus tick(BehaviorTickContext context);
    default void reset(BehaviorTickContext context) {}
    default void abort(BehaviorTickContext context) {}
}

enum BehaviorStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}
```

## 主要问题清单

### P0: 阻碍行为树和长期维护的问题

1. `GraphRuntime` 太薄，只能注册 runtime 和处理 external wait，不能承载图编译、实例生命周期、tick、持久化。
2. `core.node.nodes.BaseNode` 直接依赖 `blueprint.execution.ExecutionContext/ExecutionResult`，导致节点系统天然是蓝图节点系统。
3. `GraphContainer`、`EntityGraphAttachment`、`LevelGraphAttachment` 名字通用，但内部只保存蓝图 `GraphProcess`。
4. `GraphEngine` 是过重门面，集成了仓库查询、绑定、派发、进程生命周期、订阅索引。
5. `GraphProcess` 是过重 VM 类，同时做进程状态、线程池、调度、变量、数据求值、外部等待、网络视觉广播。

### P1: 耦合和边界问题

1. `GraphProcess` 和 `DialogueRuntime` 直接依赖 `NetworkHandler` 或具体 packet。
2. `DialogueRuntime` 通过 `GraphExecutionHandle.unwrap()` 强转蓝图 `ExecutionContext`，跨 runtime 抽象泄漏。
3. `RemoteGraphFileService` 位于 `blueprint/execution/storage`，但被 network 和 client UI 直接使用。
4. `RuntimeGraphIndex.build` 同时承担 JSON 解析、flatten、校验、索引构建。
5. `GraphProcessSerializer` 直接读写 VM 内部字段，并反向调用 `GraphEngine.getGraphIndex`。
6. 事件 dispatcher 直接知道蓝图事件节点类型和 `StandardPorts`，事件采集无法被行为树/其他 runtime 复用。
7. `NetworkHandler` 同时处理图同步、远程文件、视觉效果、对话、按键输入，且 common 网络类直接引用 client state。

### P2: 语义和一致性问题

1. `GraphKind.DIALOGUE` 当前不是完整对话图 runtime，而是蓝图 external wait runtime。
2. `DialogueGraphLauncher` 与 `ExternalWait` 两条路线并存，前者未真正接入。
3. default 对话走聊天命令，rpg 对话走 packet，展示边界不一致。
4. `DialogueCloseReason` 是字符串常量，缺少类型约束。
5. `DialoguePagePayload/DialogueChoicePayload` 有字段未进入 packet，模型和协议不一致。
6. `VariableRegistry.isSupported(Number)` 比 `toTag` 实际支持范围更宽。
7. `OnEntityTick` 有特殊分发路径，绕过普通 `GraphEngine.dispatchEvent`。

## 目标架构

```text
core/engine
├─ graph
│  ├─ model
│  │  ├─ GraphDocument
│  │  ├─ GraphKind
│  │  ├─ GraphId
│  │  └─ GraphBinding
│  ├─ compile
│  │  ├─ GraphCompiler
│  │  └─ CompiledGraph
│  ├─ runtime
│  │  ├─ GraphRuntime
│  │  ├─ GraphInstance
│  │  ├─ GraphExecutionHandle
│  │  ├─ GraphRuntimeContext
│  │  └─ GraphRuntimeRegistry
│  ├─ storage
│  │  ├─ ResourceGraphRepository
│  │  ├─ DynamicGraphRepository
│  │  ├─ RemoteGraphFileService
│  │  └─ GraphPathMapper
│  └─ attachment
│     ├─ EntityGraphAttachment
│     ├─ LevelGraphAttachment
│     └─ GraphInstanceStore
├─ blueprint
│  ├─ api
│  ├─ compiler
│  ├─ runtime
│  ├─ persistence
│  └─ integration
├─ dialogue
│  ├─ model
│  ├─ runtime
│  ├─ service
│  ├─ presenter
│  └─ integration
├─ behavior_tree
│  ├─ execution
│  ├─ nodes
│  └─ integration
└─ service
   ├─ VisualSink
   ├─ DialoguePresenter
   ├─ TextResolver
   └─ NetworkOutbox
```

### 目标 GraphRuntime 合同

可以逐步演进，不需要一次性替换：

```java
interface GraphRuntime {
    GraphKind kind();
    String id();
    void init();

    CompiledGraph compile(GraphDocument document);
    GraphInstance createInstance(GraphBinding binding, CompiledGraph graph);
    GraphExecutionHandle start(GraphInvocation invocation);
    void tick(GraphInstance instance, GraphRuntimeContext context);

    default boolean beginExternalWait(GraphExecutionHandle handle, ExternalWaitRequest request) {
        return false;
    }
}
```

## 迁移路线

### 阶段 1: 先加边界，不搬大量文件

1. 新增 `GraphRepository` 接口，封装 `GraphResourceManager` 和 `DynamicGraphManager` 的索引查询。
2. 新增 `BlueprintProcessStore` 接口，让 `GraphEngine` 不直接知道 `EntityGraphAttachment/LevelGraphAttachment` 细节。
3. 新增 `BlueprintEventRouter` 或 `EventSubscriptionIndex`，把 `receive_blueprint` 订阅从 `GraphEngine` 拆出。
4. `GraphEngine` 保持 API 不变，但内部委托这些服务。

### 阶段 2: 拆编译和运行时索引

1. 新增 `blueprint.compiler.BlueprintCompiler`。
2. 将 `RuntimeGraphIndex.build`、数据环校验、JSON value parse 迁到 compiler。
3. `RuntimeGraphIndex` 只保留不可变数组和查询 API。
4. `GraphFlattener` 移入 compiler 包，并通过 schema/default provider 获取默认输入，不直接硬依赖 runtime registry。

### 阶段 3: 拆 VM 进程

1. 将 `GraphProcess.ExecutionThread` 拆为独立 `BlueprintExecutionThread`。
2. 将视觉广播、持久属性访问改为通过 `GraphEngineServices` 提供。
3. 将 `ExecutionContext` 拆成小能力接口；节点按需依赖。
4. `GraphProcessSerializer` 面向 snapshot，不再直接读写 VM 内部字段。

### 阶段 4: 整理对话 runtime

1. 隔离或删除未接入的 `DialogueGraphLauncher` 路线。
2. 从 `DialogueRuntime` 抽出 `DialogueSessionService`。
3. 抽出 `DialoguePolicyEvaluator` 处理距离、超时、死亡、维度切换。
4. 抽出 `DialoguePresenter`，让 runtime 不直接 import network packet 和 renderer。
5. `DialogueWaitRequest` 携带 `DialogueContext`，消除 `handle.unwrap()`。
6. 统一 default/rpg 展示边界，或者明确它们是两个 presenter 实现。

### 阶段 5: 整理 storage/network/UI DTO

1. 将 `GraphIdMapper`、`RemoteGraphFileService`、`RemoteGraphPermissions` 移到 `core.engine.graph.storage` 或 `core.network.graphfile`。
2. 网络 packet 不再引用 `RemoteGraphFileService.Entry/UploadFile/Conflict`，改用 packet 自己的 DTO 或 graph storage model。
3. client UI 只依赖 packet/client API 和 graph document schema，不直接 import blueprint execution storage。

### 阶段 6: 接入行为树

1. 新建 `behavior_tree` 包，不复用蓝图 `ExecutionResult`。
2. 定义 `BehaviorStatus`、`BehaviorBlackboard`、`BehaviorTreeProcess`、`BehaviorNodeExecutor`。
3. 复用 graph document、graph id、repository、binding、runtime registry。
4. 实体绑定和 tick 走通用 graph instance store，实例按 `GraphKind` 分桶。

## 建议最终文件职责

### 通用 graph 层

| 目标文件/包 | 职责 |
| --- | --- |
| `graph/model/GraphDocument` | 编辑器和存储使用的原始图文档，不绑定蓝图 VM。 |
| `graph/model/GraphId` | 图 ID、namespace/path、json 后缀和路径安全规则。 |
| `graph/model/GraphBinding` | 描述某个 graph 绑定到 entity、level、global 或其他 owner。 |
| `graph/compile/GraphCompiler` | 按 `GraphKind` 选择 runtime compiler。 |
| `graph/runtime/GraphRuntime` | 图家族运行时合同。 |
| `graph/runtime/GraphInstance` | 一个绑定后的运行实例抽象。 |
| `graph/runtime/GraphExecutionHandle` | 可恢复/可关闭执行句柄，不暴露具体 VM。 |
| `graph/storage/ResourceGraphRepository` | datapack 图仓库。 |
| `graph/storage/DynamicGraphRepository` | 世界目录动态图仓库和热重载。 |
| `graph/storage/RemoteGraphFileService` | 服务器图文件浏览/上传/下载/管理服务。 |
| `graph/attachment/GraphInstanceStore` | entity/level 上按 kind 保存 runtime instance。 |

### 蓝图层

| 目标文件/包 | 职责 |
| --- | --- |
| `blueprint/api/BlueprintRuntime` | 稳定蓝图 API facade。 |
| `blueprint/compiler/BlueprintCompiler` | JSON/NodeGraph 到 `RuntimeGraphIndex` 的编译入口。 |
| `blueprint/compiler/GraphFlattener` | 节点组展开和边界桥接。 |
| `blueprint/compiler/BlueprintGraphValidator` | 数据流环、端口合法性等校验。 |
| `blueprint/runtime/RuntimeGraphIndex` | 蓝图运行时只读索引。 |
| `blueprint/runtime/GraphProcess` | 蓝图进程状态和变量作用域。 |
| `blueprint/runtime/BlueprintExecutionThread` | 单次执行流、数据求值、等待恢复。 |
| `blueprint/runtime/ExecutionResult` | 蓝图专属执行结果协议。 |
| `blueprint/runtime/context/*` | 节点运行时能力接口。 |
| `blueprint/persistence/*` | 蓝图进程 snapshot 和 NBT codec。 |
| `blueprint/integration/minecraft/*` | Minecraft 事件采集到蓝图事件的适配。 |

### 对话层

| 目标文件/包 | 职责 |
| --- | --- |
| `dialogue/model/DialoguePage` | 不可变对话页模型。 |
| `dialogue/model/DialogueChoice` | 不可变选项模型。 |
| `dialogue/model/DialogueCloseReason` | enum 关闭原因。 |
| `dialogue/service/DialogueSessionService` | session create/choose/close 的唯一业务入口。 |
| `dialogue/service/DialoguePolicyEvaluator` | 超时、距离、actor/player 状态检查。 |
| `dialogue/service/DialogueTextResolver` | 文本 key 解析。 |
| `dialogue/runtime/DialogueExternalWaitRuntime` | external wait 和 session service 的桥。 |
| `dialogue/presenter/DialoguePresenter` | 展示端口。 |
| `dialogue/presenter/PacketDialoguePresenter` | packet UI 展示实现。 |
| `dialogue/presenter/ChatCommandDialoguePresenter` | 默认聊天展示实现。 |
| `dialogue/integration/DialogueLifecycleBridge` | 服务端事件到 policy/session service。 |

### 行为树层

| 目标文件/包 | 职责 |
| --- | --- |
| `behavior_tree/BehaviorTreeRuntime` | 行为树 runtime facade。 |
| `behavior_tree/execution/BehaviorTreeProcess` | 某实体上的行为树实例。 |
| `behavior_tree/execution/BehaviorBlackboard` | 行为树黑板。 |
| `behavior_tree/execution/BehaviorStatus` | `RUNNING/SUCCESS/FAILURE`。 |
| `behavior_tree/nodes/BehaviorNodeExecutor` | 行为树节点执行接口。 |
| `behavior_tree/nodes/BehaviorComposite` | selector/sequence 等组合节点语义。 |
| `behavior_tree/nodes/BehaviorDecorator` | 条件、反转、冷却等装饰器语义。 |
| `behavior_tree/integration/BehaviorSensorBridge` | Minecraft 状态/事件转行为树 sensor/blackboard。 |

## 风险和验证建议

1. 重构前给 `GraphEngine.dispatchEvent`、`GraphProcess.Wait`、`ExternalWait`、`GraphProcessSerializer` 加最小回归测试或调试用样例图。
2. 对话至少覆盖：选择恢复对应端口、关闭恢复 `closed`、玩家退出关闭、距离过远关闭、busy 占用、多样式展示。
3. 文件服务至少覆盖：路径穿越防护、上传覆盖冲突、目录下载 flatten、移动目录避免移入自身。
4. 先保留旧 facade，迁移 imports 后再删除旧位置，避免一次性破坏命令、network、client UI。
5. 对行为树不要先写复杂 UI；先实现 runtime 最小闭环：编译一个树、绑定实体、tick root、blackboard 读写、返回状态。
