# Core Engine Modular Refactor Plan

本文档用于规划 `com.mine.geometry_node.core.engine` 的下一轮重构。方案按模块纵向推进，但不追求把每个概念都拆成独立文件。目标是让包边界更清楚，同时保留“看文件名就知道它干什么”的可读性。

## 设计取向

本轮重构的重点不是制造更多文件，而是修正依赖方向和职责归属：

- 一个模块优先保留 3 到 8 个核心文件。
- 只有出现多个实现、独立测试替身、明显职责膨胀时，才继续拆接口。
- 文件名优先表达具体职责，不用过度抽象命名。
- 迁包之前先切断反向依赖，避免只是把旧耦合搬到新包。
- 旧接口能删就删，但删除顺序服务于模块完成度，不为了“架构纯度”一次性全改。
- 行为树只保留最小边界，不在本轮深究。
- 编译由维护者执行，本文档只规划模块和迁移顺序。

简单说：不要把一个清楚的大类拆成十个看不懂的小类；先把“放错包、跨模块依赖、旧接口泄漏”解决掉。

## 当前真正的问题

当前很多文件单看名字是清楚的，例如 `GraphEngine`、`GraphProcess`、`DialogueRuntime`、`RemoteGraphFileService`。问题不在于文件名完全错误，而在于所有权和依赖方向不清楚：

- `blueprint/execution/storage` 里放了通用图文件服务和远程文件服务，这些不属于蓝图 VM 执行。
- `RuntimeGraphIndex.build` 同时做 JSON 读取、节点组展开、校验和运行时索引构建。
- `DynamicGraphManager` 保存动态图后直接调用 `GraphEngine.refreshGraphSubscriptions`，storage 反向依赖 blueprint runtime。
- `GraphResourceManager` 直接产出 `RuntimeGraphIndex`，datapack storage 和蓝图编译绑死。
- `RemoteGraphFileService.Entry/Conflict/UploadFile` 是 nested records，并被 packet 和 client UI 广泛引用，旧包 wrapper 很难透明兼容嵌套类型。
- `GraphEngine` 同时查图、绑定图、派发事件、维护订阅、创建进程。
- `GraphProcess` 同时管理进程状态、执行线程、数据流求值、等待、外部等待、持久属性、视觉广播，并直接依赖 network packet。
- `DialogueRuntime` 同时管理 session、策略、展示、packet、external wait，并且通过 `GraphExecutionHandle.unwrap()` 强转蓝图上下文。

因此本轮重构不是“为了拆文件而拆文件”，而是按模块把这些职责归位。尤其要注意：迁包必须在依赖方向切开之后做。

## 最终目录草案

这是收敛后的目标形态。保留现在大部分容易理解的类名，只调整包位置和少量关键接口。

```text
core/engine
├─ graph
│  ├─ GraphKind.java
│  ├─ runtime
│  │  ├─ ExternalWaitRequest.java
│  │  ├─ GraphExecutionHandle.java
│  │  ├─ GraphRuntime.java
│  │  ├─ GraphRuntimeContext.java
│  │  └─ GraphRuntimeRegistry.java
│  └─ storage
│     ├─ GraphPathMapper.java
│     ├─ GraphResourceManager.java
│     ├─ DynamicGraphManager.java
│     ├─ RemoteGraphFileService.java
│     ├─ RemoteGraphPermissions.java
│     ├─ RemoteGraphEntry.java
│     ├─ RemoteGraphConflict.java
│     └─ RemoteGraphUploadFile.java
├─ blueprint
│  ├─ BlueprintRuntime.java
│  ├─ compile
│  │  ├─ BlueprintCompiler.java
│  │  └─ GraphFlattener.java
│  ├─ runtime
│  │  ├─ GraphEngine.java
│  │  ├─ GraphProcess.java
│  │  ├─ RuntimeGraphIndex.java
│  │  ├─ ExecutionContext.java
│  │  ├─ ExecutionResult.java
│  │  └─ GraphProcessSerializer.java
│  ├─ attachment
│  │  ├─ EntityGraphAttachment.java
│  │  ├─ LevelGraphAttachment.java
│  │  ├─ GraphContainer.java
│  │  ├─ EntityImmunityAttachment.java
│  │  └─ GlobalGraphStorage.java
│  ├─ event
│  │  ├─ GraphEventHandler.java
│  │  └─ dispatcher
│  └─ variables
│     ├─ VariableRegistry.java
│     └─ VariableSerializer.java
├─ dialogue
│  ├─ DialogueRuntime.java
│  ├─ DialogueEventHandler.java
│  ├─ context
│  ├─ payload
│  ├─ presenter
│  │  ├─ DialoguePresenter.java
│  │  ├─ PacketDialoguePresenter.java
│  │  └─ ChatDialoguePresenter.java
│  ├─ session
│  └─ text
├─ behavior
│  └─ BehaviorTreeRuntime.java
└─ service
   └─ GraphEngineServices.java
```

