# Geometry Node 后端节点 API 标准化

本文档记录当前第一版后端扩展 API 的目标、已落地接口和后续边界。

本轮标准化覆盖：

- 节点注册 API
- 事件定义 API
- 事件派发 API
- 插件来源与命名空间规范

本轮暂不覆盖：

- 自定义端口类型 API
- 自定义节点 UI
- 自定义行内控件
- 图迁移/alias 系统
- 完整 MissingNode 占位

## 目标架构

```text
geometry_node
  api/
    GeometryNodePlugin
    NodeRegistrationContext
    EventRegistrationContext
    EventDef
    EventScope
    EventPayload
    GeometryEventDispatcher
    GeometryNodeEvents

  core/node/
    NodeRegistry
    BuiltinNodesPlugin
    BaseNode / NodeDef / NodeType
    PortDef / PortRow / PortType / StandardPorts / UIHint

  core/execution/
    ExecutionContext
    ExecutionResult
    GraphEngine
    GraphProcess

addon
  - 实现 GeometryNodePlugin
  - registerNodes(ctx) 注册 BaseNode
  - registerEvents(ctx) 声明 EventDef
  - 用 GeometryNodeEvents 派发事件
```

核心原则：

```text
Addon 只依赖 api 和节点定义/执行契约。
Addon 不直接接触 GraphProcess.ExecutionThread。
Addon 不直接操作 RuntimeGraphIndex / GraphFlattener。
```

## 插件入口

标准插件接口：

```java
public interface GeometryNodePlugin {
    default String addonId() {
        return getClass().getName();
    }

    default void registerNodes(NodeRegistrationContext registry) {
    }

    default void registerEvents(EventRegistrationContext registry) {
    }

    @Deprecated
    default void registerNodes(NodeRegistry registry) {
    }
}
```

说明：

- 新 Addon 使用 `registerNodes(NodeRegistrationContext)`。
- 旧 Addon 仍可使用 `registerNodes(NodeRegistry)`。
- `addonId()` 应返回 Mod ID，例如 `create_geometry`。
- 内置插件 `BuiltinNodesPlugin` 返回 `geometry_node`。

`NodeRegistry.init()` 现在负责：

- 通过 `ServiceLoader<GeometryNodePlugin>` 加载插件。
- 幂等初始化，避免重复注册。
- 隔离坏 provider，避免一个坏 Addon 阻断后续 Addon。
- 记录插件来源。
- 先调用新节点注册入口；如果没有注册节点，再回退旧入口。
- 调用事件注册入口。

## 节点注册 API

标准上下文：

```java
public interface NodeRegistrationContext {
    String addonId();

    void registerNode(String menuPath, BaseNode node);

    default void register(String menuPath, BaseNode node) {
        registerNode(menuPath, node);
    }
}
```

Addon 示例：

```java
public final class ExampleGeometryNodePlugin implements GeometryNodePlugin {
    @Override
    public String addonId() {
        return "example_geometry";
    }

    @Override
    public void registerNodes(NodeRegistrationContext registry) {
        registry.register("actions/example", new ExampleActionNode());
        registry.register("data/example", new ExampleDataNode());
    }
}
```

服务声明：

```text
src/main/resources/META-INF/services/com.mine.geometry_node.api.GeometryNodePlugin
```

内容：

```text
com.example.example_geometry.ExampleGeometryNodePlugin
```

## 节点 ID 规范

第三方节点必须使用命名空间 ID：

```java
public static final String TYPE_ID = "example_geometry:example_action";
```

不推荐：

```java
public static final String TYPE_ID = "example_action";
```

原因：

- `typeId` 会写入图 JSON 的 `node_type`。
- 多个 Addon 容易发生裸 ID 冲突。
- 改 ID 会导致旧图找不到节点。

当前策略：

- 内置 `geometry_node` 节点继续允许裸 ID。
- 第三方 Addon 裸 ID 会打印 warning。
- 暂不强制 reject，避免开发期过于僵硬。

## 节点编写契约

节点仍然继承 `BaseNode`。

动作节点：

