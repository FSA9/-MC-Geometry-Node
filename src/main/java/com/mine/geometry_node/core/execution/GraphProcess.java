package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import java.util.function.Consumer;

/**
 * [蓝图虚拟机进程 - 内存主板]
 * * 代表一个蓝图图纸在某个实体或维度上的运行实例。
 * 它不再直接持有指令指针，而是作为“内存黑板”管理变量和环境。
 * 具体的执行逻辑由内部类 ExecutionThread 承担。
 */
public class GraphProcess {

    // ================================
    // 1. 核心持久化状态
    // ================================

    private final String graphId;
    private final RuntimeGraphIndex index;

    // --- 环境上下文 ---
    private ServerLevel level;
    private Entity entity;

    static class VariableScope {
        final Object[] statics;
        Map<String, Object> dynamics = null; // 懒加载

        VariableScope(int staticSize) {
            this.statics = new Object[staticSize];
        }
    }

    final Deque<VariableScope> variableStack = new ArrayDeque<>(8);

    // --- 执行流管理 ---
    final PriorityQueue<ExecutionThread> sleepingThreads = new PriorityQueue<>(Comparator.comparingLong(t -> t.wakeUpTick));  // 挂起的协程线程
    boolean needsTimeRebase = false;  // 读档标记

    private final ArrayDeque<ExecutionThread> THREAD_POOL = new ArrayDeque<>();

    private ExecutionThread borrowThread(int startNodeId, String startPortName) {
        ExecutionThread thread = THREAD_POOL.poll();
        if (thread == null) {
            thread = this.new ExecutionThread(startNodeId, startPortName);
        } else {
            thread.reset(startNodeId, startPortName);
        }
        return thread;
    }

    private void recycleThread(ExecutionThread thread) {
        THREAD_POOL.offer(thread);
    }

    // ================================
    // 2. 构造与环境
    // ================================

    public GraphProcess(String graphId, RuntimeGraphIndex index) {
        this.graphId = graphId;
        this.index = index;
        int exactSize = index.getRegisterCount() + 8;
        this.variableStack.push(new VariableScope(exactSize));
    }

    public void setEnvironment(ServerLevel level, @Nullable Entity entity) {
        this.level = level;
        this.entity = entity;
    }

    public String getGraphId() { return graphId; }
    public RuntimeGraphIndex getIndex() { return index; }

    // ================================
    // 3. 执行调度入口
    // ================================

    /**
     * [派发事件线程]
     * 为蓝图入口分配一个独立的执行线程。支持多事件并发执行。
     */
    public void executeEvent(int startNodeId, @Nullable Consumer<ExecutionThread> initializer) {
        if (this.level == null) return;

        // 调用修改后的 borrowThread，传入默认端口 "flow_in" 作为执行起点的占位
        ExecutionThread thread = borrowThread(startNodeId, "flow_in");

        if (initializer != null) {
            initializer.accept(thread);
        }

        thread.run();
    }

    /**
     * [心跳驱动]
     * 负责处理读档重基准以及唤醒到期的休眠线程。
     */
    public void tick(long currentWorldTick) {
        if (level == null) return;

        // 1. 读档后的相对时间修正
        if (this.needsTimeRebase) {
            List<ExecutionThread> temp = new ArrayList<>(sleepingThreads);
            sleepingThreads.clear();
            for (ExecutionThread thread : temp) {
                thread.wakeUpTick = currentWorldTick + thread.wakeUpTick;
                sleepingThreads.add(thread); // 重新入堆排序
            }
            this.needsTimeRebase = false;
        }

        // 2. 唤醒到期线程
        List<ExecutionThread> awakeThreads = null;
        while (!sleepingThreads.isEmpty() && sleepingThreads.peek().wakeUpTick <= currentWorldTick) {
            ExecutionThread thread = sleepingThreads.poll();
            if (awakeThreads == null) awakeThreads = new ArrayList<>();
            awakeThreads.add(thread);
        }

        // 3. 执行唤醒线程
        if (awakeThreads != null) {
            for (ExecutionThread thread : awakeThreads) {
                thread.state = ExecutionThread.State.RUNNING;
                thread.run();
            }
        }
    }

