package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.data.GraphDataEvaluationSession;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProviderResolver;
import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Mutable state for one owner running one immutable behavior-tree plan. */
public final class BehaviorTreeProcess {
    private final UUID instanceId;
    private final BehaviorTreePlan plan;
    private final BehaviorRuntimeHost host;
    private final BehaviorRuntimeBudget budget;
    private final BehaviorNodeState[] nodeStates;
    private final Object[] nodeMemory;
    @Nullable private BehaviorResult[] lastResults;
    @Nullable private BehaviorTerminationReason[] lastReasons;
    @Nullable private long[] visitCounts;
    @Nullable private long[] totalNanos;
    @Nullable private long[] lastNanos;
    private final int[] resourceOwners;
    private final boolean[] resourcesAcquired;
    private final BehaviorBlackboard blackboard;
    private final GraphDataEvaluationSession dataEvaluation;
    private final Random random;
    @Nullable private TraceEvent[] history;
    private final int rootScheduleOffset;

    private BehaviorInstanceState state = BehaviorInstanceState.CREATED;
    private BehaviorTerminationReason stopReason;
    private BehaviorResult lastTreeResult;
    private long evaluationCount;
    private long totalEvaluationNanos;
    private long lastEvaluationNanos;
    private long evaluationTimeOverruns;
    private int lastNodeVisits;
    private int peakNodeVisits;
    private long lastEvaluationTick = Long.MIN_VALUE;
    private long nextWakeTick = Long.MAX_VALUE;
    private long traceSequence;
    private int historyStart;
    private int historySize;
    private boolean evaluating;
    private boolean reentrantWakeRequested;
    private boolean debugTracing;

