package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.data.GraphDataEvaluationSession;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitHandler;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitHandlerRegistry;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.graph.value.GraphEntityReferenceResolver;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.definition.port.PortConversionRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.utils.RateLimitedLog;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * [蓝图虚拟机进程 - 内存主板]
 * * 代表一个蓝图图纸在某个实体或维度上的运行实例。
 * 它维护运行环境、等待线程和分支联结；具体执行由内部类 ExecutionThread 承担。
 */
public class BlueprintProcess {

    // ================================
    // 1. 核心持久化状态
    // ================================

    private final String graphId;
    private final BlueprintPlan index;

    // --- 环境上下文 ---
    private ServerLevel level;
    private UUID entityUuid;
    private UUID graphOwnerEntityUuid;

    // --- 执行流管理 ---
    final PriorityQueue<ExecutionThread> sleepingThreads = new PriorityQueue<>(Comparator.comparingLong(t -> t.wakeUpTick));  // 挂起的协程线程
    private final Set<ExecutionThread> externalWaitingThreads = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ExecutionThread> liveThreads = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<ExecutionThread> readyThreads = new ArrayDeque<>();
    private boolean dispatchingReadyThreads;
    boolean needsTimeRebase = false;  // 读档标记
    private final Map<String, BranchJoin> branchJoins = new HashMap<>();
    private Runnable tickScheduleCallback = () -> {};
    private boolean shutDown;
    private boolean draining;
    @Nullable
    private Runnable drainCompletion;

    private static final int MAX_POOLED_THREADS = 128;
    private final ArrayDeque<ExecutionThread> THREAD_POOL = new ArrayDeque<>();

    static final class BranchJoin {
        final String id;
        final int completionNodeId;
        final String completionEntryPort;
        final int eventSourceNodeId;
        final String threadDimensionId;
        final UUID threadEntityUuid;
        final Object[] eventRegisters;
        final Map<String, Object> dynamicEventData;
        final Map<String, Object> tempData;
        int pendingChildren;
        boolean launchFinished;

        BranchJoin(String id,
                   int completionNodeId,
                   String completionEntryPort,
                   int eventSourceNodeId,
                   @Nullable String threadDimensionId,
                   @Nullable UUID threadEntityUuid,
                   Object[] eventRegisters,
                   @Nullable Map<String, Object> dynamicEventData,
                   Map<String, Object> tempData) {
            this.id = id;
            this.completionNodeId = completionNodeId;
            this.completionEntryPort = completionEntryPort;
            this.eventSourceNodeId = eventSourceNodeId;
            this.threadDimensionId = threadDimensionId;
            this.threadEntityUuid = threadEntityUuid;
            this.eventRegisters = GraphValueSnapshot.snapshotElements(eventRegisters);
            this.dynamicEventData = dynamicEventData != null
                    ? GraphValueSnapshot.snapshotValues(dynamicEventData) : null;
            this.tempData = GraphValueSnapshot.snapshotValues(tempData);
        }
    }

    private ExecutionThread borrowThread(int startNodeId, String startPortName) {
        ExecutionThread thread = THREAD_POOL.poll();
        if (thread == null) {
            thread = this.new ExecutionThread(startNodeId, startPortName);
        } else {
            thread.reset(startNodeId, startPortName);
        }
        activateThread(thread);
        return thread;
    }

    private void recycleThread(ExecutionThread thread) {
        liveThreads.remove(thread);
        if (!shutDown && THREAD_POOL.size() < MAX_POOLED_THREADS) {
            THREAD_POOL.offer(thread);
        }
        checkDrainCompletion();
    }

    private void activateThread(ExecutionThread thread) {
        if (thread != null) {
            liveThreads.add(thread);
        }
    }

    private void dispatchThread(ExecutionThread thread) {
        if (thread == null || shutDown) {
            return;
        }
        readyThreads.addLast(thread);
        if (dispatchingReadyThreads) {
            return;
        }
        dispatchingReadyThreads = true;
        try {
            ExecutionThread ready;
            while (!shutDown && (ready = readyThreads.pollFirst()) != null) {
                ready.run();
            }
        } finally {
            dispatchingReadyThreads = false;
            if (shutDown) {
                readyThreads.clear();
            }
        }
    }

    // ================================
    // 2. 构造与环境
    // ================================

    public BlueprintProcess(String graphId, BlueprintPlan index) {
        this.graphId = GraphAssetId.require(graphId);
        this.index = Objects.requireNonNull(index, "index");
    }

    public void setEnvironment(ServerLevel level, @Nullable Entity entity) {
        this.level = level;
        this.entityUuid = entity != null ? entity.getUUID() : null;
    }

    public void setGraphOwner(@Nullable Entity owner) {
        this.graphOwnerEntityUuid = owner != null ? owner.getUUID() : null;
    }