这个目录故意没有列出大量 `*Service`、`*Store`、`*Snapshot`、`*Invocation`。那些可以后续按需要出现，但不作为第一版重构目标。

## 拆分规则

### 保持内聚的情况

满足这些条件时，不要拆文件：

- 只有一个实现。
- 只被当前模块内部使用。
- 文件名已经能准确表达业务意图。
- 拆出去之后只是多了一层转发。
- 测试可以通过现有公开方法覆盖。

例如：

- `DialogueSessionManager` 可以继续管理 session map，不必先拆成 `DialogueSessionStore`。
- `GraphProcessSerializer` 可以继续作为一个序列化类，不必先拆 snapshot 类。
- `VariableRegistry` 可以继续作为变量编解码入口，不必先改名成 codec registry。

### 必须拆边界的情况

满足这些条件时，才拆新文件或新接口：

- 当前类已经依赖了不该依赖的模块，例如 VM 直接 import network packet。
- 旧方法暴露了内部实现，例如 `GraphExecutionHandle.unwrap()`。
- 一个类同时处理两个生命周期，例如 `DialogueRuntime` 同时处理 external wait 和 packet 展示。
- 编译、运行时、存储混在一起，例如 `RuntimeGraphIndex.build`。
- 迁包后会产生反向依赖，例如 `graph/storage` 反向 import `blueprint/runtime`。
- 需要单独测试且当前类无法隔离。

## 模块重构顺序

下面顺序从最简单到最难。关键调整是：先抽编译，再切热重载回调，然后才迁 storage。否则 `graph/storage` 会继续依赖 blueprint runtime，只是换了包名。

### 1. 蓝图编译模块

排序理由：这是后续 storage 迁包的前置条件。`GraphResourceManager` 和 `DynamicGraphManager` 现在都直接调用 `RuntimeGraphIndex.build`，如果不先抽 compiler，storage 迁到 `graph/storage` 后仍然会知道蓝图运行时索引。

当前文件：

- `blueprint/execution/RuntimeGraphIndex.java`
- `blueprint/execution/GraphFlattener.java`
- `blueprint/execution/storage/GraphResourceManager.java`
- `blueprint/execution/storage/DynamicGraphManager.java`

目标目录：

```text
core/engine/blueprint/compile
├─ BlueprintCompiler.java
└─ GraphFlattener.java

core/engine/blueprint/runtime
└─ RuntimeGraphIndex.java
```

说明：

- `BlueprintCompiler` 是本模块唯一新增核心类。
- `RuntimeGraphIndex` 只做运行时只读索引和查询。
- 校验逻辑先放在 `BlueprintCompiler` 私有方法里，不急着拆 `Validator` 文件。
- JSON 默认值解析也先放在 compiler 内部，不急着拆 `JsonValues` 文件。
- `GraphResourceManager` 和 `DynamicGraphManager` 这一步可以仍在旧包，但改成调用 `BlueprintCompiler.compile(reader)`。

旧接口处理：

- `RuntimeGraphIndex.build(Reader)` 保留 deprecated shim，内部调用 `BlueprintCompiler.compile(reader)`。
- 等 storage 迁包完成后，再删除 shim。

验证点：

- 普通图编译。
- `node_group` 展开。
- 数据流连接索引。
- 执行流连接索引。
- `receive_blueprint` 频率索引。
- 数据流环检测。

### 2. 动态图热重载解耦模块

排序理由：这是 storage 迁包的第二个前置条件。`DynamicGraphManager` 当前直接调用 `GraphEngine.refreshGraphSubscriptions`，如果直接搬到 `graph/storage`，通用 storage 会反向依赖蓝图事件/运行时。

当前文件：

- `blueprint/execution/storage/DynamicGraphManager.java`
- `blueprint/execution/GraphEngine.java`

