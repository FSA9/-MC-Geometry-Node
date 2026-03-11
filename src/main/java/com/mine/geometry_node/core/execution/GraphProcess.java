package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.GraphDataAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * [核心执行单元] 图的运行实例 (The "Process")。
 * <p>
 * 代表一次独立的蓝图执行流程，充当微型虚拟机 (VM) 的角色。
 * 负责维护执行指令栈、变量作用域、协程调度(延迟任务)以及瞬时环境上下文。
 */
public class GraphProcess {

    // ====================================================
    // 数据结构定义 (Data Structures)
    // ====================================================

    public enum State {
        RUNNING,    // 活跃状态：正在执行或准备执行
        WAITING,    // 挂起状态：等待协程任务(Delay)唤醒
        FINISHED    // 终止状态：流程彻底结束，等待引擎回收
    }

    // ====================================================
    // 成员变量 (Fields)
    // ====================================================

    // 基础配置
    private final String graphId;
    private final RuntimeGraphIndex index;
    private final InnerContext context; // 外观模式：暴露给节点使用的受限 API

    /** 描述一个被挂起的延迟任务 (注意 resumeNodeId 变成了 int) */
    private record ScheduledTask(long wakeUpTick, int resumeNodeId) {}

    /** 用于取代原来的 String 拼接 cacheKey */
    private record CacheKey(int nodeId, String port) {}

    // 运行时状态 (改用 -1 代替 null)
    private State state = State.RUNNING;
    private int currentFlowId = -1;       // 当前待执行节点 ID
    private int activeNodeId = -1;        // 当前正在计算/执行的节点 ID

    // 任务调度
    private final List<ScheduledTask> sleepingTasks = new ArrayList<>(); // 等待唤醒的协程队列
    private boolean needsTimeRebase = false;                             // 读档标记：指示是否需要将相对时间转换为绝对世界时间

    // 内存与作用域
    private final Deque<Integer> executionStack = new LinkedList<>();             // 指令执行栈变成了 Integer
    private final Deque<Map<String, Object>> variableStack = new LinkedList<>();
    private final Map<String, Object> eventData = new HashMap<>();

    // 帧级缓存
    private final Map<CacheKey, Object> frameCache = new HashMap<>();             // 使用对象作为Key，消除拼接开销
    private final Set<Integer> recursionGuard = new HashSet<>();

    // 外部环境
    private ServerLevel level;
    private Entity entity; // 挂载该图的宿主实体


    // ====================================================
    // 构造与初始化 (Constructors)
    // ====================================================

    /**
     * 创建并初始化一个新的执行进程。
     */
    public GraphProcess(String graphId, RuntimeGraphIndex index, int startNodeId) {
        this.graphId = graphId;
        this.index = index;
        this.currentFlowId = startNodeId;
        this.context = new InnerContext();
        this.variableStack.push(new HashMap<>());
    }

    /**
     * [断点续传] 从 NBT 存档中反序列化恢复执行进程。
     */
    public GraphProcess(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        this.index = index;
        this.context = new InnerContext();

        this.graphId = tag.getString("GraphId");
        // 读档时：String -> int，如果图更新了找不到该节点，赋予 -1 结束进程
        this.currentFlowId = tag.contains("NodeId") ? index.getStringToId(tag.getString("NodeId")) : -1;
        this.state = State.valueOf(tag.getString("State"));

        this.sleepingTasks.clear();
        if (tag.contains("SleepingTasks", Tag.TAG_LIST)) {
            ListTag tasksTag = tag.getList("SleepingTasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < tasksTag.size(); i++) {
                CompoundTag taskTag = tasksTag.getCompound(i);
                int resumeId = index.getStringToId(taskTag.getString("ResumeNodeId"));
                if (resumeId != -1) {
                    this.sleepingTasks.add(new ScheduledTask(taskTag.getLong("WaitRemaining"), resumeId));
                }
            }
            this.needsTimeRebase = true;
        }

        // 3. 恢复事件数据沙箱
        this.eventData.clear();
        if (tag.contains("EventData", Tag.TAG_COMPOUND)) {
            CompoundTag eventTag = tag.getCompound("EventData");
            for (String key : eventTag.getAllKeys()) {
                // 修改点 1：传入 provider
                Object deserialized = VariableRegistry.fromTag(eventTag.get(key), provider);
                if (deserialized != null) {
                    this.eventData.put(key, deserialized);
                }
            }
        }

        // 4. 恢复变量栈
        this.variableStack.clear();
        if (tag.contains("VariableStack", Tag.TAG_LIST)) {
            ListTag stackTag = tag.getList("VariableStack", Tag.TAG_COMPOUND);
            for (int i = 0; i < stackTag.size(); i++) {
                Map<String, Object> scope = new HashMap<>();
                // 修改点 2：传入 provider
                loadVariables(stackTag.getCompound(i), scope, provider);
                this.variableStack.addLast(scope);
            }
        } else {
            this.variableStack.push(new HashMap<>());
        }

        // 5. 恢复执行指令栈
        this.executionStack.clear();
        if (tag.contains("ExecutionStack", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ExecutionStack", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                int stackId = index.getStringToId(list.getString(i));
                if (stackId != -1) this.executionStack.addLast(stackId);
            }
        }
    }