    // ================================
    // 4. 内部执行线程 (ExecutionContext 实现)
    // ================================

    /**
     * 代表一次独立的蓝图指令流。
     * 拥有私有的栈、私有的寄存器、私有的运算缓存，彻底杜绝重入污染。
     */
    public class ExecutionThread implements ExecutionContext {

        public enum State { RUNNING, WAITING, FINISHED, ERROR }

        public State state = State.RUNNING;
        int currentFlowId;
        String currentEntryPort;
        private int activeNodeId = -1;
        public long wakeUpTick = -1; // 仅在 WAITING 状态有效
        private int runDepth = 0;

        // --- 线程私有寄存器 (Zero-Allocation 核心) ---
        final List<RuntimeGraphIndex.IntFlowTarget> executionStack = new ArrayList<>();
        Object[] eventRegisters = new Object[GraphProcess.this.index.getRegisterCount() + 8];
        Map<String, Object> dynamicEventData = null;
        private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<Map<String, Object>> dynamicFrameCache = new Int2ObjectOpenHashMap<>();
        private static final Object CACHED_NULL = new Object();
        private final boolean[] recursionGuard = new boolean[GraphProcess.this.index.getNodeCount()];
        // ✨ 新增：线程私有的临时黑板
        public final Map<String, Object> tempData = new HashMap<>();

        public ExecutionThread(int startNodeId, String startPortName) {
            this.currentFlowId = startNodeId;
            this.currentEntryPort = startPortName;
        }

        @Override
        public String getEntryPort() {
            return this.currentEntryPort;
        }

        /**
         * [洗盘子] 重置线程状态，准备下一次复用
         */
        public void reset(int startNodeId, String startPortName) {
            this.state = State.RUNNING;
            this.currentFlowId = startNodeId;
            this.currentEntryPort = startPortName;
            this.activeNodeId = -1;
            this.wakeUpTick = -1;
            this.runDepth = 0;
            this.executionStack.clear();
            this.frameCache.clear();
            this.dynamicFrameCache.clear();
            Arrays.fill(this.recursionGuard, false);
            this.tempData.clear();
            Arrays.fill(this.eventRegisters, null);
            if (this.dynamicEventData != null) this.dynamicEventData.clear();
        }

        /**
         * 启动或恢复执行流
         */
        public void run() {
            runDepth++; // 进入一层执行流
            int steps = 0;
            final int MAX_STEPS = 1000;

            // 只有最外层启动时，才清理初始缓存，防止误清内层递归数据
            if (runDepth == 1) {
                frameCache.clear();
                Arrays.fill(this.recursionGuard, false);
            }

            try {
                while ((currentFlowId != -1 || !executionStack.isEmpty()) && state == State.RUNNING) {
                    if (currentFlowId == -1) {
                        RuntimeGraphIndex.IntFlowTarget frame = executionStack.remove(executionStack.size() - 1);
                        currentFlowId = frame.targetNodeId();
                        currentEntryPort = frame.targetPortName();
                    }

                    if (steps++ > MAX_STEPS) {
                        System.err.println("[GraphVM] Infinite loop detected!");
                        state = State.ERROR;
                        return; // return 会自动触发 finally 块的清理
                    }

                    String nodeType = index.getNodeType(currentFlowId);
                    BaseNode logic = NodeRegistry.INSTANCE.get(nodeType);
                    if (logic == null) {
                        state = State.ERROR;
                        return;
                    }

                    try {
                        int previousActive = this.activeNodeId;
                        this.activeNodeId = currentFlowId;

                        ExecutionResult result = logic.execute(this);

                        this.activeNodeId = previousActive;
                        handleExecutionResult(result);

                    } catch (Exception e) {
                        System.err.println("[GraphVM] Critical error in node " + index.getIdToString(currentFlowId));
                        e.printStackTrace();
                        state = State.ERROR;
                        // [新增修正] 节点抛出异常时强制断开执行流，避免死循环
                        currentFlowId = -1;
                        executionStack.clear();
                    }
                }
            } finally {
                runDepth--;
                if (runDepth == 0 && currentFlowId == -1 && executionStack.isEmpty() && state != State.WAITING) {
                    if (this.state != State.ERROR) {
                        this.state = State.FINISHED;
                    }
                    GraphProcess.this.recycleThread(this);
                }
            }
        }