目标目录：

```text
core/engine/blueprint/runtime
└─ GraphEngine.java

core/engine/blueprint/execution/storage
└─ DynamicGraphManager.java
```

说明：

- 本模块先不迁包，只切依赖。
- `DynamicGraphManager` 增加一个很小的热重载监听入口，例如 `setReloadListener(...)` 或 `addReloadListener(...)`。
- 监听参数保持具体，不要过度抽象：`server`、`graphId`、`oldIndex`、`newIndex` 足够。
- `GraphEngine` 或 blueprint bootstrap 注册监听器，并在回调里调用 `refreshGraphSubscriptions`。
- 如果不想引入 listener，也可以让 `saveAndHotReload` 返回 reload result，由调用方负责通知。核心是 `DynamicGraphManager` 不直接 import `GraphEngine`。

旧接口处理：

- `DynamicGraphManager.saveAndHotReload` 可以保持原方法签名，内部触发 listener。
- 不新增大型 event bus。

验证点：

- 保存动态图后缓存更新。
- 保存动态图后订阅刷新仍发生。
- `DynamicGraphManager` 不再 import `GraphEngine`。

### 3. 图文件和远程文件迁包模块

排序理由：前两个依赖切开后，再把 storage 从 blueprint 移到 graph 才是真的归位。

当前文件：

- `blueprint/execution/storage/GraphIdMapper.java`
- `blueprint/execution/storage/GraphResourceManager.java`
- `blueprint/execution/storage/DynamicGraphManager.java`
- `blueprint/execution/storage/RemoteGraphFileService.java`
- `blueprint/execution/storage/RemoteGraphPermissions.java`
- `core/network/packet/*RemoteGraph*`
- client asset browser 中引用 `RemoteGraphFileService.Entry/Conflict/UploadFile` 的位置

目标目录：

```text
core/engine/graph/storage
├─ GraphPathMapper.java
├─ GraphResourceManager.java
├─ DynamicGraphManager.java
├─ RemoteGraphFileService.java
├─ RemoteGraphPermissions.java
├─ RemoteGraphEntry.java
├─ RemoteGraphConflict.java
└─ RemoteGraphUploadFile.java
```

说明：

- `GraphIdMapper` 改名为 `GraphPathMapper`，因为它实际处理 graph id、路径、后缀和安全校验。
- `GraphResourceManager` 和 `DynamicGraphManager` 先不拆 repository 接口，直接移到 `graph/storage`。
- `RemoteGraphFileService` 继续是一个完整文件服务，不先拆成一堆 service。
- `RemoteGraphFileService.Entry/Conflict/UploadFile` 不再作为嵌套 record 存在，改为 `RemoteGraphEntry`、`RemoteGraphConflict`、`RemoteGraphUploadFile` 三个顶层 DTO。
- packet、network、client UI 同一轮全量改到顶层 DTO，不保留旧包 wrapper。
- 不推荐写一个旧包 `RemoteGraphFileService` wrapper 来假装无风险迁移 nested records。Java 嵌套类型不能做真正的透明别名，最后仍然要改大量类型引用。

旧接口处理：

- `blueprint.execution.storage.GraphIdMapper` 删除，调用方统一使用 `graph.storage.GraphPathMapper`。
- `blueprint.execution.storage.RemoteGraphFileService` 删除，调用方统一使用 `graph.storage.RemoteGraphFileService` 和顶层 DTO。
- 新代码不再 import `blueprint.execution.storage.*`。

验证点：

- datapack 图能加载。
- 世界目录动态图能保存和热重载。
- 远程列表、上传、下载、移动、删除行为不变。
- packet 编解码不变。
- client asset browser 上传/下载不回归。
- 路径穿越防护仍有效。

### 4. 对话模块

排序理由：对话是一个独立功能，但不需要拆成很多 service。优先消除 `handle.unwrap()`，这是明确的跨 runtime 抽象泄漏。

当前文件：

- `dialogue/DialogueRuntime.java`
- `dialogue/DialogueEventHandler.java`
- `dialogue/context/DialogueContext.java`
- `dialogue/payload/*`
- `dialogue/presenter/ChatDialoguePresenter.java`
- `dialogue/session/*`
- `dialogue/text/DialogueTextManager.java`

目标目录：

