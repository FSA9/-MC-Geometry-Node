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

    // --- 内存模型 (进程级共享) ---
    private final Deque<Object[]> variableStack = new LinkedList<>();         // 变量作用域栈
    private final Map<String, Object> tempData = new HashMap<>();             // 瞬时数据黑板 (如 handled 标记)

    // --- 执行流管理 ---
    private final List<ExecutionThread> sleepingThreads = new ArrayList<>();  // 挂起的协程线程
    private boolean needsTimeRebase = false;                                  // 读档标记

    private final java.util.concurrent.ConcurrentLinkedQueue<ExecutionThread> THREAD_POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * 从池中借用一个线程，如果没有多余的才去 new (按需扩容)
     */
    private ExecutionThread borrowThread(int startNodeId) {
        ExecutionThread thread = THREAD_POOL.poll();
        if (thread == null) {
            // 池子空了，只有这种极端情况才分配新内存
            thread = this.new ExecutionThread(startNodeId);
        } else {
            // 重置脏数据 (洗盘子)
            thread.reset(startNodeId);
        }
        return thread;
    }

    /**
     * 将用完的线程洗干净还回池子
     */
    private void recycleThread(ExecutionThread thread) {
        THREAD_POOL.offer(thread);
    }

    // ================================
    // 2. 构造与环境
    // ================================

    public GraphProcess(String graphId, RuntimeGraphIndex index) {
        this.graphId = graphId;
        this.index = index;
        this.variableStack.push(new Object[16]); // 初始化全局作用域
    }

    public void setEnvironment(ServerLevel level, @Nullable Entity entity) {
        this.level = level;
        this.entity = entity;
    }

    public String getGraphId() { return graphId; }
    public RuntimeGraphIndex getIndex() { return index; }

    // ================================
    // 3. 执行调度入口 (核心优化点)
    // ================================

    /**
     * [派发事件线程]
     * 为蓝图入口分配一个独立的执行线程。支持多事件并发执行。
     */
    public void executeEvent(int startNodeId, @Nullable Consumer<ExecutionThread> initializer) {
        if (this.level == null) return;

        // 第二阶段优化预留：此处可从 ExecutionThread 池中 borrow
        ExecutionThread thread = borrowThread(startNodeId);

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
            for (ExecutionThread thread : sleepingThreads) {
                thread.wakeUpTick = currentWorldTick + thread.wakeUpTick;
            }
            this.needsTimeRebase = false;
        }

        // 2. 唤醒到期的线程
        Iterator<ExecutionThread> it = sleepingThreads.iterator();
        while (it.hasNext()) {
            ExecutionThread thread = it.next();
            if (currentWorldTick >= thread.wakeUpTick) {
                it.remove();
                thread.state = ExecutionThread.State.RUNNING;
                thread.run(); // 继续跑剩下的逻辑
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
        private int currentFlowId;
        private int activeNodeId = -1;
        public long wakeUpTick = -1; // 仅在 WAITING 状态有效

        // --- 线程私有寄存器 (Zero-Allocation 核心) ---
        private final IntArrayList executionStack = new IntArrayList();
        private Object[] eventRegisters = new Object[16];
        private final Long2ObjectOpenHashMap<Object> frameCache = new Long2ObjectOpenHashMap<>();
        private final IntOpenHashSet recursionGuard = new IntOpenHashSet();

        public ExecutionThread(int startNodeId) {
            this.currentFlowId = startNodeId;
        }

        /**
         * [洗盘子] 重置线程状态，准备下一次复用
         */
        public void reset(int startNodeId) {
            this.state = State.RUNNING;
            this.currentFlowId = startNodeId;
            this.activeNodeId = -1;
            this.wakeUpTick = -1;
            this.executionStack.clear();
            this.frameCache.clear();
            this.recursionGuard.clear();
            Arrays.fill(this.eventRegisters, null); // 清空上一次的事件参数
        }

        /**
         * 启动或恢复执行流
         */
        public void run() {
            int steps = 0;
            final int MAX_STEPS = 1000;

            // 每轮执行前清理瞬时缓存
            frameCache.clear();
            recursionGuard.clear();

            while ((currentFlowId != -1 || !executionStack.isEmpty()) && state == State.RUNNING) {
                if (currentFlowId == -1) {
                    currentFlowId = executionStack.removeInt(0);
                }

                if (steps++ > MAX_STEPS) {
                    state = State.ERROR;
                    return;
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

                    ExecutionResult result = logic.execute(this); // 传入线程上下文

                    this.activeNodeId = previousActive;
                    handleExecutionResult(result);

                } catch (Exception e) {
                    System.err.println("[GraphVM] Critical error in node " + index.getIdToString(currentFlowId));
                    e.printStackTrace();
                    state = State.ERROR;
                }
            }

            if (currentFlowId == -1 && executionStack.isEmpty() && state != State.WAITING) {
                state = State.FINISHED;
                // 第二阶段优化预留：此处可 return 到对象池
            }

            if (currentFlowId == -1 && executionStack.isEmpty() && state != State.WAITING) {
                this.state = State.FINISHED;

                // 用完自动还给对象池！
                GraphProcess.this.recycleThread(this);
            }
        }

        private void handleExecutionResult(ExecutionResult result) {
            switch (result) {
                case ExecutionResult.Next next -> {
                    this.currentFlowId = index.findFlowTarget(currentFlowId, next.outputPortName());
                }
                case ExecutionResult.Call call -> {
                    List<String> ports = call.outputPorts();
                    for (int i = ports.size() - 1; i >= 0; i--) {
                        int targetId = index.findFlowTarget(currentFlowId, ports.get(i));
                        if (targetId != -1) this.executionStack.add(0, targetId);
                    }
                    this.currentFlowId = executionStack.isEmpty() ? -1 : executionStack.removeInt(0);
                }
                case ExecutionResult.Wait wait -> {
                    this.wakeUpTick = level.getGameTime() + wait.ticks();
                    int nextId = index.findFlowTarget(currentFlowId, wait.nextPortName());
                    if (nextId != -1) this.executionStack.add(0, nextId);

                    this.currentFlowId = -1;
                    this.state = State.WAITING;
                    GraphProcess.this.sleepingThreads.add(this); // 挂起到进程
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
            int portId = index.getOrRegisterKey(portName);
            long cacheKey = ((long) nodeId << 32) | (portId & 0xFFFFFFFFL);

            if (frameCache.containsKey(cacheKey)) return frameCache.get(cacheKey);
            if (recursionGuard.contains(nodeId)) return null;

            recursionGuard.add(nodeId);
            int prevActive = this.activeNodeId;

            try {
                BaseNode logic = NodeRegistry.INSTANCE.get(index.getNodeType(nodeId));
                if (logic == null) return null;

                this.activeNodeId = nodeId;
                Object result = logic.compute(this, portName);

                frameCache.put(cacheKey, result);
                return result;
            } finally {
                this.activeNodeId = prevActive;
                recursionGuard.remove(nodeId);
            }
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
            int id = index.getOrRegisterKey(name);
            for (Object[] scope : GraphProcess.this.variableStack) {
                if (id < scope.length && scope[id] != null) {
                    Object val = scope[id];
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
            Object[] scope = GraphProcess.this.variableStack.peek();
            if (scope == null) return;
            int id = index.getOrRegisterKey(name);
            scope = ensureCapacity(scope, id);
            if (value == null) {
                scope[id] = null;
            } else if (VariableRegistry.isSupported(value)) {
                scope[id] = (value instanceof Entity ent) ? ent.getUUID() : value;
            }
            // 弹出旧的压回扩容后的 (Zero-allocation 策略下此处可优化为固定大小数组)
            GraphProcess.this.variableStack.pop();
            GraphProcess.this.variableStack.push(scope);
        }

        @Override
        public Object getEventData(String key) {
            int id = index.getOrRegisterKey(key);
            if (id >= eventRegisters.length) return null;
            Object val = eventRegisters[id];
            if (val instanceof UUID uuid && level != null) {
                Entity res = level.getEntity(uuid);
                return (res == null || res.isRemoved()) ? null : res;
            }
            return val;
        }

        @Override
        public void setEventData(String key, Object value) {
            int id = index.getOrRegisterKey(key);
            eventRegisters = ensureCapacity(eventRegisters, id);
            eventRegisters[id] = (value instanceof Entity ent) ? ent.getUUID() : value;
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
        public Object getNodeProperty(String key) {
            return (activeNodeId != -1) ? index.getNodeProperty(activeNodeId, key) : null;
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
        public void clearFrameCache() { frameCache.clear(); }

        @Override
        public void executeBranchSync(String portName) {
            if (activeNodeId == -1) return;
            int targetId = index.findFlowTarget(activeNodeId, portName);
            if (targetId == -1) return;

            int savedId = this.currentFlowId;
            IntArrayList savedStack = new IntArrayList(this.executionStack);

            this.currentFlowId = targetId;
            this.executionStack.clear();
            this.run(); // 递归执行

            this.currentFlowId = savedId;
            this.executionStack.clear();
            this.executionStack.addAll(savedStack);
        }

        @Override
        public void setTempData(String key, Object value) { GraphProcess.this.tempData.put(key, value); }

        @Override
        public Object getTempData(String key) { return GraphProcess.this.tempData.get(key); }

        @Override
        public void removeTempData(String key) { GraphProcess.this.tempData.remove(key); }

        @Override
        public int getCurrentNodeId() { return this.activeNodeId; }

        @Override
        public void scheduleNode(int nodeId, long delayTicks) {
            if (level == null) return;
            ExecutionThread delayThread = new ExecutionThread(nodeId);
            delayThread.wakeUpTick = level.getGameTime() + delayTicks;
            delayThread.state = State.WAITING;
            // 复制当前线程的上下文数据
            System.arraycopy(this.eventRegisters, 0, delayThread.eventRegisters, 0, this.eventRegisters.length);
            GraphProcess.this.sleepingThreads.add(delayThread);
        }

        @Override
        public void broadcastVisual(String type, int srcId, Vec3 start, int tgtId, Vec3 end, int color, float size, int ticks) {
            if (level == null) return;
            // 网络广播逻辑 (略，保持原有逻辑)
        }

        @Override
        public void broadcastDynamicVisual(String type, int color, int ticks, Map<String, String> exprs, Map<String, String> binds, CompoundTag extra) {
            if (level == null) return;
            // 网络广播逻辑 (略，保持原有逻辑)
        }
    }

    // ================================
    // 5. 辅助与持久化 (NBT)
    // ================================

    private Object[] ensureCapacity(Object[] arr, int requiredIndex) {
        if (requiredIndex >= arr.length) {
            return Arrays.copyOf(arr, Math.max(arr.length * 2, requiredIndex + 1));
        }
        return arr;
    }

    /**
     * [存档]
     * 只需要保存变量栈和那些正在休眠的 ExecutionThread。
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("GraphId", graphId);

        // 1. 保存变量栈 (由近及远)
        ListTag stackTag = new ListTag();
        Iterator<Object[]> it = variableStack.descendingIterator();
        while (it.hasNext()) {
            CompoundTag scopeTag = new CompoundTag();
            saveVariablesToTag(scopeTag, it.next(), provider);
            stackTag.add(scopeTag);
        }
        tag.put("VariableStack", stackTag);

        // 2. 保存休眠线程
        if (!sleepingThreads.isEmpty()) {
            ListTag threadsTag = new ListTag();
            for (ExecutionThread thread : sleepingThreads) {
                CompoundTag tTag = new CompoundTag();
                long remaining = (level != null) ? Math.max(0, thread.wakeUpTick - level.getGameTime()) : thread.wakeUpTick;
                tTag.putLong("WaitRemaining", remaining);
                tTag.putString("ResumeNodeId", index.getIdToString(thread.currentFlowId != -1 ? thread.currentFlowId : thread.executionStack.getInt(0)));
                // 保存现场寄存器
                CompoundTag regTag = new CompoundTag();
                saveVariablesToTag(regTag, thread.eventRegisters, provider);
                tTag.put("Registers", regTag);
                threadsTag.add(tTag);
            }
            tag.put("SleepingThreads", threadsTag);
        }
        return tag;
    }

    private void saveVariablesToTag(CompoundTag tag, Object[] scope, HolderLookup.Provider provider) {
        for (int i = 0; i < scope.length; i++) {
            if (scope[i] != null) {
                String key = index.getKeyFromId(i);
                Tag s = VariableRegistry.toTag(scope[i], provider);
                if (s != null && key != null) tag.put(key, s);
            }
        }
    }

    // 读档构造函数 (配套重写)
    public GraphProcess(CompoundTag tag, RuntimeGraphIndex index, HolderLookup.Provider provider) {
        this.index = index;
        this.graphId = tag.getString("GraphId");

        // 1. 恢复变量栈
        this.variableStack.clear();
        if (tag.contains("VariableStack", Tag.TAG_LIST)) {
            ListTag list = tag.getList("VariableStack", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Object[] scope = loadVariablesFromTag(list.getCompound(i), new Object[16], provider);
                this.variableStack.addLast(scope);
            }
        } else {
            this.variableStack.push(new Object[16]);
        }

        // 2. 恢复线程
        if (tag.contains("SleepingThreads", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SleepingThreads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompound(i);
                int resumeId = index.getStringToId(tTag.getString("ResumeNodeId"));
                if (resumeId != -1) {
                    ExecutionThread thread = new ExecutionThread(resumeId);
                    thread.wakeUpTick = tTag.getLong("WaitRemaining");
                    thread.state = ExecutionThread.State.WAITING;
                    loadVariablesFromTag(tTag.getCompound("Registers"), thread.eventRegisters, provider);
                    this.sleepingThreads.add(thread);
                }
            }
            this.needsTimeRebase = true;
        }
    }

    private Object[] loadVariablesFromTag(CompoundTag tag, Object[] scope, HolderLookup.Provider provider) {
        for (String key : tag.getAllKeys()) {
            Object obj = VariableRegistry.fromTag(tag.get(key), provider);
            if (obj != null) {
                int id = index.getOrRegisterKey(key);
                scope = ensureCapacity(scope, id);
                scope[id] = obj;
            }
        }
        return scope;
    }
}