        private void handleExecutionResult(ExecutionResult result) {
            switch (result) {
                case ExecutionResult.Next next -> {
                    RuntimeGraphIndex.IntFlowTarget target = index.findFlowTarget(currentFlowId, next.outputPortName());
                    if (target != null) {
                        this.currentFlowId = target.targetNodeId();
                        this.currentEntryPort = target.targetPortName();
                    } else {
                        this.currentFlowId = -1;
                    }
                }
                case ExecutionResult.Call call -> {
                    List<String> ports = call.outputPorts();
                    for (int i = ports.size() - 1; i >= 0; i--) {
                        RuntimeGraphIndex.IntFlowTarget target = index.findFlowTarget(currentFlowId, ports.get(i));
                        if (target != null) this.executionStack.add(target);
                    }
                    if (executionStack.isEmpty()) {
                        this.currentFlowId = -1;
                    } else {
                        // 从尾部弹出
                        RuntimeGraphIndex.IntFlowTarget frame = executionStack.remove(executionStack.size() - 1);
                        this.currentFlowId = frame.targetNodeId();
                        this.currentEntryPort = frame.targetPortName();
                    }
                }
                case ExecutionResult.Wait wait -> {
                    this.wakeUpTick = level.getGameTime() + wait.ticks();
                    RuntimeGraphIndex.IntFlowTarget target = index.findFlowTarget(currentFlowId, wait.nextPortName());
                    if (target != null) this.executionStack.add(target);

                    this.currentFlowId = -1;
                    this.state = State.WAITING;
                    GraphProcess.this.sleepingThreads.add(this);
                }
                case ExecutionResult.Finish ignored -> this.currentFlowId = -1;
                case ExecutionResult.Error err -> {
                    this.state = State.ERROR;
                    this.currentFlowId = -1;
                    this.executionStack.clear();
                }
            }
        }

        private Object executeDataNode(int nodeId, String portName) {
            if (recursionGuard[nodeId]) return null;

            int portId = index.getKeyId(portName);
            long cacheKey = 0;
            Map<String, Object> nodeDynamicCache = null;

            // ==========================================
            // 1. 查缓存 (单次查询 O(1) + 零字符串分配)
            // ==========================================
            if (portId != -1) {
                cacheKey = ((long) nodeId << 32) | (portId & 0xFFFFFFFFL);
                Object cached = frameCache.get(cacheKey);
                // 如果有值，判断是否是占位符
                if (cached != null) return cached == CACHED_NULL ? null : cached;
            } else {
                nodeDynamicCache = dynamicFrameCache.get(nodeId);
                if (nodeDynamicCache != null) {
                    Object cached = nodeDynamicCache.get(portName);
                    if (cached != null) return cached == CACHED_NULL ? null : cached;
                }
            }

            // ==========================================
            // 2. 执行计算
            // ==========================================
            recursionGuard[nodeId] = true;
            int prevActive = this.activeNodeId;
            Object result;

            try {
                BaseNode logic = NodeRegistry.INSTANCE.get(index.getNodeType(nodeId));
                if (logic == null) return null;

                this.activeNodeId = nodeId;
                result = logic.compute(this, portName);
            } finally {
                this.activeNodeId = prevActive;
                recursionGuard[nodeId] = false;
            }

            // ==========================================
            // 3. 写缓存 (使用 CACHED_NULL 占位)
            // ==========================================
            Object cacheValue = (result == null) ? CACHED_NULL : result;
            if (portId != -1) {
                frameCache.put(cacheKey, cacheValue);
            } else {
                if (nodeDynamicCache == null) {
                    nodeDynamicCache = new HashMap<>();
                    dynamicFrameCache.put(nodeId, nodeDynamicCache);
                }
                nodeDynamicCache.put(portName, cacheValue);
            }

            return result;
        }