    public String getGraphId() { return graphId; }
    public BlueprintPlan getIndex() { return index; }
    @Nullable
    public Entity getEntity() {
        return level != null ? GraphEntityReferenceResolver.resolve(entityUuid, level) : null;
    }

    @Nullable
    public Object evaluateDataOutput(int nodeId, String portName) {
        if (this.shutDown || this.draining || this.level == null || portName == null || portName.isBlank()
                || nodeId < 0 || nodeId >= index.getNodeCount()) {
            return null;
        }

        ExecutionThread thread = borrowThread(nodeId, "flow_in");
        try {
            return thread.evaluateDataOutput(nodeId, portName);
        } catch (Exception e) {
            if (RateLimitedLog.acquire(diagnosticKey("data_output", nodeId, portName))) {
                GeometryNode.LOGGER.error("Blueprint data output evaluation failed: graph={}, node={}, port={}",
                        graphId, index.getNodeId(nodeId), portName, e);
            }
            return null;
        } finally {
            thread.finishDetachedEvaluation();
        }
    }

    public void setTickScheduleCallback(@Nullable Runnable callback) {
        this.tickScheduleCallback = callback != null ? callback : () -> {};
    }

    public long getNextRequiredTick() {
        if (needsTimeRebase) {
            return Long.MIN_VALUE;
        }
        ExecutionThread thread = sleepingThreads.peek();
        return thread != null ? thread.wakeUpTick : Long.MAX_VALUE;
    }

    private void notifyTickScheduleChanged() {
        tickScheduleCallback.run();
    }

    public Iterable<ExecutionThread> getSleepingThreadsForSerialization() {
        return sleepingThreads;
    }

    public boolean hasSleepingThreadsForSerialization() {
        return !sleepingThreads.isEmpty();
    }

    public void addSleepingThreadForSerialization(ExecutionThread thread) {
        activateThread(thread);
        sleepingThreads.add(thread);
        notifyTickScheduleChanged();
    }

    public boolean isDraining() {
        return draining && !shutDown;
    }

    public void requestDrain(Runnable completion) {
        if (shutDown) {
            if (completion != null) completion.run();
            return;
        }
        draining = true;
        drainCompletion = completion;
        checkDrainCompletion();
    }

    public void restoreDrainingForSerialization(boolean draining) {
        this.draining = draining;
    }

    private void checkDrainCompletion() {
        if (!draining || shutDown || !liveThreads.isEmpty() || drainCompletion == null) {
            return;
        }
        Runnable completion = drainCompletion;
        drainCompletion = null;
        completion.run();
    }

    public Collection<BranchJoin> getBranchJoinsForSerialization() {
        return branchJoins.values();
    }

    public boolean hasBranchJoinsForSerialization() {
        return !branchJoins.isEmpty();
    }

    public void clearBranchJoinsForSerialization() {
        branchJoins.clear();
    }

    public void addBranchJoinForSerialization(BranchJoin join) {
        branchJoins.put(join.id, join);
    }

    public void markNeedsTimeRebaseForSerialization() {
        needsTimeRebase = true;
        notifyTickScheduleChanged();
    }

    // ================================
    // 3. 执行调度入口
    // ================================

    /**
     * [派发事件线程]
     * 为蓝图入口分配一个独立的执行线程。支持多事件并发执行。
     */
    public void executeEvent(int startNodeId, @Nullable Map<String, Object> eventData) {
        if (this.shutDown || this.draining || this.level == null) return;

        // 调用修改后的 borrowThread，传入默认端口 "flow_in" 作为执行起点的占位
        ExecutionThread thread = borrowThread(startNodeId, "flow_in");

        thread.eventSourceNodeId = startNodeId;
        thread.applyEventData(eventData);

        dispatchThread(thread);
    }

