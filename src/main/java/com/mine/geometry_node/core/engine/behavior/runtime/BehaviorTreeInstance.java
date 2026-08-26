package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorLifecycleContract;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.data.GraphDataEvaluationSession;
import com.mine.geometry_node.core.node.NodeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Mutable state for one owner running one immutable behavior-tree plan. */
public final class BehaviorTreeInstance {
    private final UUID instanceId;
    private final BehaviorTreePlan plan;
    private final BehaviorRuntimeHost host;
    private final BehaviorRuntimeBudget budget;
    private final BehaviorNodeState[] nodeStates;
    private final Object[] nodeMemory;
    private final BehaviorResult[] lastResults;
    private final BehaviorTerminationReason[] lastReasons;
    private final long[] visitCounts;
    private final long[] totalNanos;
    private final long[] lastNanos;
    private final int[] resourceOwners;
    private final boolean[] resourcesAcquired;
    private final BehaviorBlackboard blackboard;
    private final GraphDataEvaluationSession dataEvaluation;
    private final Random random;
    private final TraceEvent[] history;

    private BehaviorInstanceState state = BehaviorInstanceState.CREATED;
    private BehaviorTerminationReason stopReason;
    private BehaviorResult lastTreeResult;
    private long evaluationCount;
    private long totalEvaluationNanos;
    private long lastEvaluationNanos;
    private long evaluationTimeOverruns;
    private long lastEvaluationTick = Long.MIN_VALUE;
    private long nextWakeTick = Long.MAX_VALUE;
    private long traceSequence;
    private int historyStart;
    private int historySize;
    private boolean evaluating;
    private boolean reentrantWakeRequested;