```java
public final class ExampleActionNode extends BaseNode {
    public static final String TYPE_ID = "example_geometry:example_action";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("example_geometry.node.example_action"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.MESSAGE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String message = getInput(context, StandardPorts.MESSAGE.getId(), String.class);
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
```

数据节点：

```java
public final class ExampleDataNode extends BaseNode {
    public static final String TYPE_ID = "example_geometry:example_data";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("example_geometry.node.example_data"))
                .addRow(new PortRow(null, StandardPorts.STRING.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.STRING.getId().equals(portName)) {
            return null;
        }
        return "hello";
    }
}
```

规则：

- `getDefaultDefinition()` 必须稳定、非空、无副作用。
- `BaseNode` 实例是共享对象，不要在字段里保存运行时状态。
- 输入读取优先用 `getInput(...)`、`getInputList(...)`、`getInputDict(...)`。
- 端口 ID 是存档协议，发布后不要随意改。
- 动态端口可以继续使用 `getDefinition(NodeData)`，但后续会考虑只读 `NodeInstanceView`。

## 事件 API

事件系统分为两部分：

```text
事件定义：EventDef
事件派发：GeometryNodeEvents / GeometryEventDispatcher
```

### EventDef

```java
public record EventDef(
        String eventId,
        Component displayName,
        EventScope scope,
        List<PortDef> outputs
) {}
```

`eventId` 必须和事件节点 `TYPE_ID` 一致。

事件作用域：

```java
public enum EventScope {
    GLOBAL,
    LEVEL,
    ENTITY
}
```

事件节点注册时，如果 `NodeDef.category() == NodeType.EVENT`，`NodeRegistry` 会自动生成一份默认 `EventDef`。

Addon 可以在 `registerEvents(...)` 中覆盖同一个 Addon 自己的事件定义，用于补充更准确的 scope 或输出 schema。

### EventRegistrationContext

```java
public interface EventRegistrationContext {
    String addonId();

    GeometryEventDispatcher dispatcher();

    void registerEvent(EventDef eventDef);
}
```

示例：

```java
@Override
public void registerEvents(EventRegistrationContext registry) {
    registry.registerEvent(new EventDef(
            "example_geometry:on_machine_tick",
            Component.translatable("example_geometry.event.on_machine_tick"),
            EventScope.LEVEL,
            List.of(
                    StandardPorts.ENTITY.toOutput(),
                    StandardPorts.VALUE.toOutput()
            )
    ));
}
```

## 事件节点编写

简单事件节点继承 `BaseEventNode`：

```java
public final class OnMachineTick extends BaseEventNode {
    public static final String TYPE_ID = "example_geometry:on_machine_tick";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("example_geometry.node.on_machine_tick"))
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }
}
```

`BaseEventNode.compute(...)` 会默认从 `ExecutionContext.getEventData(portName)` 读取 payload。

如果事件节点需要过滤条件，可以像 `OnPlayerKeyEvent` 一样自定义 `execute(...)` 和 `compute(...)`，但节点类型仍应是 `NodeType.EVENT`。

## 事件派发 API

Addon 不应直接调用 `GraphEngine.dispatchEvent(... Consumer<ExecutionThread>)`。

标准入口：

```java
GeometryNodeEvents.dispatch(level, target, "example_geometry:on_machine_tick",
        EventPayload.builder()
                .put(StandardPorts.ENTITY.getId(), entity)
                .put(StandardPorts.VALUE.getId(), value)
                .build()
);
```

也可以拿 dispatcher：

```java
GeometryEventDispatcher dispatcher = GeometryNodeEvents.dispatcher();
dispatcher.dispatch(level, target, eventId, payload);
```

`EventPayload`：

```java
EventPayload.builder()
        .put("port_id", value)
        .build();
```

约束：

- payload key 必须和事件节点输出端口 ID 一致。
- 第一版事件只负责触发蓝图，不负责取消或修改原 NeoForge 事件。
- payload 值应尽量使用 `VariableRegistry` 已支持的类型，例如数字、字符串、布尔、Entity、Vec3、ItemStack、BlockState、List、Map。