    public BehaviorTreeProcess(UUID instanceId, BehaviorTreePlan plan, BehaviorRuntimeHost host,
                                BehaviorRuntimeBudget budget, long randomSeed) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.host = Objects.requireNonNull(host, "host");
        this.budget = Objects.requireNonNull(budget, "budget");
        int nodeCount = plan.getNodeCount();
        this.nodeStates = new BehaviorNodeState[nodeCount];
        Arrays.fill(nodeStates, BehaviorNodeState.IDLE);
        this.nodeMemory = new Object[nodeCount];
        this.resourceOwners = new int[NodeCapabilities.ResourceUse.values().length];
        Arrays.fill(resourceOwners, -1);
        this.resourcesAcquired = new boolean[nodeCount];
        this.blackboard = newBlackboard();
        this.dataEvaluation = new GraphDataEvaluationSession(plan);
        this.random = new Random(randomSeed);
        this.rootScheduleOffset = plan.rootSchedule().resolveOffset(host.identity(), plan.assetId());
    }

    public UUID instanceId() { return instanceId; }
    public String graphId() { return plan.assetId(); }
    public BehaviorTreePlan plan() { return plan; }
    public BehaviorRuntimeHost host() { return host; }
    public BehaviorRuntimeBudget budget() { return budget; }
    public BehaviorInstanceState state() { return state; }
    @Nullable public BehaviorTerminationReason stopReason() { return stopReason; }
    @Nullable public BehaviorResult lastTreeResult() { return lastTreeResult; }
    public long evaluationCount() { return evaluationCount; }
    public EvaluationMetrics evaluationMetrics() {
        return new EvaluationMetrics(evaluationCount, totalEvaluationNanos,
                lastEvaluationNanos, evaluationTimeOverruns, lastNodeVisits,
                peakNodeVisits);
    }
    public long lastEvaluationTick() { return lastEvaluationTick; }
    public long nextWakeTick() { return nextWakeTick; }
    public BehaviorBlackboard blackboard() { return blackboard; }

    private BehaviorBlackboard newBlackboard() {
        BehaviorBlackboard blackboard = new BehaviorBlackboard(
                budget.maxBlackboardEntriesPerInstance());
        ServerLevel level = host.level();
        Entity owner = host.owner();
        if (level != null && owner != null) {
            ScopedStateNamespace namespace = ScopedStateNamespace.PUBLIC;
            blackboard.installProvider(ScopedStateProviderResolver.owner(owner, namespace));
            blackboard.installProvider(ScopedStateProviderResolver.shared(level, namespace));
            blackboard.installProvider(ScopedStateProviderResolver.currentGroup(owner, namespace));
            blackboard.installProvider(ScopedStateProviderResolver.world(level, namespace));
        }
        return blackboard;
    }

    public BehaviorNodeState nodeState(int nodeIndex) {
        return validNode(nodeIndex) ? nodeStates[nodeIndex] : BehaviorNodeState.IDLE;
    }

    public NodeMetrics nodeMetrics(int nodeIndex) {
        if (!validNode(nodeIndex) || visitCounts == null || totalNanos == null || lastNanos == null
                || lastResults == null || lastReasons == null) return NodeMetrics.EMPTY;
        return new NodeMetrics(visitCounts[nodeIndex], totalNanos[nodeIndex], lastNanos[nodeIndex],
                lastResults[nodeIndex], lastReasons[nodeIndex]);
    }

    public List<Integer> activePath() {
        List<Integer> result = new ArrayList<>();
        boolean[] visited = new boolean[nodeStates.length];
        int current = plan.getRootNode();
        while (validNode(current) && nodeStates[current].isActive() && !visited[current]) {
            visited[current] = true;
            result.add(current);
            int activeChild = -1;
            for (int index = 0; index < plan.getChildCount(current); index++) {
                int child = plan.getChild(current, index);
                if (validNode(child) && nodeStates[child].isActive()) {
                    activeChild = child;
                    break;
                }
            }
            current = activeChild;
        }
        return List.copyOf(result);
    }

    public List<TraceEvent> history() {
        if (history == null || historySize == 0) return List.of();
        List<TraceEvent> result = new ArrayList<>(historySize);
        for (int index = 0; index < historySize; index++) {
            result.add(history[(historyStart + index) % history.length]);
        }
        return List.copyOf(result);
    }

    public boolean debugTracingEnabled() {
        return debugTracing;
    }

    public void setDebugTracingEnabled(boolean enabled) {
        if (debugTracing == enabled) return;
        debugTracing = enabled;
        if (enabled) {
            int nodeCount = plan.getNodeCount();
            lastResults = new BehaviorResult[nodeCount];
            lastReasons = new BehaviorTerminationReason[nodeCount];
            visitCounts = new long[nodeCount];
            totalNanos = new long[nodeCount];
            lastNanos = new long[nodeCount];
            history = new TraceEvent[budget.maxHistoryEntriesPerInstance()];
            return;
        }
        lastResults = null;
        lastReasons = null;
        visitCounts = null;
        totalNanos = null;
        lastNanos = null;
        history = null;
        evaluationCount = 0L;
        totalEvaluationNanos = 0L;
        lastEvaluationNanos = 0L;
        evaluationTimeOverruns = 0L;
        lastNodeVisits = 0;
        peakNodeVisits = 0;
        traceSequence = 0L;
        historyStart = 0;
        historySize = 0;
    }

    void markRunning() {
        if (state != BehaviorInstanceState.CREATED && state != BehaviorInstanceState.SUSPENDED) {
            throw new IllegalStateException("Cannot start behavior instance from " + state);
        }
        state = BehaviorInstanceState.RUNNING;
        stopReason = null;
    }

    void markSuspended() {
        if (state != BehaviorInstanceState.RUNNING) return;
        state = BehaviorInstanceState.SUSPENDED;
        nextWakeTick = Long.MAX_VALUE;
        dataEvaluation.clearValues();
    }

    void markResumed() {
        if (state != BehaviorInstanceState.SUSPENDED) return;
        state = BehaviorInstanceState.RUNNING;
    }

    void markStopped(BehaviorTerminationReason reason) {
        state = reason.kind() == BehaviorTerminationReason.Kind.ERROR
                ? BehaviorInstanceState.ERROR : BehaviorInstanceState.STOPPED;
        stopReason = reason;
        nextWakeTick = Long.MAX_VALUE;
    }

    boolean beginEvaluation(long gameTick) {
        if (state != BehaviorInstanceState.RUNNING || evaluating) {
            if (evaluating) reentrantWakeRequested = true;
            return false;
        }
        if (lastEvaluationTick == gameTick) {
            if (nextWakeTick <= gameTick) nextWakeTick = safeIncrement(gameTick);
            return false;
        }
        evaluating = true;
        reentrantWakeRequested = false;
        nextWakeTick = Long.MAX_VALUE;
        lastEvaluationTick = gameTick;
        if (debugTracing) evaluationCount++;
        dataEvaluation.beginEpoch();
        return true;
    }

    void finishEvaluation(BehaviorResult result, long defaultWakeTick) {
        lastTreeResult = result;
        evaluating = false;
        if (state != BehaviorInstanceState.RUNNING) return;
        if (result != BehaviorResult.RUNNING) {
            long scheduled = nextRootRoundTick(lastEvaluationTick,
                    plan.rootSchedule().recheckInterval(), rootScheduleOffset);
            if (reentrantWakeRequested) scheduled = Math.min(scheduled, defaultWakeTick);
            nextWakeTick = Math.min(nextWakeTick, scheduled);
            return;
        }
        if (reentrantWakeRequested) requestWakeup(defaultWakeTick);
        if (nextWakeTick == Long.MAX_VALUE) nextWakeTick = defaultWakeTick;
    }

    void failEvaluation(BehaviorTerminationReason reason) {
        evaluating = false;
        markStopped(reason);
    }

    void requestWakeup(long dueTick) {
        if (!state.isActive()) return;
        long earliest = evaluating ? safeIncrement(lastEvaluationTick) : host.gameTick();
        long normalized = Math.max(dueTick, earliest);
        nextWakeTick = Math.min(nextWakeTick, normalized);
    }

    boolean isEvaluating() { return evaluating; }
    GraphDataEvaluationSession dataEvaluation() { return dataEvaluation; }
    Random random() { return random; }
    BehaviorNodeState rawNodeState(int nodeIndex) { return nodeStates[nodeIndex]; }
    void setNodeState(int nodeIndex, BehaviorNodeState value) { nodeStates[nodeIndex] = value; }
    @Nullable Object nodeMemory(int nodeIndex) { return nodeMemory[nodeIndex]; }
    void setNodeMemory(int nodeIndex, @Nullable Object value) { nodeMemory[nodeIndex] = value; }

    void recordVisit(int nodeIndex, long elapsedNanos) {
        if (!debugTracing || visitCounts == null || lastNanos == null || totalNanos == null) return;
        visitCounts[nodeIndex]++;
        lastNanos[nodeIndex] = Math.max(0L, elapsedNanos);
        totalNanos[nodeIndex] = saturatingAdd(totalNanos[nodeIndex], lastNanos[nodeIndex]);
    }

    void recordEvaluationMetrics(long elapsedNanos, int nodeVisits) {
        if (!debugTracing) return;
        lastEvaluationNanos = Math.max(0L, elapsedNanos);
        totalEvaluationNanos = saturatingAdd(totalEvaluationNanos, lastEvaluationNanos);
        lastNodeVisits = Math.max(0, nodeVisits);
        peakNodeVisits = Math.max(peakNodeVisits, lastNodeVisits);
        if (lastEvaluationNanos > budget.instanceNanosPerEvaluation()) {
            evaluationTimeOverruns++;
        }
    }

    void recordTermination(int nodeIndex, BehaviorTerminationReason reason, long elapsedNanos,
                           @Nullable String failureCode, @Nullable String detail) {
        if (!debugTracing || lastResults == null || lastReasons == null || history == null) return;
        BehaviorResult result = reason.result();
        lastResults[nodeIndex] = result;
        lastReasons[nodeIndex] = reason;
        TraceEvent event = new TraceEvent(++traceSequence, host.gameTick(), nodeIndex,
                plan.getNodeId(nodeIndex), plan.getNodeType(nodeIndex), reason, result,
                Math.max(0L, elapsedNanos),
                failureCode, detail != null ? detail : "");
        int insertion = (historyStart + historySize) % history.length;
        history[insertion] = event;
        if (historySize < history.length) {
            historySize++;
        } else {
            historyStart = (historyStart + 1) % history.length;
        }
    }

    boolean acquireResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        if (resources.isEmpty()) return true;
        if (conflictingResourceOwner(nodeIndex, resources) >= 0) return false;
        if (!host.acquireResources(nodeIndex, resources)) return false;
        for (NodeCapabilities.ResourceUse resource : resources) {
            resourceOwners[resource.ordinal()] = nodeIndex;
        }
        resourcesAcquired[nodeIndex] = true;
        return true;
    }

    int conflictingResourceOwner(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        for (NodeCapabilities.ResourceUse requested : resources) {
            for (NodeCapabilities.ResourceUse held : NodeCapabilities.ResourceUse.values()) {
                int owner = resourceOwners[held.ordinal()];
                if (owner != -1 && owner != nodeIndex && requested == held) {
                    return owner;
                }
            }
        }
        return -1;
    }

    void releaseResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        if (!resourcesAcquired[nodeIndex]) return;
        resourcesAcquired[nodeIndex] = false;
        try {
            host.releaseResources(nodeIndex, resources);
        } finally {
            for (NodeCapabilities.ResourceUse resource : resources) {
                if (resourceOwners[resource.ordinal()] == nodeIndex) {
                    resourceOwners[resource.ordinal()] = -1;
                }
            }
        }
    }

    void releaseAllResources() {
        for (int nodeIndex = 0; nodeIndex < resourcesAcquired.length; nodeIndex++) {
            if (resourcesAcquired[nodeIndex]) {
                releaseResources(nodeIndex, plan.getNodeResources(nodeIndex));
            }
        }
    }

    private boolean validNode(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < nodeStates.length;
    }

    private static long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private static long nextRootRoundTick(long currentTick, int interval, int offset) {
        long remainder = Math.floorMod(currentTick, (long) interval);
        long delta = Math.floorMod((long) offset - remainder, (long) interval);
        if (delta == 0L) delta = interval;
        return currentTick > Long.MAX_VALUE - delta ? Long.MAX_VALUE : currentTick + delta;
    }

    private static long saturatingAdd(long first, long second) {
        long result = first + second;
        return result < first ? Long.MAX_VALUE : result;
    }

    public record NodeMetrics(long visits, long totalNanos, long lastNanos,
                              @Nullable BehaviorResult lastResult,
                              @Nullable BehaviorTerminationReason lastReason) {
        public static final NodeMetrics EMPTY = new NodeMetrics(0L, 0L, 0L, null, null);
    }

    public record EvaluationMetrics(long evaluations, long totalNanos, long lastNanos,
                                    long softTimeBudgetOverruns, int lastNodeVisits,
                                    int peakNodeVisits) {
    }

    public record TraceEvent(long sequence, long gameTick, int nodeIndex, String nodeId,
                             String nodeType,
                             BehaviorTerminationReason reason, @Nullable BehaviorResult result,
                             long elapsedNanos, @Nullable String failureCode, String detail) {
    }

}