        // ==========================================
        // ExecutionContext 实现 (隔离的执行环境)
        // ==========================================

        @Override
        public ServerLevel getLevel() { return GraphProcess.this.level; }

        @Override
        public Entity getEntity() { return GraphProcess.this.entity; }

        @Override
        public String getGraphId() { return GraphProcess.this.graphId; }

        @Override
        public Object getVariable(String name) {
            int id = index.getKeyId(name);
            for (VariableScope scope : GraphProcess.this.variableStack) {
                Object val = null;
                if (id != -1 && id < scope.statics.length && scope.statics[id] != null) {
                    val = scope.statics[id];
                } else if (id == -1 && scope.dynamics != null && scope.dynamics.containsKey(name)) {
                    val = scope.dynamics.get(name);
                }

                if (val != null) {
                    if (val instanceof UUID uuid && level != null) {
                        Entity res = level.getEntity(uuid);
                        return (res == null || res.isRemoved()) ? null : res;
                    }
                    return val;
                }
            }
            return null;
        }

        @Override
        public void setVariable(String name, Object value) {
            VariableScope scope = GraphProcess.this.variableStack.peek();
            if (scope == null) return;

            int id = index.getKeyId(name);
            Object finalValue = (value instanceof Entity ent) ? ent.getUUID() : value;

            if (id != -1) {
                if (id < scope.statics.length) scope.statics[id] = finalValue;
            } else {
                if (scope.dynamics == null) scope.dynamics = new HashMap<>();
                if (finalValue == null) scope.dynamics.remove(name);
                else scope.dynamics.put(name, finalValue);
            }
        }

        @Override
        public Object getEventData(String key) {
            int id = index.getKeyId(key);
            Object val = null;

            if (id != -1 && id < eventRegisters.length) {
                val = eventRegisters[id];
            } else if (id == -1 && dynamicEventData != null) {
                val = dynamicEventData.get(key);
            }

            if (val instanceof UUID uuid && level != null) {
                Entity res = level.getEntity(uuid);
                return (res == null || res.isRemoved()) ? null : res;
            }
            return val;
        }

        @Override
        public void setEventData(String key, Object value) {
            int id = index.getKeyId(key);
            Object finalValue = (value instanceof Entity ent) ? ent.getUUID() : value;

            if (id != -1) {
                if (id < eventRegisters.length) eventRegisters[id] = finalValue;
            } else {
                if (dynamicEventData == null) dynamicEventData = new HashMap<>();
                dynamicEventData.put(key, finalValue);
            }
        }

        @Override
        public Object getInputValue(String portName) {
            if (activeNodeId == -1) return null;
            RuntimeGraphIndex.IntConnectionSource src = index.findInputSource(activeNodeId, portName);
            if (src == null) return null;
            return executeDataNode(src.sourceNodeId(), src.sourcePortName());
        }

        @Override
        public Object getStaticInput(String portName) {
            return (activeNodeId != -1) ? index.getNodeStaticInput(activeNodeId, portName) : null;
        }

        @Override
        public boolean hasPort(String portName) {
            return activeNodeId != -1 && index.hasPort(activeNodeId, portName);
        }

        @Override
        public void setPersistentAttribute(@Nullable Object target, String name, Object value) {
            // 实现同前，操作 Level/Entity 的 Attachment
            if (target == null) return;
            if (target instanceof Entity ent) {
                EntityGraphAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                if (att != null) att.setAttribute(name, value);
            } else if ("GLOBAL".equals(target) && level != null) {
                LevelGraphAttachment.get(level.getServer().overworld()).setAttribute(name, value);
            }
        }

        @Override
        public Object getPersistentAttribute(@Nullable Object target, String name) {
            if (target == null) return null;
            if (target instanceof Entity ent) {
                EntityGraphAttachment att = ent.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                return att != null ? att.getAttribute(name) : null;
            } else if ("GLOBAL".equals(target) && level != null) {
                return LevelGraphAttachment.get(level.getServer().overworld()).getAttribute(name);
            }
            return null;
        }