```text
core/engine/dialogue
├─ DialogueRuntime.java
├─ DialogueEventHandler.java
├─ context
│  └─ DialogueContext.java
├─ payload
│  ├─ DialogueWaitRequest.java
│  ├─ DialoguePagePayload.java
│  └─ DialogueChoicePayload.java
├─ presenter
│  ├─ DialoguePresenter.java
│  ├─ PacketDialoguePresenter.java
│  └─ ChatDialoguePresenter.java
├─ session
│  ├─ DialogueSession.java
│  ├─ DialogueSessionManager.java
│  ├─ DialogueSessionPolicy.java
│  └─ DialogueCloseReason.java
└─ text
   └─ DialogueTextManager.java
```

说明：

- 保留 `DialogueRuntime` 作为对话总入口，名字直观，不强拆 `DialogueSessionService`。
- 新增 `DialoguePresenter` 是必要的，因为展示确实有 packet UI 和聊天 UI 两种实现。
- `DefaultDialogueRenderer` 改成 `ChatDialoguePresenter`，名字更接近职责。
- `DialogueWaitRequest` 应直接携带 `DialogueContext`，不要再让 runtime 通过 `handle.unwrap()` 去蓝图线程里找临时数据。
- `DialogueGraphLauncher` 当前没有真正接入，本模块直接删除，不保留 unused 包。

旧接口处理：

- `GraphExecutionHandle.unwrap()` 在对话模块迁完后不应再被对话使用。
- `DialogueRuntime.INSTANCE.getTextManager()` 可以短期保留，节点迁移完再收窄。
- `DialogueCloseReason` 先可保持字符串常量，enum 不是本模块第一优先级。

验证点：

- 显示对话页。
- 选择选项后恢复对应端口。
- 关闭、退出、死亡、距离过远恢复 `closed`。
- default 聊天样式和 packet 样式都走 presenter。
- busy 占用策略不变。

### 5. 蓝图绑定、附件和持久化模块

排序理由：这些都是“图绑在哪里、进程存在哪里、变量怎么保存”的问题，放在一起比拆成三层更好理解。

当前文件：

- `blueprint/execution/attachment/GraphContainer.java`
- `blueprint/execution/attachment/EntityGraphAttachment.java`
- `blueprint/execution/attachment/LevelGraphAttachment.java`
- `blueprint/execution/attachment/EntityImmunityAttachment.java`
- `blueprint/execution/storage/GlobalGraphStorage.java`
- `blueprint/execution/GraphProcessSerializer.java`
- `blueprint/execution/variables/*`

目标目录：

```text
core/engine/blueprint/attachment
├─ GraphContainer.java
├─ EntityGraphAttachment.java
├─ LevelGraphAttachment.java
├─ EntityImmunityAttachment.java
└─ GlobalGraphStorage.java

core/engine/blueprint/runtime
└─ GraphProcessSerializer.java

core/engine/blueprint/variables
├─ VariableRegistry.java
└─ VariableSerializer.java
```

说明：

- `GraphContainer` 名字虽然偏通用，但当前读起来清楚。可以先保留，文档标注它目前只装蓝图进程。
- `EntityGraphAttachment`、`LevelGraphAttachment` 继续放在 blueprint 下，避免伪装成通用 graph attachment。
- `GlobalGraphStorage` 建议并入 attachment/binding 语义，或者保留到 `blueprint/attachment` 附近，不再放 storage。
- `GraphProcessSerializer` 先不拆 snapshot 文件，等 VM 拆分时再考虑。
- `VariableRegistry` 保持单入口，只修正支持类型和实际序列化不一致的问题。

旧接口处理：

- 不急着创建 `BlueprintProcessStore`、`BlueprintProcessSnapshot`。
- 如果后续行为树也需要绑定和存档，再抽通用 `GraphInstanceStore`。

验证点：

- 实体绑定图。
- 全局绑定图。
- 进程保存和恢复。
- 变量保存和恢复。
- wait 线程读档后的 tick 基准不变。

### 6. 蓝图事件模块

排序理由：事件派发影响面大，但文件名本身清晰。目标是减少 dispatcher 对 VM 内部线程的依赖，而不是拆一堆路由对象。

当前文件：

- `blueprint/execution/event_handler/GraphEventHandler.java`
- `blueprint/execution/event_handler/dispatcher/*`
- `blueprint/execution/state/PlayerInputStateManager.java`
- `blueprint/execution/GraphEngine.java`

目标目录：