    // ====================================================
    // 生命周期与驱动 API (Lifecycle & Public API)
    // ====================================================

    public void setEnvironment(ServerLevel level, @Nullable Entity entity) {
        this.level = level;
        this.entity = entity;
    }

    public void setEventData(String key, Object value) {
        this.eventData.put(key, value);
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    public String getGraphId() {
        return graphId;
    }

    /**
     * [核心驱动马达] 游戏主循环每 Tick 调用一次。
     * 负责处理时间轴对齐、唤醒到期协程，并驱动逻辑主循环。
     */
    public void tick(long currentWorldTick) {
        // 0. 前置合法性检查
        if (state == State.FINISHED || level == null) return;

        // 1. 清理瞬时缓存 (每 Tick 重置)
        frameCache.clear();
        recursionGuard.clear();

        // 2. 读档后首帧处理：时间轴校准 (相对时间 -> 绝对世界时间)
        if (this.needsTimeRebase) {
            for (int i = 0; i < sleepingTasks.size(); i++) {
                ScheduledTask oldTask = sleepingTasks.get(i);
                sleepingTasks.set(i, new ScheduledTask(currentWorldTick + oldTask.wakeUpTick(), oldTask.resumeNodeId()));
            }
            this.needsTimeRebase = false;
        }

        // 3. 任务调度：唤醒到期的任务并压入执行栈
        Iterator<ScheduledTask> it = sleepingTasks.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (currentWorldTick >= task.wakeUpTick()) {
                if (task.resumeNodeId() != -1) {
                    this.executionStack.addLast(task.resumeNodeId());
                }
                it.remove();
            }
        }

        if (currentFlowId != -1 || !executionStack.isEmpty()) {
            state = State.RUNNING;
            runExecutionLoop();
        } else if (sleepingTasks.isEmpty()) {
            // 既无运行指令，也无挂起任务 -> 寿终正寝
            state = State.FINISHED;
        } else {
            // 无运行指令，但仍有任务沉睡 -> 保持挂起
            state = State.WAITING;
        }
    }

    // ====================================================
    // 核心执行引擎
    // ====================================================

    /**
     * 执行控制流逻辑 (Push Model)。
     * 只要指令栈有任务，将持续执行，直至挂起(Delay)或触及单帧执行上限。
     */
    private void runExecutionLoop() {
        int steps = 0;
        final int MAX_STEPS = 1000;

        while ((currentFlowId != -1 || !executionStack.isEmpty()) && state == State.RUNNING) {

            if (currentFlowId == -1) {
                Integer next = executionStack.pollFirst();
                if (next == null) continue;
                currentFlowId = next;
            }

            if (steps++ > MAX_STEPS) return;

            String nodeType = index.getNodeType(currentFlowId);
            if ("unknown".equals(nodeType)) {
                state = State.FINISHED;
                return;
            }

            BaseNode logic = NodeRegistry.INSTANCE.get(nodeType);
            if (logic == null) {
                System.err.println("GraphProcess: Unknown node type " + nodeType);
                state = State.FINISHED;
                return;
            }

            try {
                int previousActive = this.activeNodeId;
                this.activeNodeId = currentFlowId;

                ExecutionResult result = logic.execute(context);

                this.activeNodeId = previousActive;
                handleExecutionResult(result);

            } catch (Exception e) {
                System.err.println("GraphProcess Error at node " + index.getIdToString(currentFlowId) + ": " + e.getMessage());
                e.printStackTrace();
                state = State.FINISHED;
            }
        }

        if (currentFlowId == -1 && executionStack.isEmpty() && sleepingTasks.isEmpty()) {
            state = State.FINISHED;
        }
    }