    /**
     * [心跳驱动]
     * 负责处理读档重基准以及唤醒到期的休眠线程。
     */
    public void tick(long currentWorldTick) {
        if (this.shutDown || level == null) return;

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
                dispatchThread(thread);
            }
        }
    }

    /**
     * Terminates all suspended work before this process is replaced or unloaded.
     */
    public void shutdown(String reason) {
        if (this.shutDown) {
            return;
        }
        this.shutDown = true;
        this.draining = false;
        this.drainCompletion = null;
        String closeReason = reason == null || reason.isBlank() ? "graph_unloaded" : reason;
        branchJoins.clear();
        readyThreads.clear();
        for (ExecutionThread thread : new ArrayList<>(liveThreads)) {
            thread.cancelForShutdown(closeReason);
        }
        externalWaitingThreads.clear();
        sleepingThreads.clear();
        THREAD_POOL.clear();
        notifyTickScheduleChanged();
    }

    private void onThreadFinished(ExecutionThread thread) {
        String parentJoinId = thread.parentJoinId;
        if (parentJoinId == null) {
            return;
        }
        thread.parentJoinId = null;

        BranchJoin join = branchJoins.get(parentJoinId);
        if (join == null) {
            return;
        }
        if (join.pendingChildren > 0) {
            join.pendingChildren--;
        }
        tryCompleteBranchJoin(join);
    }

    private void finishBranchJoin(String joinId) {
        if (joinId == null || joinId.isBlank()) {
            return;
        }
        BranchJoin join = branchJoins.get(joinId);
        if (join == null) {
            return;
        }
        join.launchFinished = true;
        tryCompleteBranchJoin(join);
    }

    private void tryCompleteBranchJoin(BranchJoin join) {
        if (!join.launchFinished || join.pendingChildren > 0) {
            return;
        }
        if (branchJoins.remove(join.id) == null) {
            return;
        }

        if (join.completionNodeId < 0 || join.completionEntryPort == null
                || join.completionEntryPort.isBlank()) {
            return;
        }

        ExecutionThread completionThread = borrowThread(join.completionNodeId, join.completionEntryPort);
        completionThread.restoreEnvironment(join.threadDimensionId, join.threadEntityUuid);
        completionThread.parentJoinId = null;
        completionThread.eventSourceNodeId = join.eventSourceNodeId;
        completionThread.eventRegisters = GraphValueSnapshot.snapshotElements(join.eventRegisters);
        completionThread.dynamicEventData = join.dynamicEventData != null
                ? GraphValueSnapshot.snapshotValues(join.dynamicEventData) : null;
        GraphValueSnapshot.putSnapshotValues(completionThread.tempData, join.tempData);
        dispatchThread(completionThread);
    }

    // ================================
    // 4. 内部执行线程 (ExecutionContext 实现)
    // ================================

    /**
     * 代表一次独立的蓝图指令流。
     * 拥有私有的栈、私有的寄存器、私有的运算缓存，彻底杜绝重入污染。
     */
    public class ExecutionThread implements ExecutionContext, GraphExecutionHandle {

        public enum State { RUNNING, WAITING, EXTERNAL_WAITING, FINISHED, ERROR }

        public State state = State.RUNNING;
        int currentFlowId;
        String currentEntryPort;
        private int activeNodeId = -1;
        private int externalWaitNodeId = -1;
        @Nullable
        private ExternalWaitHandler externalWaitHandler;
        public long wakeUpTick = -1; // 仅在 WAITING 状态有效
        private int runDepth = 0;
        private ServerLevel threadLevel;
        private String threadDimensionId;
        private UUID threadEntityUuid;
        private boolean pooled = false;
        @Nullable
        private String parentJoinId;
        private int eventSourceNodeId = -1;

        // --- 线程私有寄存器 (Zero-Allocation 核心) ---
        final List<BlueprintPlan.IntFlowTarget> executionStack = new ArrayList<>();
        Object[] eventRegisters = new Object[BlueprintProcess.this.index.getRegisterCount() + 8];
        Map<String, Object> dynamicEventData = null;
        private final GraphDataEvaluationSession dataEvaluation =
                new GraphDataEvaluationSession(BlueprintProcess.this.index);
        private final GraphDataEvaluationSession.NodeEvaluator dataNodeEvaluator = this::computeDataNode;
        // ✨ 新增：线程私有的临时黑板
        public final Map<String, Object> tempData = new HashMap<>();

        public ExecutionThread(int startNodeId, String startPortName) {
            this.currentFlowId = startNodeId;
            this.currentEntryPort = startPortName;
            captureEnvironment();
        }

        @Override
        public String getEntryPort() {
            return this.currentEntryPort;
        }

        /**
         * [洗盘子] 重置线程状态，准备下一次复用
         */
        public void reset(int startNodeId, String startPortName) {
            this.pooled = false;
            this.state = State.RUNNING;
            this.currentFlowId = startNodeId;
            this.currentEntryPort = startPortName;
            this.activeNodeId = -1;
            this.externalWaitNodeId = -1;
            this.externalWaitHandler = null;
            this.wakeUpTick = -1;
            this.runDepth = 0;
            this.parentJoinId = null;
            this.eventSourceNodeId = -1;
            this.executionStack.clear();
            this.dataEvaluation.reset();
            this.tempData.clear();
            Arrays.fill(this.eventRegisters, null);
            if (this.dynamicEventData != null) this.dynamicEventData.clear();
            captureEnvironment();
        }

        void captureEnvironment() {
            this.threadLevel = BlueprintProcess.this.level;
            this.threadDimensionId = this.threadLevel != null ? this.threadLevel.dimension().identifier().toString() : null;
            this.threadEntityUuid = BlueprintProcess.this.entityUuid;
        }

        public void restoreEnvironment(@Nullable ServerLevel level, @Nullable UUID entityUuid) {
            this.threadLevel = level;
            this.threadDimensionId = level != null ? level.dimension().identifier().toString() : null;
            this.threadEntityUuid = entityUuid;
        }

        public void restoreEnvironment(@Nullable String dimensionId, @Nullable UUID entityUuid) {
            this.threadLevel = null;
            this.threadDimensionId = dimensionId;
            this.threadEntityUuid = entityUuid;
        }

        @Nullable
        public String getThreadDimensionId() {
            if (this.threadDimensionId != null) return this.threadDimensionId;
            return this.threadLevel != null ? this.threadLevel.dimension().identifier().toString() : null;
        }

        @Nullable
        public UUID getThreadEntityUuid() {
            return this.threadEntityUuid;
        }

        public int getCurrentFlowIdForSerialization() {
            return currentFlowId;
        }

        @Nullable
        public String getCurrentEntryPortForSerialization() {
            return currentEntryPort;
        }

        public List<BlueprintPlan.IntFlowTarget> getExecutionStackForSerialization() {
            return executionStack;
        }

        public Object[] getEventRegistersForSerialization() {
            return eventRegisters;
        }

        public void setEventRegistersForSerialization(Object[] eventRegisters) {
            this.eventRegisters = GraphValueSnapshot.snapshotElements(eventRegisters);
        }

        public int getEventSourceNodeIdForSerialization() {
            return eventSourceNodeId;
        }

        public void setEventSourceNodeIdForSerialization(int eventSourceNodeId) {
            this.eventSourceNodeId = eventSourceNodeId;
        }

        @Nullable
        public Map<String, Object> getDynamicEventDataForSerialization() {
            return dynamicEventData;
        }

        public void setDynamicEventDataForSerialization(@Nullable Map<String, Object> dynamicEventData) {
            this.dynamicEventData = dynamicEventData != null
                    ? GraphValueSnapshot.snapshotValues(dynamicEventData) : null;
        }

        @Nullable
        public String getParentJoinIdForSerialization() {
            return parentJoinId;
        }

        public void setParentJoinIdForSerialization(@Nullable String parentJoinId) {
            this.parentJoinId = parentJoinId;
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
                dataEvaluation.beginEpoch();
            }

            try {
                while ((currentFlowId != -1 || !executionStack.isEmpty()) && state == State.RUNNING) {
                    if (currentFlowId == -1) {
                        BlueprintPlan.IntFlowTarget frame = executionStack.remove(executionStack.size() - 1);
                        currentFlowId = frame.targetNodeId();
                        currentEntryPort = frame.targetPortName();
                    }

                    if (steps++ > MAX_STEPS) {
                        if (RateLimitedLog.acquire(diagnosticKey("step_limit", currentFlowId, null))) {
                            GeometryNode.LOGGER.error("Blueprint synchronous step limit exceeded: graph={}, node={}",
                                    graphId, index.getNodeId(currentFlowId));
                        }
                        state = State.ERROR;
                        return; // return 会自动触发 finally 块的清理
                    }

                    String nodeType = index.getNodeType(currentFlowId);
                    BaseNode logic = NodeRegistry.INSTANCE.get(nodeType);
                    if (logic == null) {
                        if (RateLimitedLog.acquire(diagnosticKey("missing_execution_node", currentFlowId, nodeType))) {
                            GeometryNode.LOGGER.error("Blueprint execution node type is unavailable: graph={}, node={}, type={}",
                                    graphId, index.getNodeId(currentFlowId), nodeType);
                        }
                        state = State.ERROR;
                        currentFlowId = -1;
                        executionStack.clear();
                        return;
                    }

                    try {
                        int previousActive = this.activeNodeId;
                        this.activeNodeId = currentFlowId;

                        ExecutionResult result = logic.execute(this);

                        this.activeNodeId = previousActive;
                        if (BlueprintProcess.this.shutDown) {
                            this.currentFlowId = -1;
                            this.executionStack.clear();
                            this.state = State.FINISHED;
                        } else {
                            handleExecutionResult(result);
                        }

                    } catch (Exception e) {
                        if (RateLimitedLog.acquire(diagnosticKey("node_execution", currentFlowId,
                                e.getClass().getName()))) {
                            GeometryNode.LOGGER.error("Blueprint node execution failed: graph={}, node={}, type={}",
                                    graphId, index.getNodeId(currentFlowId), nodeType, e);
                        }
                        state = State.ERROR;
                        // [新增修正] 节点抛出异常时强制断开执行流，避免死循环
                        currentFlowId = -1;
                        executionStack.clear();
                    }
                }
            } finally {
                runDepth--;
                if (runDepth == 0 && currentFlowId == -1 && executionStack.isEmpty()
                        && state != State.WAITING && state != State.EXTERNAL_WAITING) {
                    if (this.state != State.ERROR) {
                        this.state = State.FINISHED;
                    }
                    BlueprintProcess.this.onThreadFinished(this);
                    recycleIfNeeded();
                }
            }
        }

        private void handleExecutionResult(ExecutionResult result) {
            switch (result) {
                case ExecutionResult.Next next -> {
                    BlueprintPlan.IntFlowTarget target = index.findFlowTarget(currentFlowId, next.outputPortName());
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
                        BlueprintPlan.IntFlowTarget target = index.findFlowTarget(currentFlowId, ports.get(i));
                        if (target != null) this.executionStack.add(target);
                    }
                    if (executionStack.isEmpty()) {
                        this.currentFlowId = -1;
                    } else {
                        // 从尾部弹出
                        BlueprintPlan.IntFlowTarget frame = executionStack.remove(executionStack.size() - 1);
                        this.currentFlowId = frame.targetNodeId();
                        this.currentEntryPort = frame.targetPortName();
                    }
                }
                case ExecutionResult.Wait wait -> {
                    ServerLevel currentLevel = getLevel();
                    if (currentLevel == null) {
                        this.state = State.ERROR;
                        this.currentFlowId = -1;
                        this.executionStack.clear();
                        return;
                    }

                    this.wakeUpTick = currentLevel.getGameTime() + wait.ticks();
                    BlueprintPlan.IntFlowTarget target = index.findFlowTarget(currentFlowId, wait.nextPortName());
                    if (target != null) this.executionStack.add(target);

                    this.currentFlowId = -1;
                    this.state = State.WAITING;
                    BlueprintProcess.this.sleepingThreads.add(this);
                    BlueprintProcess.this.notifyTickScheduleChanged();
                }
                case ExecutionResult.ExternalWait externalWait -> {
                    ExternalWaitHandler handler = ExternalWaitHandlerRegistry.INSTANCE
                            .get(externalWait.handlerId());
                    if (handler == null) {
                        this.state = State.ERROR;
                        this.currentFlowId = -1;
                        this.executionStack.clear();
                        return;
                    }

                    this.externalWaitNodeId = this.currentFlowId;
                    this.externalWaitHandler = handler;
                    this.currentFlowId = -1;
                    this.state = State.EXTERNAL_WAITING;
                    BlueprintProcess.this.externalWaitingThreads.add(this);

                    boolean beganWaiting;
                    try {
                        beganWaiting = handler.beginExternalWait(this, externalWait.request());
                    } catch (RuntimeException | Error exception) {
                        failExternalWait();
                        throw exception;
                    }
                    if (!beganWaiting) {
                        failExternalWait();
                    }
                }
                case ExecutionResult.Finish ignored -> this.currentFlowId = -1;
                case ExecutionResult.Error err -> {
                    this.state = State.ERROR;
                    this.currentFlowId = -1;
                    this.executionStack.clear();
                }
            }
        }

        public boolean resumeExternalWait(String outputPortName) {
            if (this.state != State.EXTERNAL_WAITING || this.externalWaitNodeId == -1) {
                return false;
            }

            BlueprintProcess.this.externalWaitingThreads.remove(this);
            ExternalWaitHandler handler = this.externalWaitHandler;
            BlueprintPlan.IntFlowTarget target = index.findFlowTarget(this.externalWaitNodeId, outputPortName);
            if (handler != null) {
                ExternalWaitHandler.Completion completion = target != null
                        ? ExternalWaitHandler.Completion.RESUMED
                        : ExternalWaitHandler.Completion.NO_TARGET;
                handler.completeExternalWait(this, outputPortName, completion);
            }
            this.externalWaitNodeId = -1;
            this.externalWaitHandler = null;
            this.wakeUpTick = -1;

            if (target == null) {
                this.currentFlowId = -1;
                this.executionStack.clear();
                this.state = State.FINISHED;
                BlueprintProcess.this.onThreadFinished(this);
                recycleIfNeeded();
                return false;
            }

            this.currentFlowId = target.targetNodeId();
            this.currentEntryPort = target.targetPortName();
            this.state = State.RUNNING;
            if (this.runDepth == 0) {
                BlueprintProcess.this.dispatchThread(this);
            }
            return true;
        }

        @Override
        public boolean resume(String outputPortName) {
            return resumeExternalWait(outputPortName);
        }

        @Override
        public String graphId() {
            return getGraphId();
        }

        @Override
        public ServerLevel level() {
            return getLevel();
        }

        @Override
        public boolean isActive() {
            return !BlueprintProcess.this.shutDown
                    && (this.state == State.RUNNING || this.state == State.WAITING || this.state == State.EXTERNAL_WAITING);
        }

        @Override
        public void close() {
            abort("closed");
        }

        @Override
        public void abort(String reason) {
            ExternalWaitHandler handler = this.state == State.EXTERNAL_WAITING
                    ? this.externalWaitHandler : null;
            this.externalWaitHandler = null;
            BlueprintProcess.this.externalWaitingThreads.remove(this);
            this.externalWaitNodeId = -1;
            this.currentFlowId = -1;
            this.executionStack.clear();
            this.state = State.FINISHED;
            if (BlueprintProcess.this.sleepingThreads.remove(this)) {
                BlueprintProcess.this.notifyTickScheduleChanged();
            }
            if (handler != null) {
                handler.endExternalWait(this, reason);
            }
            BlueprintProcess.this.onThreadFinished(this);
            recycleIfNeeded();
        }

        private void cancelForShutdown(String reason) {
            if (this.state == State.WAITING || this.state == State.EXTERNAL_WAITING) {
                abort(reason);
                return;
            }
            this.currentFlowId = -1;
            this.executionStack.clear();
            this.state = State.FINISHED;
        }

        private void failExternalWait() {
            BlueprintProcess.this.externalWaitingThreads.remove(this);
            this.externalWaitNodeId = -1;
            this.externalWaitHandler = null;
            this.state = State.ERROR;
            this.executionStack.clear();
        }

        @Override
        public Object unwrap() {
            return this;
        }

        private void recycleIfNeeded() {
            if (!this.pooled) {
                this.pooled = true;
                BlueprintProcess.this.recycleThread(this);
            }
        }

        private Object executeDataNode(int nodeId, String portName) {
            return dataEvaluation.evaluate(nodeId, portName, dataNodeEvaluator);
        }

        private Object resolveConnectedInput(CompiledDataIndex.DataConnectionSource source) {
            Object value = executeDataNode(source.sourceNodeId(), source.sourcePortName());
            return PortConversionRegistry.convert(value, source.sourceType(),
                    source.targetType(), this);
        }

        private Object computeDataNode(int nodeId, String portName) {
            int prevActive = this.activeNodeId;
            try {
                if (index.isDataPassthroughOutput(nodeId, portName)) {
                    CompiledDataIndex.DataConnectionSource source =
                            index.findDataInput(nodeId, portName);
                    return source != null
                            ? resolveConnectedInput(source)
                            : index.getStaticInput(nodeId, portName);
                }
                BaseNode logic = NodeRegistry.INSTANCE.get(index.getNodeType(nodeId));
                if (logic == null) {
                    if (RateLimitedLog.acquire(diagnosticKey("missing_data_node", nodeId, portName))) {
                        GeometryNode.LOGGER.error("Blueprint data node type is unavailable: graph={}, node={}, type={}, port={}",
                                graphId, index.getNodeId(nodeId), index.getNodeType(nodeId), portName);
                    }
                    return null;
                }

                this.activeNodeId = nodeId;
                return logic.compute((GraphDataContext) this, portName);
            } finally {
                this.activeNodeId = prevActive;
            }
        }

        private Object evaluateDataOutput(int nodeId, String portName) {
            dataEvaluation.beginEpoch();
            return executeDataNode(nodeId, portName);
        }

        private void finishDetachedEvaluation() {
            this.currentFlowId = -1;
            this.currentEntryPort = "flow_in";
            this.activeNodeId = -1;
            this.executionStack.clear();
            this.state = State.FINISHED;
            this.parentJoinId = null;
            recycleIfNeeded();
        }

        // ==========================================
        // ExecutionContext 实现 (隔离的执行环境)
        // ==========================================

        @Override
        public ServerLevel getLevel() {
            if (this.threadLevel != null) return this.threadLevel;
            if (this.threadDimensionId != null && BlueprintProcess.this.level != null) {
                Identifier dimensionLocation = Identifier.tryParse(this.threadDimensionId);
                if (dimensionLocation != null) {
                    ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
                    ServerLevel resolved = BlueprintProcess.this.level.getServer().getLevel(dimensionKey);
                    if (resolved != null) {
                        this.threadLevel = resolved;
                        return resolved;
                    }
                }
            }
            return BlueprintProcess.this.level;
        }

        @Override
        public Entity getEntity() {
            ServerLevel currentLevel = getLevel();
            if (this.threadEntityUuid != null && currentLevel != null) {
                return GraphEntityReferenceResolver.resolve(this.threadEntityUuid, currentLevel);
            }
            return null;
        }

        @Override
        public Entity getGraphOwnerEntity() {
            ServerLevel currentLevel = getLevel();
            UUID ownerUuid = BlueprintProcess.this.graphOwnerEntityUuid;
            if (ownerUuid != null && currentLevel != null) {
                return GraphEntityReferenceResolver.resolve(ownerUuid, currentLevel);
            }
            return null;
        }

        @Override
        public String getGraphId() { return BlueprintProcess.this.graphId; }

        @Override
        public GraphBindingKey getGraphBindingKey() {
            return new GraphBindingKey(BlueprintProcess.this.index.runtimeKind(), BlueprintProcess.this.graphId);
        }

        @Override
        public Object getEventData(String key) {
            int id = index.getPortKey(key);
            Object val = null;

            if (id != -1 && id < eventRegisters.length) {
                val = eventRegisters[id];
            } else if (id == -1 && dynamicEventData != null) {
                val = dynamicEventData.get(key);
            }

            ServerLevel currentLevel = getLevel();
            if (val instanceof UUID uuid && currentLevel != null) {
                return GraphEntityReferenceResolver.resolve(uuid, currentLevel);
            }
            return val;
        }

        @Override
        public int getEventSourceNodeId() {
            return eventSourceNodeId;
        }

        @Override
        public void setEventData(String key, Object value) {
            int id = index.getPortKey(key);
            Object finalValue = GraphValueSnapshot.snapshot(value);

            if (id != -1) {
                if (id < eventRegisters.length) eventRegisters[id] = finalValue;
            } else {
                if (dynamicEventData == null) dynamicEventData = new HashMap<>();
                dynamicEventData.put(key, finalValue);
            }
        }

        private void applyEventData(@Nullable Map<String, Object> eventData) {
            if (eventData == null || eventData.isEmpty()) return;
            for (Map.Entry<String, Object> entry : eventData.entrySet()) {
                setEventData(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Object getInputValue(String portName) {
            if (activeNodeId == -1) return null;
            CompiledDataIndex.DataConnectionSource src = index.findDataInput(activeNodeId, portName);
            if (src == null) return null;
            return resolveConnectedInput(src);
        }

        @Override
        public boolean hasInputConnection(String portName) {
            return activeNodeId != -1 && index.findDataInput(activeNodeId, portName) != null;
        }

        @Override
        public Object getStaticInput(String portName) {
            return (activeNodeId != -1) ? index.getStaticInput(activeNodeId, portName) : null;
        }

        @Override
        public boolean hasPort(String portName) {
            return activeNodeId != -1 && index.hasPort(activeNodeId, portName);
        }

        @Override
        public void setScopedState(ScopedStateTarget target, String name, Object value) {
            GraphEngineServices.INSTANCE.scopedState().set(runtimeContext(), target, name, value);
            dataEvaluation.clearValues();
        }

        @Override
        public Object getScopedState(ScopedStateTarget target, String name) {
            return GraphEngineServices.INSTANCE.scopedState().get(runtimeContext(), target, name);
        }

        @Override
        public boolean hasScopedState(ScopedStateTarget target, String name) {
            return GraphEngineServices.INSTANCE.scopedState().has(runtimeContext(), target, name);
        }

        @Override
        public boolean clearScopedState(ScopedStateTarget target, String name) {
            boolean changed = GraphEngineServices.INSTANCE.scopedState().clear(runtimeContext(), target, name);
            if (changed) dataEvaluation.clearValues();
            return changed;
        }

        @Nullable
        private GraphRuntimeContext runtimeContext() {
            ServerLevel currentLevel = getLevel();
            return currentLevel != null ? new GraphRuntimeContext(currentLevel, getEntity()) : null;
        }

        @Override
        public void clearFrameCache() {
            dataEvaluation.clearValues();
        }

        @Override
        public boolean executeBranchThenResume(String branchPortName, String resumeEntryPort) {
            if (activeNodeId == -1 || resumeEntryPort == null || resumeEntryPort.isBlank()) {
                return false;
            }
            BlueprintPlan.IntFlowTarget branchTarget = index.findFlowTarget(activeNodeId, branchPortName);
            if (branchTarget == null) {
                return false;
            }

            String joinId = createBranchJoin(activeNodeId, resumeEntryPort);
            if (!spawnBranch(branchPortName, null, joinId)) {
                BlueprintProcess.this.branchJoins.remove(joinId);
                return false;
            }
            BlueprintProcess.this.finishBranchJoin(joinId);
            return true;
        }

        @Override
        public String createBranchJoin(String completedPortName) {
            BlueprintPlan.IntFlowTarget completedTarget = activeNodeId >= 0
                    ? index.findFlowTarget(activeNodeId, completedPortName)
                    : null;
            return createBranchJoin(
                    completedTarget != null ? completedTarget.targetNodeId() : -1,
                    completedTarget != null ? completedTarget.targetPortName() : ""
            );
        }

        private String createBranchJoin(int completionNodeId, String completionEntryPort) {
            String joinId = UUID.randomUUID().toString();
            BranchJoin join = new BranchJoin(
                    joinId,
                    completionNodeId,
                    completionEntryPort,
                    this.eventSourceNodeId,
                    getThreadDimensionId(),
                    getThreadEntityUuid(),
                    this.eventRegisters,
                    this.dynamicEventData,
                    this.tempData
            );
            BlueprintProcess.this.branchJoins.put(joinId, join);
            return joinId;
        }

        @Override
        public boolean spawnBranch(String portName, @Nullable Map<String, Object> tempDataOverride, @Nullable String joinId) {
            if (activeNodeId == -1) {
                return false;
            }
            BlueprintPlan.IntFlowTarget target = index.findFlowTarget(activeNodeId, portName);
            if (target == null) {
                return false;
            }

            if (joinId != null && !joinId.isBlank()) {
                BranchJoin join = BlueprintProcess.this.branchJoins.get(joinId);
                if (join == null) {
                    return false;
                }
                join.pendingChildren++;
            }

            ExecutionThread child = BlueprintProcess.this.borrowThread(target.targetNodeId(), target.targetPortName());
            child.restoreEnvironment(getLevel(), getThreadEntityUuid());
            child.parentJoinId = joinId;
            child.eventSourceNodeId = this.eventSourceNodeId;
            child.eventRegisters = GraphValueSnapshot.snapshotElements(this.eventRegisters);
            child.dynamicEventData = this.dynamicEventData != null
                    ? GraphValueSnapshot.snapshotValues(this.dynamicEventData) : null;
            GraphValueSnapshot.putSnapshotValues(child.tempData, this.tempData);
            if (tempDataOverride != null) {
                GraphValueSnapshot.putSnapshotValues(child.tempData, tempDataOverride);
            }
            BlueprintProcess.this.dispatchThread(child);
            return true;
        }

        @Override
        public void finishBranchJoin(String joinId) {
            BlueprintProcess.this.finishBranchJoin(joinId);
        }

        @Override
        public void setTempData(String key, Object value) {
            this.tempData.put(key, GraphValueSnapshot.snapshot(value));
        }

        @Override
        public Object getTempData(String key) { return this.tempData.get(key); }

        @Override
        public void removeTempData(String key) { this.tempData.remove(key); }

        @Override
        public int getCurrentNodeId() { return this.activeNodeId; }

        @Override
        public String getCurrentNodeStableId() {
            return activeNodeId >= 0 ? index.getNodeId(activeNodeId) : null;
        }

        @Override
        public void scheduleNode(int nodeId, long delayTicks, String entryPortName) {
            ServerLevel currentLevel = getLevel();
            if (currentLevel == null) return;

            ExecutionThread delayThread = new ExecutionThread(nodeId, entryPortName);
            BlueprintProcess.this.activateThread(delayThread);
            delayThread.restoreEnvironment(currentLevel, getThreadEntityUuid());
            delayThread.wakeUpTick = currentLevel.getGameTime() + delayTicks;
            delayThread.state = State.WAITING;
            delayThread.eventSourceNodeId = this.eventSourceNodeId;

            delayThread.eventRegisters = GraphValueSnapshot.snapshotElements(this.eventRegisters);

            if (this.dynamicEventData != null) {
                delayThread.dynamicEventData = GraphValueSnapshot.snapshotValues(this.dynamicEventData);
            }

            GraphValueSnapshot.putSnapshotValues(delayThread.tempData, this.tempData);
            BlueprintProcess.this.sleepingThreads.add(delayThread);
            BlueprintProcess.this.notifyTickScheduleChanged();
        }

        @Override
        public void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                           Map<String, ExpressionData> expressions,
                                           net.minecraft.nbt.CompoundTag extraData) {

            ServerLevel currentLevel = getLevel();
            if (currentLevel == null) return;

            GraphVisualEmitter.broadcastDynamicVisual(currentLevel, effectType, color, durationTicks,
                    expressions, extraData);
        }

        @Override
        public void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                           Map<String, ExpressionData> expressions,
                                           net.minecraft.nbt.CompoundTag extraData,
                                           Vec3 center,
                                           double radius,
                                           List<GraphEngineServices.VisualAsset> assets) {
            ServerLevel currentLevel = getLevel();
            if (currentLevel == null) return;

            GraphVisualEmitter.broadcastDynamicVisual(currentLevel, effectType, color, durationTicks,
                    expressions, extraData, center, radius, assets);
        }
    }

    // ================================
    // 5. 辅助与持久化 (NBT)
    // ================================

    public ServerLevel getLevel() { return this.level; }

    private String diagnosticKey(String kind, int nodeId, @Nullable String detail) {
        return "blueprint:" + graphId + ':' + kind + ':' + nodeId + ':' + (detail != null ? detail : "");
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return BlueprintProcessSerializer.save(this, tag, provider);
    }

    public static BlueprintProcess load(CompoundTag tag, BlueprintPlan index, HolderLookup.Provider provider) {
        return BlueprintProcessSerializer.load(tag, index, provider);
    }
}