```text
core/engine/blueprint/event
├─ GraphEventHandler.java
├─ PlayerInputStateManager.java
└─ dispatcher
   ├─ BlockDispatcher.java
   ├─ EntityDispatcher.java
   ├─ PlayerDispatcher.java
   └─ WorldDispatcher.java
```

说明：

- `GraphEventHandler` 名字保留，它就是事件入口和 tick 入口。
- dispatcher 文件保留，不拆 `Invocation`、`Payload` 等小文件。
- 新增一个轻量参数对象可以接受，例如 `GraphEventData`，但只有当 dispatcher 初始化参数继续膨胀时再加。
- `GraphEngine.dispatchEvent(... Consumer<GraphProcess.ExecutionThread>)` 是旧泄漏点，应逐步改成传 event data map 或专用 event data 对象。

旧接口处理：

- `GraphEngine.dispatchEvent` 的 `ExecutionThread initializer` 保留 deprecated 过渡。
- 外部 API 继续推荐 `GeometryNodeEvents`，不暴露 VM 内部线程。
- `OnEntityTick` 的特殊路径后续并入普通事件派发，或者明确保留为性能 fast path。

验证点：

- 实体事件、玩家事件、方块事件、世界事件派发。
- 全局图和实体绑定图都能收到事件。
- `receive_blueprint` 订阅刷新。
- 玩家按键状态不回归。

### 7. 蓝图运行时模块

排序理由：这是核心 VM，最后动。目标不是马上把 `GraphProcess` 拆成很多文件，而是先让它不再承担外部模块职责。

当前文件：

- `blueprint/execution/GraphEngine.java`
- `blueprint/execution/GraphProcess.java`
- `blueprint/execution/ExecutionContext.java`
- `blueprint/execution/ExecutionResult.java`
- `blueprint/execution/RuntimeGraphIndex.java`

目标目录：

```text
core/engine/blueprint/runtime
├─ GraphEngine.java
├─ GraphProcess.java
├─ RuntimeGraphIndex.java
├─ ExecutionContext.java
├─ ExecutionResult.java
└─ GraphProcessSerializer.java
```

说明：

- `GraphEngine` 保留，因为它是很好理解的蓝图运行时门面。但它不应该继续直接管文件仓库和事件订阅细节。
- `GraphProcess` 先保留内部 `ExecutionThread`。只有当持久化、对话、事件都不再引用内部线程时，再考虑拆成顶层 `GraphExecutionThread`。
- `ExecutionContext` 先保留大接口，因为现有节点大量依赖它。后续可以在同文件或子接口中收窄能力，但不作为第一阶段目标。
- `ExecutionResult` 保持蓝图专属，不上移到通用 graph。
- 视觉广播改走 `GraphEngineServices`，避免 VM 直接 import network。

旧接口处理：

- `GraphProcess.ExecutionThread` 不再作为新代码参数类型。
- `ExecutionContext` 不马上删除，但新增节点应少用大而全能力。
- `GraphEngine.getGraphIndex` 后续应委托 `graph/storage`，不要自己知道 dynamic/datapack 查找细节。

验证点：

- next/call/finish/error。
- wait tick 唤醒。
- external wait resume/close。
- 数据流 pull model。
- frame cache。
- `executeBranchSync`。
- 最大步数保护。

### 8. 通用 runtime 和服务端口模块

排序理由：这是跨模块收口，应在蓝图和对话边界稳定后做。太早做会产生过多空接口。

当前文件：

- `graph/runtime/*`
- `service/GraphEngineServices.java`
- `BlueprintRuntime.java`
- `DialogueRuntime.java`
- `BehaviorTreeRuntime.java`

目标目录：

```text
core/engine/graph/runtime
├─ GraphRuntime.java
├─ GraphRuntimeRegistry.java
├─ GraphRuntimeContext.java
├─ GraphExecutionHandle.java
└─ ExternalWaitRequest.java

core/engine/service
└─ GraphEngineServices.java
```

说明：

- `GraphRuntime` 先保持薄接口，不急着加 compile/createInstance/tick/saveLoad 全套方法。
- `GraphEngineServices` 可以先作为一个聚合服务类，内部放 `VisualSink`、持久属性访问等嵌套接口，不必每个端口一个文件。
- `GraphExecutionHandle.unwrap()` 是明确要删的旧口子，但要等对话、事件、持久化迁移完再删。