    /**
     * 解析节点的执行结果，并操纵虚拟机状态机进行响应跳转。
     */
    private void handleExecutionResult(ExecutionResult result) {
        switch (result) {
            case ExecutionResult.Next next -> {
                this.currentFlowId = index.findFlowTarget(currentFlowId, next.outputPortName());
            }
            case ExecutionResult.Call call -> {
                List<String> ports = call.outputPorts();
                for (int i = ports.size() - 1; i >= 0; i--) {
                    String portName = ports.get(i);
                    int targetId = index.findFlowTarget(currentFlowId, portName);
                    if (targetId != -1) {
                        this.executionStack.addFirst(targetId);
                    }
                }
                Integer next = executionStack.pollFirst();
                this.currentFlowId = (next != null) ? next : -1;
            }
            case ExecutionResult.Wait wait -> {
                long wakeTime = level.getGameTime() + wait.ticks();
                int nextId = index.findFlowTarget(currentFlowId, wait.nextPortName());
                if (nextId != -1) {
                    this.sleepingTasks.add(new ScheduledTask(wakeTime, nextId));
                }
                this.currentFlowId = -1;
            }
            case ExecutionResult.Finish ignored -> {
                this.currentFlowId = -1;
            }
            case ExecutionResult.Error err -> {
                System.err.println("Graph Error: " + err.errorMessage());
                this.executionStack.clear();
                this.currentFlowId = -1;
                this.state = State.FINISHED;
            }
        }
    }

    // ====================================================
    // 数据拉取模型
    // ====================================================

    /**
     * 递归向上游节点索要数据 (Pull Model)。
     * 附带了帧级结果缓存与成环依赖检测。
     */
    private Object executeDataNode(int nodeId, String portName) {
        CacheKey cacheKey = new CacheKey(nodeId, portName); // 优雅的缓存命中，0 字符串拼接

        if (frameCache.containsKey(cacheKey)) {
            return frameCache.get(cacheKey);
        }

        if (recursionGuard.contains(nodeId)) {
            System.err.println("GraphProcess: Detected dependency cycle at node " + index.getIdToString(nodeId));
            return null;
        }

        recursionGuard.add(nodeId);
        int previousActiveNodeId = this.activeNodeId;

        try {
            String nodeType = index.getNodeType(nodeId);
            if ("unknown".equals(nodeType)) return null;

            BaseNode logic = NodeRegistry.INSTANCE.get(nodeType);
            if (logic == null) return null;

            this.activeNodeId = nodeId;
            Object result = logic.compute(context, portName);

            frameCache.put(cacheKey, result);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            this.activeNodeId = previousActiveNodeId;
            recursionGuard.remove(nodeId);
        }
    }


    // ====================================================
    // 序列化&反序列化
    // ====================================================

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("GraphId", graphId);
        tag.putString("State", state.name());
        if (currentFlowId != -1) {
            tag.putString("NodeId", index.getIdToString(currentFlowId));
        }

        if (!sleepingTasks.isEmpty()) {
            ListTag tasksTag = new ListTag();
            for (ScheduledTask task : sleepingTasks) {
                CompoundTag taskTag = new CompoundTag();
                long remaining = (level != null && !needsTimeRebase) ?
                        Math.max(0, task.wakeUpTick() - level.getGameTime()) :
                        task.wakeUpTick();

                taskTag.putLong("WaitRemaining", remaining);
                if (task.resumeNodeId() != -1) {
                    taskTag.putString("ResumeNodeId", index.getIdToString(task.resumeNodeId()));
                }
                tasksTag.add(taskTag);
            }
            tag.put("SleepingTasks", tasksTag);
        }

        // 3. 事件数据沙箱
        if (!eventData.isEmpty()) {
            CompoundTag eventTag = new CompoundTag();
            eventData.forEach((key, val) -> {
                Object toSave = (val instanceof Entity ent) ? ent.getUUID() : val;
                // 修改点 3：传入 provider
                Tag serialized = VariableRegistry.toTag(toSave, provider);
                if (serialized != null) {
                    eventTag.put(key, serialized);
                }
            });
            tag.put("EventData", eventTag);
        }

