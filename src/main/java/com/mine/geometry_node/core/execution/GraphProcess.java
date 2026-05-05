package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * [蓝图虚拟机核心进程] (The Graph VM Process)
 * <p>
 * 代表一次独立的蓝图执行生命周期。
 * 采用了零对象分配（Zero-Allocation）的底层设计，利用 Fastutil 库、
 * 寄存器数组和位运算来维持极高的执行性能。
 */
public class GraphProcess {

    // ================================
    // 1. 数据结构定义
    // ================================

    /** 虚拟机的生命周期状态 */
    public enum State {
        RUNNING,    // 活跃：正在执行指令栈中的节点
        WAITING,    // 挂起：指令栈为空，正等待协程/延迟任务唤醒
        FINISHED    // 终止：所有任务结束，等待引擎回收销毁
    }

    /** 描述一个被挂起的延迟协程任务 */
    private record ScheduledTask(long wakeUpTick, int resumeNodeId) {}


    // ================================
    // 2. 核心状态与内存寄存器
    // ================================

    // --- 基础信息 ---
    private final String graphId;
    private final RuntimeGraphIndex index;
    private final InnerContext context;

    // --- 外部环境上下文 ---
    private ServerLevel level;
    private Entity entity;

    // --- 引擎执行状态 ---
    private State state = State.RUNNING;
    private int currentFlowId = -1;       // 指令指针 (PC)：当前待执行的节点 ID
    private int activeNodeId = -1;        // 当前正在计算数据的节点 ID

    // --- 协程调度栈 ---
    private final List<ScheduledTask> sleepingTasks = new ArrayList<>();
    private boolean needsTimeRebase = false; // 读档标记：是否需要将相对时间转换为当前世界绝对时间

    // --- 内存模型 ---
    private final IntArrayList executionStack = new IntArrayList();           // 执行栈：存储待执行的分支节点
    private final Deque<Object[]> variableStack = new LinkedList<>();         // 变量栈：局部变量的多层作用域
    private Object[] eventRegisters = new Object[16];                         // 事件寄存器：存储系统事件注入的瞬时参数

    // --- 帧级缓存 ---
    private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>(); // 运算结果缓存 (高32位NodeId, 低32位PortId)
    private final IntOpenHashSet recursionGuard = new IntOpenHashSet();                       // 循环依赖防线：防止数据流死锁

    // --- 瞬时态数据黑板 ---
    private final Map<String, Object> tempData = new HashMap<>();

    // ================================
    // 3. 构造与序列化
    // ================================

    /**
     * [全新启动] 实例化并初始化一个新的执行进程
     */
    public GraphProcess(String graphId, RuntimeGraphIndex index, int startNodeId) {
        this.graphId = graphId;
        this.index = index;
        this.currentFlowId = startNodeId;
        this.context = new InnerContext();

        // 压入全局（顶级）变量作用域
        this.variableStack.push(new Object[16]);
    }