        @Override
        public void clearFrameCache() {
            frameCache.clear();
            for (Map<String, Object> map : dynamicFrameCache.values()) {
                map.clear();
            }
        }

        @Override
        public void executeBranchSync(String portName) {
            if (activeNodeId == -1) return;
            RuntimeGraphIndex.IntFlowTarget target = index.findFlowTarget(activeNodeId, portName);
            if (target == null) return;

            int savedId = this.currentFlowId;
            String savedPort = this.currentEntryPort;
            List<RuntimeGraphIndex.IntFlowTarget> savedStack = new ArrayList<>(this.executionStack);

            this.currentFlowId = target.targetNodeId();
            this.currentEntryPort = target.targetPortName();
            this.executionStack.clear();
            this.run(); // 递归执行子树

            this.currentFlowId = savedId;
            this.currentEntryPort = savedPort;
            this.executionStack.clear();
            this.executionStack.addAll(savedStack);
        }

        @Override
        public void setTempData(String key, Object value) { this.tempData.put(key, value); }

        @Override
        public Object getTempData(String key) { return this.tempData.get(key); }

        @Override
        public void removeTempData(String key) { this.tempData.remove(key); }

        @Override
        public int getCurrentNodeId() { return this.activeNodeId; }

        @Override
        public void scheduleNode(int nodeId, long delayTicks, String entryPortName) {
            if (level == null) return;

            ExecutionThread delayThread = new ExecutionThread(nodeId, entryPortName);
            delayThread.wakeUpTick = level.getGameTime() + delayTicks;
            delayThread.state = State.WAITING;

            System.arraycopy(this.eventRegisters, 0, delayThread.eventRegisters, 0, this.eventRegisters.length);

            if (this.dynamicEventData != null) {
                delayThread.dynamicEventData = new HashMap<>(this.dynamicEventData);
            }

            delayThread.tempData.putAll(this.tempData);
            GraphProcess.this.sleepingThreads.add(delayThread);
        }

        @Override
        public void broadcastVisual(String effectType, int sourceEntityId, Vec3 startPos,
                                    int targetEntityId, Vec3 endPos,
                                    int color, float size, int durationTicks) {

            if (GraphProcess.this.level == null) return;

            int radius = 128;

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

            PacketSpawnDynamicVisual payload = new PacketSpawnDynamicVisual(
            effectType, color, durationTicks,
                    java.util.Collections.emptyMap(),
                    java.util.Collections.emptyMap(),
                    extraData
            );

            Vec3 center = startPos != null ? startPos : Vec3.ZERO;
            List<ServerPlayer> nearbyPlayers =
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

            if (extraData != null && extraData.contains("sourceId")) {
                int sourceId = extraData.getInt("sourceId");
                if (sourceId != -1) {
                    Entity sourceEntity = GraphProcess.this.level.getEntity(sourceId);
                    if (sourceEntity != null) {
                        center = sourceEntity.position();
                    }
                }
            }

            if (center == null && extraData != null && extraData.contains("startX")) {
                center = new Vec3(extraData.getDouble("startX"),
                                  extraData.getDouble("startY"),
                                  extraData.getDouble("startZ"));
            }

            if (center == null) {
                center = Vec3.ZERO;
            }

            int radius = 128;
            double radiusSqr = (double) radius * radius;

            List<ServerPlayer> targetPlayers = new java.util.ArrayList<>();

            for (ServerPlayer player : GraphProcess.this.level.players()) {
                if (player.position().distanceToSqr(center) < radiusSqr) {
                    targetPlayers.add(player);
                }
            }

            if (!targetPlayers.isEmpty()) {
                NetworkHandler.sendToPlayers(targetPlayers, packet);
            }
        }
    }

    // ================================
    // 5. 辅助与持久化 (NBT)
    // ================================

    ServerLevel getLevel() { return this.level; }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return GraphProcessSerializer.save(this, tag, provider);
    }

    public static GraphProcess load(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        return GraphProcessSerializer.load(tag, index, provider);
    }
}