        // 4. 局部变量栈
        ListTag stackTag = new ListTag();
        Iterator<Map<String, Object>> it = variableStack.descendingIterator();
        while (it.hasNext()) {
            Map<String, Object> scope = it.next();
            CompoundTag scopeTag = new CompoundTag();
            // 修改点 4：传入 provider
            saveVariables(scopeTag, scope, provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 5. 执行指令栈
        if (!executionStack.isEmpty()) {
            ListTag list = new ListTag();
            for (int id : executionStack) {
                list.add(StringTag.valueOf(index.getIdToString(id)));
            }
            tag.put("ExecutionStack", list);
        }
        return tag;
    }

    private boolean isValidType(Object v) {
        return VariableRegistry.isSupported(v);
    }

    private void saveVariables(CompoundTag tag, Map<String, Object> scope, HolderLookup.Provider provider) {
        scope.forEach((key, val) -> {
            Tag serialized = VariableRegistry.toTag(val, provider);
            if (serialized != null) {
                tag.put(key, serialized);
            }
        });
    }

    private void loadVariables(CompoundTag tag, Map<String, Object> scope, HolderLookup.Provider provider) {
        for (String key : tag.getAllKeys()) {
            Object deserialized = VariableRegistry.fromTag(tag.get(key), provider);
            if (deserialized != null) {
                scope.put(key, deserialized);
            }
        }
    }


    // ====================================================
    // 内部类：上下文实现
    // ====================================================

    private class InnerContext implements ExecutionContext {

        @Override
        public ServerLevel getLevel() { return GraphProcess.this.level; }

        @Override
        public Entity getEntity() { return GraphProcess.this.entity; }

        @Override
        public String getGraphId() { return GraphProcess.this.graphId; }

        @Override
        public Object getVariable(String name) {
            // 从栈顶往栈底查找作用域变量
            for (Map<String, Object> scope : variableStack) {
                if (scope.containsKey(name)) {
                    Object val = scope.get(name);
                    if (val instanceof UUID uuid) {
                        if (level == null) return null;
                        Entity resolvedEntity = level.getEntity(uuid);
                        if (resolvedEntity == null || resolvedEntity.isRemoved()) return null;
                        return resolvedEntity;
                    }
                    return val;
                }
            }
            return null;
        }

        @Override
        public void setVariable(String name, Object value) {
            Map<String, Object> currentScope = variableStack.peek();
            if (currentScope == null) return;

            if (value == null) {
                currentScope.remove(name);
                return;
            }

            if (isValidType(value)) {
                currentScope.put(name, (value instanceof Entity ent) ? ent.getUUID() : value);
            } else {
                System.err.println("GraphProcess: Unsupported variable type " + value.getClass().getSimpleName());
            }
        }

        @Override
        public Object getInputValue(String portName) {
            if (activeNodeId == -1) return null;
            RuntimeGraphIndex.IntConnectionSource source = index.findInputSource(activeNodeId, portName);
            if (source == null) return null;
            return executeDataNode(source.sourceNodeId(), source.sourcePortName());
        }

        @Override
        public Object getStaticInput(String portName) {
            return (activeNodeId != -1) ? GraphProcess.this.index.getNodeStaticInput(activeNodeId, portName) : null;
        }

        @Override
        public Object getNodeProperty(String key) {
            return (activeNodeId != -1) ? GraphProcess.this.index.getNodeProperty(activeNodeId, key) : null;
        }

        @Override
        public Object getEventData(String key) {
            Object val = GraphProcess.this.eventData.get(key);

            // UUID -> Entity 解析
            if (val instanceof UUID uuid) {
                if (level == null) return null;
                Entity resolvedEntity = level.getEntity(uuid);
                if (resolvedEntity == null || resolvedEntity.isRemoved()) return null;
                return resolvedEntity;
            }
            return val;
        }

        @Override
        public void setEventData(String key, Object value) {
            GraphProcess.this.eventData.put(key, value);
        }

        @Override
        public boolean hasPort(String portName) {
            return activeNodeId != -1 && GraphProcess.this.index.hasPort(activeNodeId, portName);
        }

        @Override
        public void setPersistentAttribute(@Nullable Object target, String name, Object value) {
            if (target == null) return;

            // 1. 实体层级
            if (target instanceof Entity ent) {
                GraphDataAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                if (att != null) att.setAttribute(name, value);
            }
            // 2. 全局层级 - "GLOBAL"
            else if ("GLOBAL".equals(target) && level != null) {
                LevelGraphAttachment att = LevelGraphAttachment.get(level.getServer().overworld());
                att.setAttribute(name, value);
            }
            // 3. 维度层级 (如 "minecraft:overworld")
            else if (target instanceof String dimId && level != null) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId);

                if (loc != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);

                    ServerLevel targetLevel = level.getServer().getLevel(dimKey);
                    if (targetLevel != null) {LevelGraphAttachment att = LevelGraphAttachment.get(targetLevel);
                        att.setAttribute(name, value);
                    }
                } else {
                    System.err.println("GraphProcess: Invalid dimension format -> " + dimId);
                }
            }
        }

        @Override
        public Object getPersistentAttribute(@Nullable Object target, String name) {
            if (target == null) return null; // 严格模式

            // 1. 实体层级
            if (target instanceof Entity ent) {
                GraphDataAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                return att != null ? att.getAttribute(name) : null;
            }
            // 2. 全局层级
            else if ("GLOBAL".equals(target) && level != null) {
                LevelGraphAttachment att = LevelGraphAttachment.get(level.getServer().overworld());
                return att.getAttribute(name);
            }
            // 3. 维度层级
            else if (target instanceof String dimId && level != null) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId);

                if (loc != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);

                    ServerLevel targetLevel = level.getServer().getLevel(dimKey);
                    if (targetLevel != null) {
                        LevelGraphAttachment att = LevelGraphAttachment.get(targetLevel);
                        return att.getAttribute(name);
                    }
                } else {
                    System.err.println("GraphProcess: Invalid dimension format -> " + dimId);
                }
            }
            return null;
        }
    }
}