## 内部事件入口状态

`GraphEngine.dispatchEvent(... Consumer<GraphProcess.ExecutionThread>)` 已标记为 deprecated。

保留原因：

- 内置 dispatcher 当前仍大量使用它。
- 直接全部迁移会扩大改动面。

对外规则：

- Addon 使用 `GeometryNodeEvents`。
- 后续核心内部 dispatcher 可以逐步迁移到 `GeometryNodeEvents`。
- 最终再考虑收紧 `GraphEngine` 的 public API。

当前已迁移示例：

- `PlayerInputStateManager` 的玩家按键事件已经改用 `GeometryNodeEvents`。
- `OnPlayerKeyEvent` 已调整为 `NodeType.EVENT`。

## 端口类型策略

本轮不实现自定义端口类型 API。

原因：

- 当前 `PortType` 是 enum。
- 连线兼容逻辑在 `PortType.isCompatible(...)`。
- 类型转换逻辑在 `TypeConverter.convert(...)`。
- UI 颜色、默认值、输入控件都依赖现有 enum。

第一版 Addon 应使用现有类型：

- `EXECUTION`
- `INTEGER`
- `FLOAT`
- `BOOLEAN`
- `STRING`
- `ENTITY`
- `ITEM`
- `ITEM_STACK`
- `BLOCK`
- `XYZ`
- `LIST`
- `DICT`
- `ANY`

复杂对象建议先用：

- `STRING` 存 registry id。
- `DICT` 存结构化字段。
- `ANY` 做同一次执行链内的临时对象。

后续如果要做自定义端口类型，应单独规划：

- `PortDataType`
- `TypeCompatibilityRegistry`
- `TypeConverterRegistry`
- 类型值 codec
- 图协议迁移

## 当前实现清单

新增 API：

- `api/NodeRegistrationContext.java`
- `api/EventRegistrationContext.java`
- `api/EventDef.java`
- `api/EventScope.java`
- `api/EventPayload.java`
- `api/GeometryEventDispatcher.java`
- `api/GeometryNodeEvents.java`

修改：

- `api/GeometryNodePlugin.java`
  - 新增 `addonId()`
  - 新增标准 `registerNodes(NodeRegistrationContext)`
  - 新增 `registerEvents(EventRegistrationContext)`
  - 保留旧 `registerNodes(NodeRegistry)`

- `core/node/NodeRegistry.java`
  - `init()` 幂等
  - ServiceLoader provider 错误隔离
  - 插件来源追踪
  - 新旧注册入口兼容
  - 节点命名空间 warning
  - 事件定义注册表
  - `NodeType.EVENT` 自动注册默认 `EventDef`

- `core/node/BuiltinNodesPlugin.java`
  - 使用新 `NodeRegistrationContext`
  - `addonId()` 返回 `geometry_node`

- `core/execution/GraphEngine.java`
  - 旧 thread-consumer 事件入口标记 deprecated

- `core/execution/state/PlayerInputStateManager.java`
  - 改用 `GeometryNodeEvents`

- `core/node/nodes/events/player/OnPlayerKeyEvent.java`
  - 改为 `NodeType.EVENT`

## 验收标准

应验证：

1. 内置节点仍能注册。
2. 旧 `GeometryNodePlugin.registerNodes(NodeRegistry)` 写法仍能注册节点。
3. 新 `registerNodes(NodeRegistrationContext)` 写法能注册节点。
4. 第三方裸 ID 会 warning，但不阻断。
5. `NodeType.EVENT` 节点会进入事件定义表。
6. Addon 可通过 `GeometryNodeEvents.dispatch(...)` 派发事件。
7. Addon 不需要引用 `GraphProcess.ExecutionThread`。
8. 玩家按键事件仍能触发 `OnPlayerKeyEvent`。

## 后续建议

下一步优先级：

1. 写一个最小 example addon。
2. 把一两个内置 dispatcher 迁到 `GeometryNodeEvents`，验证 facade 适合内部使用。
3. 给未知节点做最小不崩溃处理。
4. 后续再单独设计自定义端口类型。