旧接口处理：

- `GraphRuntime.init()` 无参版本可以继续保留，直到 services 真正接入。
- `BlueprintRuntime.INSTANCE`、`DialogueRuntime.INSTANCE` 可以继续作为 facade，重点是内部不再互相强转。

验证点：

- runtime 注册。
- external wait 查找。
- 对话恢复蓝图执行。
- VM 视觉广播不直接依赖 network。

### 9. 行为树最小占位

排序理由：行为树不深究，只确保不被蓝图接口污染。

当前文件：

- `behavior/BehaviorTreeRuntime.java`

目标目录：

```text
core/engine/behavior
└─ BehaviorTreeRuntime.java
```

说明：

- 先保留当前 `behavior` 包名，不急着改 `behavior_tree`。
- 不复用蓝图 `ExecutionResult`。
- 不复用蓝图 `ExecutionContext`。
- 不设计 selector、sequence、decorator。
- 不接 UI。

## 模块排序汇总

| 顺序 | 模块 | 难度 | 文件数量策略 |
| --- | --- | --- | --- |
| 1 | 蓝图编译 | 低到中 | 只新增 `BlueprintCompiler`，先让 build 变 shim。 |
| 2 | 动态图热重载解耦 | 低到中 | 只加小回调或返回结果，不上 event bus。 |
| 3 | 图文件和远程文件迁包 | 中 | 先切依赖再迁包；nested DTO 要单独处理。 |
| 4 | 对话 | 中 | 只新增 presenter 边界，优先删除 `unwrap()` 依赖。 |
| 5 | 蓝图绑定、附件和持久化 | 中 | 保留现有清晰类名，不急着抽 store/snapshot。 |
| 6 | 蓝图事件 | 中到高 | 保留 handler/dispatcher 结构，减少 VM 线程泄漏。 |
| 7 | 蓝图运行时 | 高 | 保留 `GraphEngine`、`GraphProcess`，先移除外部职责。 |
| 8 | 通用 runtime 和服务端口 | 高 | 保持薄接口，服务端口先聚合在一个文件。 |
| 9 | 行为树最小占位 | 低 | 只保留边界，不深挖。 |

## 旧接口删除清单

| 旧接口/类 | 处理方式 |
| --- | --- |
| `RuntimeGraphIndex.build(Reader)` | 先改为调用 `BlueprintCompiler`，storage 迁完后删除。 |
| `DynamicGraphManager -> GraphEngine.refreshGraphSubscriptions` | 改为 listener/callback 或 save result，由 blueprint runtime 侧处理订阅刷新。 |
| `blueprint.execution.storage.GraphIdMapper` | 删除；调用方统一改为 `graph.storage.GraphPathMapper`。 |
| `RemoteGraphFileService.Entry/Conflict/UploadFile` | 删除；改为 `RemoteGraphEntry`、`RemoteGraphConflict`、`RemoteGraphUploadFile` 顶层 DTO。 |
| `blueprint.execution.storage.RemoteGraphFileService` | 删除；不做旧包 wrapper。 |
| `GraphExecutionHandle.unwrap()` | 对话不再使用后删除。 |
| `GraphEngine.dispatchEvent(... ExecutionThread initializer)` | dispatcher 迁到 event data 后删除或改 private。 |
| `GraphProcess.ExecutionThread` 作为外部参数 | 禁止新增使用，等事件/对话/存档不依赖后再拆或收回可见性。 |
| `DialogueGraphLauncher` | 删除；当前未接入，不保留 unused/future。 |

## 推荐第一步

先做模块 1：

1. 新建 `blueprint/compile/BlueprintCompiler`。
2. 把 `RuntimeGraphIndex.build(Reader)` 里的解析、flatten、校验和索引构建迁到 `BlueprintCompiler.compile(reader)`。
3. `RuntimeGraphIndex.build(Reader)` 保留 deprecated shim。
4. `GraphResourceManager` 和 `DynamicGraphManager` 暂时仍可调用 shim，或直接改用 compiler。

然后做模块 2：

1. 去掉 `DynamicGraphManager` 对 `GraphEngine` 的直接 import。
2. 用小 listener/callback 或 save result 把“动态图已热重载”通知给蓝图 runtime。
3. 确认订阅刷新行为不变。

完成这两步后，再迁 `graph/storage`。这样迁包时不会把蓝图 VM 依赖带进通用 storage。