    /**
     * [断点续传] 从 NBT 存档中反序列化恢复执行进程
     */
    public GraphProcess(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        this.index = index;
        this.context = new InnerContext();

        this.graphId = tag.getString("GraphId");
        this.state = State.valueOf(tag.getString("State"));
        this.currentFlowId = tag.contains("NodeId") ? index.getStringToId(tag.getString("NodeId")) : -1;

        // 1. 恢复协程任务
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

        // 2. 恢复事件寄存器
        this.eventRegisters = new Object[16];
        if (tag.contains("EventData", Tag.TAG_COMPOUND)) {
            CompoundTag eventTag = tag.getCompound("EventData");
            for (String key : eventTag.getAllKeys()) {
                Object deserialized = VariableRegistry.fromTag(eventTag.get(key), provider);
                if (deserialized != null) {
                    int id = index.getOrRegisterKey(key);
                    this.eventRegisters = ensureCapacity(this.eventRegisters, id);
                    this.eventRegisters[id] = deserialized;
                }
            }
        }

        // 3. 恢复变量作用域栈
        this.variableStack.clear();
        if (tag.contains("VariableStack", Tag.TAG_LIST)) {
            ListTag stackTag = tag.getList("VariableStack", Tag.TAG_COMPOUND);
            for (int i = 0; i < stackTag.size(); i++) {
                Object[] scope = new Object[16];
                scope = loadVariables(stackTag.getCompound(i), scope, provider);
                this.variableStack.addLast(scope);
            }
        } else {
            this.variableStack.push(new Object[16]);
        }

        // 4. 恢复执行指令栈
        this.executionStack.clear();
        if (tag.contains("ExecutionStack", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ExecutionStack", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                int stackId = index.getStringToId(list.getString(i));
                if (stackId != -1) this.executionStack.add(stackId);
            }
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("GraphId", graphId);
        tag.putString("State", state.name());

        if (currentFlowId != -1) {
            tag.putString("NodeId", index.getIdToString(currentFlowId));
        }

        // 1. 保存协程任务
        if (!sleepingTasks.isEmpty()) {
            ListTag tasksTag = new ListTag();
            for (ScheduledTask task : sleepingTasks) {
                CompoundTag taskTag = new CompoundTag();
                long remaining = (level != null && !needsTimeRebase) ?
                        Math.max(0, task.wakeUpTick() - level.getGameTime()) : task.wakeUpTick();

                taskTag.putLong("WaitRemaining", remaining);
                if (task.resumeNodeId() != -1) {
                    taskTag.putString("ResumeNodeId", index.getIdToString(task.resumeNodeId()));
                }
                tasksTag.add(taskTag);
            }
            tag.put("SleepingTasks", tasksTag);
        }

        // 2. 保存事件寄存器
        boolean hasEventData = false;
        CompoundTag eventTag = new CompoundTag();
        for (int i = 0; i < eventRegisters.length; i++) {
            if (eventRegisters[i] != null) {
                hasEventData = true;
                String key = index.getKeyFromId(i);
                Object toSave = (eventRegisters[i] instanceof Entity ent) ? ent.getUUID() : eventRegisters[i];
                Tag serialized = VariableRegistry.toTag(toSave, provider);
                if (serialized != null && key != null) eventTag.put(key, serialized);
            }
        }
        if (hasEventData) tag.put("EventData", eventTag);

        // 3. 保存变量作用域栈
        ListTag stackTag = new ListTag();
        Iterator<Object[]> it = variableStack.descendingIterator();
        while (it.hasNext()) {
            CompoundTag scopeTag = new CompoundTag();
            saveVariables(scopeTag, it.next(), provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 4. 保存执行指令栈
        if (!executionStack.isEmpty()) {
            ListTag list = new ListTag();
            for (int i = 0; i < executionStack.size(); i++) {
                list.add(StringTag.valueOf(index.getIdToString(executionStack.getInt(i))));
            }
            tag.put("ExecutionStack", list);
        }

        return tag;
    }


    // ================================
    // 4. 生命周期与公开 API
    // ================================

    public String getGraphId() { return graphId; }
    public boolean isFinished() { return state == State.FINISHED; }

    public void setEnvironment(ServerLevel level, @Nullable Entity entity) {
        this.level = level;
        this.entity = entity;
    }

    /**
     * [引擎注值] 向事件寄存器写入参数 (通常由事件分发器调用)
     */
    public void setEventData(String key, Object value) {
        int id = index.getOrRegisterKey(key);
        this.eventRegisters = ensureCapacity(this.eventRegisters, id);
        this.eventRegisters[id] = value;
    }
    
    public void tick(long currentWorldTick) {
        if (state == State.FINISHED || level == null) return;

        // 1. 每帧清空瞬时运算缓存与防死锁集合
        frameCache.clear();
        recursionGuard.clear();

        // 2. 相对等待时间 to 世界绝对时间
        if (this.needsTimeRebase) {
            for (int i = 0; i < sleepingTasks.size(); i++) {
                ScheduledTask oldTask = sleepingTasks.get(i);
                sleepingTasks.set(i, new ScheduledTask(currentWorldTick + oldTask.wakeUpTick(), oldTask.resumeNodeId()));
            }
            this.needsTimeRebase = false;
        }

        // 3. 协程调度：唤醒到期的延迟任务，将其压入主执行栈
        Iterator<ScheduledTask> it = sleepingTasks.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (currentWorldTick >= task.wakeUpTick()) {
                if (task.resumeNodeId() != -1) {
                    this.executionStack.add(0, task.resumeNodeId());
                }
                it.remove();
            }
        }

        // 4. 状态机推演与执行
        if (currentFlowId != -1 || !executionStack.isEmpty()) {
            state = State.RUNNING;
            runExecutionLoop();
        } else if (sleepingTasks.isEmpty()) {
            state = State.FINISHED; // 彻底结束
        } else {
            state = State.WAITING;  // 挂起等待
        }
    }


    // ================================
    // 5. 虚拟机内部引擎
    // ================================

    /**
     * [控制流执行模型 (Push Model)]
     * 持续执行栈中指令，直到触发挂起 (Wait) 或触碰单帧防卡死上限 (MAX_STEPS)。
     */
    private void runExecutionLoop() {
        int steps = 0;
        final int MAX_STEPS = 1000;

        while ((currentFlowId != -1 || !executionStack.isEmpty()) && state == State.RUNNING) {

            // 若当前无指令，则从栈顶弹出下一个
            if (currentFlowId == -1) {
                currentFlowId = executionStack.removeInt(0);
            }

            // 防卡死
            if (steps++ > MAX_STEPS) return;

            String nodeType = index.getNodeType(currentFlowId);
            if ("unknown".equals(nodeType)) {
                state = State.FINISHED;
                return;
            }

            BaseNode logic = NodeRegistry.INSTANCE.get(nodeType);
            if (logic == null) {
                System.err.println("[GraphProcess] Unknown node type encountered: " + nodeType);
                state = State.FINISHED;
                return;
            }

            // --- 核心执行域 ---
            try {
                int previousActive = this.activeNodeId;
                this.activeNodeId = currentFlowId;

                ExecutionResult result = logic.execute(context);

                this.activeNodeId = previousActive;
                handleExecutionResult(result);

            } catch (Exception e) {
                System.err.println("[GraphProcess] Critical error at node " + index.getIdToString(currentFlowId) + ": " + e.getMessage());
                e.printStackTrace();
                state = State.FINISHED;
            }
        }

        // 善后状态判定
        if (currentFlowId == -1 && executionStack.isEmpty() && sleepingTasks.isEmpty()) {
            state = State.FINISHED;
        }
    }

    /**
     * 解析节点的执行结果，并改变指令指针或压栈
     */
    private void handleExecutionResult(ExecutionResult result) {
        switch (result) {
            case ExecutionResult.Next next -> {
                this.currentFlowId = index.findFlowTarget(currentFlowId, next.outputPortName());
            }
            case ExecutionResult.Call call -> {
                // 将后续执行流按序压入栈顶 (逆序压入保证正序弹出)
                List<String> ports = call.outputPorts();
                for (int i = ports.size() - 1; i >= 0; i--) {
                    int targetId = index.findFlowTarget(currentFlowId, ports.get(i));
                    if (targetId != -1) {
                        this.executionStack.add(0, targetId);
                    }
                }
                this.currentFlowId = executionStack.isEmpty() ? -1 : executionStack.removeInt(0);
            }
            case ExecutionResult.Wait wait -> {
                // 挂起当前流，注册协程唤醒时间
                long wakeTime = level.getGameTime() + wait.ticks();
                int nextId = index.findFlowTarget(currentFlowId, wait.nextPortName());
                if (nextId != -1) {
                    this.sleepingTasks.add(new ScheduledTask(wakeTime, nextId));
                }
                this.currentFlowId = -1;
            }
            case ExecutionResult.Finish ignored -> {
                // 自然结束当前分支
                this.currentFlowId = -1;
            }
            case ExecutionResult.Error err -> {
                // 异常宕机
                System.err.println("[GraphProcess] Execution aborted: " + err.errorMessage());
                this.executionStack.clear();
                this.currentFlowId = -1;
                this.state = State.FINISHED;
            }
        }
    }

    /**
     * [数据流计算模型 (Pull Model)]
     * 递归向上游节点索要数据。附带单帧缓存与死锁防御。
     */
    private Object executeDataNode(int nodeId, String portName) {
        int portId = index.getOrRegisterKey(portName);

        // 位运算拼装终极 CacheKey (高32位放NodeID，低32位放PortID)
        long cacheKey = ((long) nodeId << 32) | (portId & 0xFFFFFFFFL);

        // 缓存命中：同帧内已被计算过，直接返回
        if (frameCache.containsKey(cacheKey)) {
            return frameCache.get(cacheKey);
        }

        // 循环依赖检测：触发死锁
        if (recursionGuard.contains(nodeId)) {
            System.err.println("[GraphProcess] Dependency cycle detected at node: " + index.getIdToString(nodeId));
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


    // ================================
    // 6. 内存管理辅助工具
    // ================================

    private Object[] ensureCapacity(Object[] arr, int requiredIndex) {
        if (requiredIndex >= arr.length) {
            return Arrays.copyOf(arr, Math.max(arr.length * 2, requiredIndex + 1));
        }
        return arr;
    }

    private void saveVariables(CompoundTag tag, Object[] scope, HolderLookup.Provider provider) {
        for (int i = 0; i < scope.length; i++) {
            if (scope[i] != null) {
                String key = index.getKeyFromId(i);
                Tag serialized = VariableRegistry.toTag(scope[i], provider);
                if (serialized != null && key != null) {
                    tag.put(key, serialized);
                }
            }
        }
    }

    private Object[] loadVariables(CompoundTag tag, Object[] scope, HolderLookup.Provider provider) {
        for (String key : tag.getAllKeys()) {
            Object deserialized = VariableRegistry.fromTag(tag.get(key), provider);
            if (deserialized != null) {
                int id = index.getOrRegisterKey(key);
                scope = ensureCapacity(scope, id);
                scope[id] = deserialized;
            }
        }
        return scope;
    }

    public void terminate() {
        this.state = State.FINISHED;
    }


    // ================================
    // 7. 内部执行上下文
    // ================================

    /**
     * 供蓝图节点调用的隔离接口，隐藏底层虚拟机的复杂状态。
     */
    private class InnerContext implements ExecutionContext {

        @Override
        public ServerLevel getLevel() { return GraphProcess.this.level; }

        @Override
        public Entity getEntity() { return GraphProcess.this.entity; }

        @Override
        public String getGraphId() { return GraphProcess.this.graphId; }

        @Override
        public Object getVariable(String name) {
            int id = index.getOrRegisterKey(name);
            // 从栈顶往栈底（由近及远）查找局部变量
            for (Object[] scope : variableStack) {
                if (id < scope.length && scope[id] != null) {
                    Object val = scope[id];
                    // 若为实体引用，实施即时反解析
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
            Object[] currentScope = variableStack.pop();
            if (currentScope == null) return;

            int id = index.getOrRegisterKey(name);
            currentScope = ensureCapacity(currentScope, id);

            if (value == null) {
                currentScope[id] = null; // 删除变量
            } else if (VariableRegistry.isSupported(value)) {
                // 实体类型 to UUID，防止内存泄漏
                currentScope[id] = (value instanceof Entity ent) ? ent.getUUID() : value;
            } else {
                System.err.println("[GraphProcess] Unsupported variable type: " + value.getClass().getSimpleName());
            }
            variableStack.push(currentScope);
        }

        @Override
        public Object getEventData(String key) {
            int id = index.getOrRegisterKey(key);
            if (id >= eventRegisters.length) return null;

            Object val = eventRegisters[id];
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
            int id = index.getOrRegisterKey(key);
            eventRegisters = ensureCapacity(eventRegisters, id);
            eventRegisters[id] = value;
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
        public boolean hasPort(String portName) {
            return activeNodeId != -1 && GraphProcess.this.index.hasPort(activeNodeId, portName);
        }

        @Override
        public void setPersistentAttribute(@Nullable Object target, String name, Object value) {
            if (target == null) return;

            if (target instanceof Entity ent) {
                EntityGraphAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                if (att != null) att.setAttribute(name, value);
            } else if ("GLOBAL".equals(target) && level != null) {
                LevelGraphAttachment att = LevelGraphAttachment.get(level.getServer().overworld());
                att.setAttribute(name, value);
            } else if (target instanceof String dimId && level != null) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId);
                if (loc != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);
                    ServerLevel targetLevel = level.getServer().getLevel(dimKey);
                    if (targetLevel != null) {
                        LevelGraphAttachment att = LevelGraphAttachment.get(targetLevel);
                        att.setAttribute(name, value);
                    }
                } else {
                    System.err.println("[GraphProcess] Invalid dimension format -> " + dimId);
                }
            }
        }

        @Override
        public Object getPersistentAttribute(@Nullable Object target, String name) {
            if (target == null) return null;

            if (target instanceof Entity ent) {
                EntityGraphAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                return att != null ? att.getAttribute(name) : null;
            } else if ("GLOBAL".equals(target) && level != null) {
                LevelGraphAttachment att = LevelGraphAttachment.get(level.getServer().overworld());
                return att.getAttribute(name);
            } else if (target instanceof String dimId && level != null) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId);
                if (loc != null) {
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);
                    ServerLevel targetLevel = level.getServer().getLevel(dimKey);
                    if (targetLevel != null) {
                        LevelGraphAttachment att = LevelGraphAttachment.get(targetLevel);
                        return att.getAttribute(name);
                    }
                } else {
                    System.err.println("[GraphProcess] Invalid dimension format -> " + dimId);
                }
            }
            return null;
        }

        // ==========================================
        // 高级控制流与引擎特权 API
        // ==========================================

        @Override
        public void clearFrameCache() {
            // 直接清空引擎运算缓存
            GraphProcess.this.frameCache.clear();
        }

        @Override
        public void executeBranchSync(String portName) {
            if (activeNodeId == -1) return;
            int targetId = index.findFlowTarget(activeNodeId, portName);
            if (targetId == -1) return;

            // 1. 备份当前执行现场
            int savedFlowId = GraphProcess.this.currentFlowId;
            IntArrayList savedStack = new IntArrayList(GraphProcess.this.executionStack);
            State savedState = GraphProcess.this.state; // 备份当前虚拟机生命周期状态

            // 2. 将引擎的指针指向“子分支”起点
            GraphProcess.this.currentFlowId = targetId;
            GraphProcess.this.executionStack.clear();
            GraphProcess.this.state = State.RUNNING;    // 强制设为运行态，给子分支注入活力

            // 3. 阻塞式执行子分支
            GraphProcess.this.runExecutionLoop();

            // 4. 恢复初始执行现场
            GraphProcess.this.currentFlowId = savedFlowId;
            GraphProcess.this.executionStack.clear();
            GraphProcess.this.executionStack.addAll(savedStack);
            GraphProcess.this.state = savedState;       // 恢复外层的生命周期状态
        }

        @Override
        public void setTempData(String key, Object value) {
            GraphProcess.this.tempData.put(key, value);
        }

        @Override
        public Object getTempData(String key) {
            return GraphProcess.this.tempData.get(key);
        }

        @Override
        public void removeTempData(String key) {
            GraphProcess.this.tempData.remove(key);
        }

        @Override
        public int getCurrentNodeId() {
            return GraphProcess.this.activeNodeId;
        }

        @Override
        public void scheduleNode(int nodeId, long delayTicks) {
            if (GraphProcess.this.level == null) return;
            long wakeTime = GraphProcess.this.level.getGameTime() + delayTicks;
            GraphProcess.this.sleepingTasks.add(new ScheduledTask(wakeTime, nodeId));
        }

        @Override
        public void broadcastVisual(String effectType, int sourceEntityId, Vec3 startPos,
                                    int targetEntityId, Vec3 endPos,
                                    int color, float size, int durationTicks) {

            if (GraphProcess.this.level == null) return;

            int radius = 128;  // 广播半径

            // 1. 将原有的静态坐标与 ID 打包进 NBT 数据夹
            net.minecraft.nbt.CompoundTag extraData = new net.minecraft.nbt.CompoundTag();
            extraData.putInt("sourceId", sourceEntityId);
            if (startPos != null) {
                extraData.putDouble("startX", startPos.x);
                extraData.putDouble("startY", startPos.y);
                extraData.putDouble("startZ", startPos.z);
            }
            extraData.putInt("targetId", targetEntityId);
            if (endPos != null) {
                extraData.putDouble("endX", endPos.x);
                extraData.putDouble("endY", endPos.y);
                extraData.putDouble("endZ", endPos.z);
            }
            extraData.putFloat("size", size);

            // 2. 组装重构后的视觉网络包
            PacketSpawnDynamicVisual payload = new PacketSpawnDynamicVisual(
                    effectType, color, durationTicks,
                    java.util.Collections.emptyMap(), // 没有动态表达式
                    java.util.Collections.emptyMap(), // 没有动态变量绑定
                    extraData
            );

            // 3. 广播范围筛选 (以起点为中心)
            Vec3 center = startPos != null ? startPos : Vec3.ZERO;
            List<net.minecraft.server.level.ServerPlayer> nearbyPlayers =
                    GraphProcess.this.level.getPlayers(
                            player -> player.position().distanceToSqr(center) < radius * radius
                    );

            if (!nearbyPlayers.isEmpty()) {
                com.mine.geometry_node.core.network.NetworkHandler.sendToPlayers(nearbyPlayers, payload);
            }
        }

        @Override
        public void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                           Map<String, String> expressions,
                                           Map<String, String> bindings,
                                           net.minecraft.nbt.CompoundTag extraData) {

            if (GraphProcess.this.level == null) return;

            PacketSpawnDynamicVisual packet = new PacketSpawnDynamicVisual(
                    effectType, color, durationTicks, expressions, bindings, extraData
            );

            Vec3 center = null;

            // 1. 优先尝试从绑定的实体获取最新鲜的中心点
            if (extraData != null && extraData.contains("sourceId")) {
                int sourceId = extraData.getInt("sourceId");
                if (sourceId != -1) {
                    net.minecraft.world.entity.Entity sourceEntity = GraphProcess.this.level.getEntity(sourceId);
                    if (sourceEntity != null) {
                        center = sourceEntity.position();
                    }
                }
            }

            // 2. 如果没有绑定实体，或者实体已经超出了服务端的加载范围（获取为 null），则使用数据包里提供的静态坐标
            if (center == null && extraData != null && extraData.contains("startX")) {
                center = new Vec3(
                        extraData.getDouble("startX"),
                        extraData.getDouble("startY"),
                        extraData.getDouble("startZ")
                );
            }

            // 3. 终极容错：如果还是拿不到，才回落到 0,0,0
            if (center == null) {
                center = Vec3.ZERO;
            }

            // 统一的安全大范围距离过滤 (彻底避免了原生 broadcastAndSend 的强制转型崩溃问题)
            int radius = 128; // 可以视情况放大，比如 256
            double radiusSqr = (double) radius * radius;

            List<net.minecraft.server.level.ServerPlayer> targetPlayers = new java.util.ArrayList<>();

            for (net.minecraft.server.level.ServerPlayer player : GraphProcess.this.level.players()) {
                if (player.position().distanceToSqr(center) < radiusSqr) {
                    targetPlayers.add(player);
                }
            }

            // 使用你自己写的、安全的网络层发送自定义载荷
            if (!targetPlayers.isEmpty()) {
                com.mine.geometry_node.core.network.NetworkHandler.sendToPlayers(targetPlayers, packet);
            }
        }
    }
}