    public BehaviorTreeInstance(UUID instanceId, BehaviorTreePlan plan, BehaviorRuntimeHost host,
                                BehaviorRuntimeBudget budget, long randomSeed) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.host = Objects.requireNonNull(host, "host");
        this.budget = Objects.requireNonNull(budget, "budget");
        int nodeCount = plan.getNodeCount();
        this.nodeStates = new BehaviorNodeState[nodeCount];
        Arrays.fill(nodeStates, BehaviorNodeState.IDLE);
        this.nodeMemory = new Object[nodeCount];
        this.lastResults = new BehaviorResult[nodeCount];
        this.lastReasons = new BehaviorTerminationReason[nodeCount];
        this.visitCounts = new long[nodeCount];
        this.totalNanos = new long[nodeCount];
        this.lastNanos = new long[nodeCount];
        this.resourceOwners = new int[NodeCapabilities.ResourceUse.values().length];
        Arrays.fill(resourceOwners, -1);
        this.resourcesAcquired = new boolean[nodeCount];
        this.blackboard = new BehaviorBlackboard(plan.blackboardSchema(),
                budget.maxBlackboardEntriesPerInstance());
        this.dataEvaluation = new GraphDataEvaluationSession(plan);
        this.random = new Random(randomSeed);
        this.history = new TraceEvent[budget.maxHistoryEntriesPerInstance()];
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
                lastEvaluationNanos, evaluationTimeOverruns);
    }
    public long lastEvaluationTick() { return lastEvaluationTick; }
    public long nextWakeTick() { return nextWakeTick; }
    public BehaviorBlackboard blackboard() { return blackboard; }

    public BehaviorNodeState nodeState(int nodeIndex) {
        return validNode(nodeIndex) ? nodeStates[nodeIndex] : BehaviorNodeState.IDLE;
    }

    public NodeMetrics nodeMetrics(int nodeIndex) {
        if (!validNode(nodeIndex)) return NodeMetrics.EMPTY;
        return new NodeMetrics(visitCounts[nodeIndex], totalNanos[nodeIndex], lastNanos[nodeIndex],
                lastResults[nodeIndex], lastReasons[nodeIndex]);
    }

    public List<Integer> activePath() {
        List<Integer> result = new ArrayList<>();
        int current = plan.getRootNode();
        while (validNode(current) && nodeStates[current].isActive()) {
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

    public List<Integer> activeNodes() {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < nodeStates.length; index++) {
            if (nodeStates[index].isActive()) result.add(index);
        }
        return List.copyOf(result);
    }

    public List<TraceEvent> history() {
        List<TraceEvent> result = new ArrayList<>(historySize);
        for (int index = 0; index < historySize; index++) {
            result.add(history[(historyStart + index) % history.length]);
        }
        return List.copyOf(result);
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
        for (int index = 0; index < nodeStates.length; index++) {
            if (nodeStates[index] == BehaviorNodeState.RUNNING) {
                transitionNode(index, BehaviorNodeState.SUSPENDED);
            }
        }
    }

    void markResumed() {
        if (state != BehaviorInstanceState.SUSPENDED) return;
        state = BehaviorInstanceState.RUNNING;
        for (int index = 0; index < nodeStates.length; index++) {
            if (nodeStates[index] == BehaviorNodeState.SUSPENDED) {
                transitionNode(index, BehaviorNodeState.RUNNING);
            }
        }
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
        evaluationCount++;
        dataEvaluation.beginEpoch();
        return true;
    }

    void finishEvaluation(BehaviorResult result, long defaultWakeTick) {
        lastTreeResult = result;
        evaluating = false;
        if (state == BehaviorInstanceState.RUNNING) {
            if (reentrantWakeRequested) requestWakeup(defaultWakeTick);
            if (nextWakeTick == Long.MAX_VALUE) nextWakeTick = defaultWakeTick;
        }
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
        visitCounts[nodeIndex]++;
        lastNanos[nodeIndex] = Math.max(0L, elapsedNanos);
        totalNanos[nodeIndex] = saturatingAdd(totalNanos[nodeIndex], lastNanos[nodeIndex]);
    }

    void recordEvaluationTime(long elapsedNanos) {
        lastEvaluationNanos = Math.max(0L, elapsedNanos);
        totalEvaluationNanos = saturatingAdd(totalEvaluationNanos, lastEvaluationNanos);
        if (lastEvaluationNanos > budget.instanceNanosPerEvaluation()) {
            evaluationTimeOverruns++;
        }
    }

    void recordTermination(int nodeIndex, BehaviorTerminationReason reason, long elapsedNanos,
                           @Nullable String detail) {
        BehaviorResult result = reason.result();
        lastResults[nodeIndex] = result;
        lastReasons[nodeIndex] = reason;
        TraceEvent event = new TraceEvent(++traceSequence, host.gameTick(), nodeIndex,
                plan.getNodeId(nodeIndex), reason, result, Math.max(0L, elapsedNanos),
                detail != null ? detail : "");
        if (history.length == 0) return;
        int insertion = (historyStart + historySize) % history.length;
        history[insertion] = event;
        if (historySize < history.length) {
            historySize++;
        } else {
            historyStart = (historyStart + 1) % history.length;
        }
    }

    boolean acquireResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        boolean requiresLease = resources.stream().anyMatch(resource ->
                resource != NodeCapabilities.ResourceUse.NONE);
        if (!requiresLease) return true;
        for (NodeCapabilities.ResourceUse resource : resources) {
            if (resource == NodeCapabilities.ResourceUse.NONE) continue;
            int owner = resourceOwners[resource.ordinal()];
            if (owner != -1 && owner != nodeIndex) return false;
        }
        if (!host.acquireResources(nodeIndex, resources)) return false;
        for (NodeCapabilities.ResourceUse resource : resources) {
            if (resource != NodeCapabilities.ResourceUse.NONE) resourceOwners[resource.ordinal()] = nodeIndex;
        }
        resourcesAcquired[nodeIndex] = true;
        return true;
    }

    void releaseResources(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        if (!resourcesAcquired[nodeIndex]) return;
        resourcesAcquired[nodeIndex] = false;
        try {
            host.releaseResources(nodeIndex, resources);
        } finally {
            for (NodeCapabilities.ResourceUse resource : resources) {
                if (resource != NodeCapabilities.ResourceUse.NONE
                        && resourceOwners[resource.ordinal()] == nodeIndex) {
                    resourceOwners[resource.ordinal()] = -1;
                }
            }
        }
    }

    private boolean validNode(int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < nodeStates.length;
    }

    private void transitionNode(int nodeIndex, BehaviorNodeState target) {
        BehaviorNodeState current = nodeStates[nodeIndex];
        if (!BehaviorLifecycleContract.allows(current, target)) {
            throw new IllegalStateException("Illegal behavior lifecycle transition: "
                    + current + " -> " + target);
        }
        nodeStates[nodeIndex] = target;
    }

    private static long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
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
                                    long softTimeBudgetOverruns) {
    }

    public record TraceEvent(long sequence, long gameTick, int nodeIndex, String nodeId,
                             BehaviorTerminationReason reason, @Nullable BehaviorResult result,
                             long elapsedNanos, String detail) {
    }
}
