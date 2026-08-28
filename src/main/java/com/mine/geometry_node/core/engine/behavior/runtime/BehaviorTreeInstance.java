package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorScopedStateProviders;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorLifecycleContract;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorRuntimeBudget;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorValueSemantics;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.engine.graph.data.GraphDataEvaluationSession;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.node.NodeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BehaviorBlackboard[] blackboards;
    private final GraphDataEvaluationSession dataEvaluation;
    private final Random random;
    private final TraceEvent[] history;
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
    private int lastImmediateTransitions;
    private int peakImmediateTransitions;
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
        this.blackboards = new BehaviorBlackboard[plan.blackboardFrameInfos().size()];
        for (int frame = 0; frame < blackboards.length; frame++) {
            blackboards[frame] = newBlackboard(frame);
        }
        this.dataEvaluation = new GraphDataEvaluationSession(plan);
        this.random = new Random(randomSeed);
        this.history = new TraceEvent[budget.maxHistoryEntriesPerInstance()];
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
                peakNodeVisits, lastImmediateTransitions, peakImmediateTransitions);
    }
    public long lastEvaluationTick() { return lastEvaluationTick; }
    public long nextWakeTick() { return nextWakeTick; }
    public BehaviorBlackboard blackboard() { return blackboards[0]; }

    /** Read-only copies of all call-frame blackboards for diagnostics. */
    public List<BlackboardFrameSnapshot> blackboardFrameSnapshots() {
        List<BlackboardFrameSnapshot> result = new ArrayList<>(blackboards.length);
        for (int frame = 0; frame < blackboards.length; frame++) {
            BehaviorTreePlan.BlackboardFrameInfo info = plan.blackboardFrameInfos().get(frame);
            result.add(new BlackboardFrameSnapshot(frame, info.assetId(), info.callNodePath(),
                    blackboards[frame].revision(), frame == 0 ? blackboards[frame].snapshot()
                    : blackboards[frame].snapshot(
                            ScopedStateScope.INSTANCE)));
        }
        return List.copyOf(result);
    }

    BehaviorBlackboard blackboard(int nodeIndex) {
        return blackboards[plan.blackboardFrame(nodeIndex)];
    }

    void enterSubtreeCall(int nodeIndex) {
        BehaviorTreePlan.SubtreeCallBoundary call = requireSubtreeCall(nodeIndex);
        blackboards[call.childFrame()] = newBlackboard(call.childFrame());
        BehaviorBlackboard parent = blackboards[call.parentFrame()];
        BehaviorBlackboard child = blackboards[call.childFrame()];
        for (BehaviorTreePlan.SubtreeParameterTransfer transfer : call.inputTransfers()) {
            Object value = parent.get(
                    ScopedStateScope.INSTANCE,
                    transfer.sourceKey());
            if (value != null) {
                child.set(ScopedStateScope.INSTANCE,
                        transfer.targetKey(), validateSubtreeTransfer(nodeIndex, transfer, value, "input"),
                        blackboardAuditSource(nodeIndex), host.gameTick());
            }
        }
        dataEvaluation.clearValues();
    }

    private void installPersistentBlackboards(BehaviorBlackboard blackboard) {
        if (host.level() != null && host.owner() != null) {
            BehaviorScopedStateProviders.install(blackboard, host.level(), host.owner());
        }
    }

    private BehaviorBlackboard newBlackboard(int frame) {
        BehaviorBlackboard blackboard = new BehaviorBlackboard(
                budget.maxBlackboardEntriesPerInstance());
        installPersistentBlackboards(blackboard);
        return blackboard;
    }

    String blackboardAuditSource(int nodeIndex) {
        return instanceId + "|" + plan.getNodeAssetId(nodeIndex) + "|" + plan.getNodeId(nodeIndex);
    }

    void exitSubtreeCall(int nodeIndex, BehaviorTerminationReason reason) {
        BehaviorTreePlan.SubtreeCallBoundary call = requireSubtreeCall(nodeIndex);
        if (reason == BehaviorTerminationReason.COMPLETED_SUCCESS
                || reason == BehaviorTerminationReason.COMPLETED_FAILURE) {
            BehaviorBlackboard parent = blackboards[call.parentFrame()];
            BehaviorBlackboard child = blackboards[call.childFrame()];
            Map<BehaviorTreePlan.SubtreeParameterTransfer, Object> outputs = new LinkedHashMap<>();
            for (BehaviorTreePlan.SubtreeParameterTransfer transfer : call.outputTransfers()) {
                Object value = child.get(
                        ScopedStateScope.INSTANCE,
                        transfer.sourceKey());
                if (value != null) {
                    outputs.put(transfer, validateSubtreeTransfer(
                            nodeIndex, transfer, value, "output"));
                }
            }
            for (Map.Entry<BehaviorTreePlan.SubtreeParameterTransfer, Object> output : outputs.entrySet()) {
                parent.set(ScopedStateScope.INSTANCE,
                        output.getKey().targetKey(), output.getValue(),
                        blackboardAuditSource(nodeIndex), host.gameTick());
            }
        }
        dataEvaluation.clearValues();
    }

    private BehaviorTreePlan.SubtreeCallBoundary requireSubtreeCall(int nodeIndex) {
        BehaviorTreePlan.SubtreeCallBoundary call = plan.subtreeCall(nodeIndex);
        if (call == null) throw new IllegalStateException(
                "Linked plan has no subtree call boundary for " + plan.getNodeId(nodeIndex));
        return call;
    }

    private Object validateSubtreeTransfer(int nodeIndex,
                                           BehaviorTreePlan.SubtreeParameterTransfer transfer,
                                           Object value, String direction) {
        if (!BehaviorValueSemantics.matches(value, transfer.type())) {
            throw new BehaviorContractViolation("Subtree " + direction + " type mismatch at "
                    + plan.getNodeId(nodeIndex) + ": " + transfer.sourceKey() + " -> "
                    + transfer.targetKey() + " requires " + transfer.type() + " but received "
                    + value.getClass().getSimpleName());
        }
        return BehaviorValueSemantics.freezeAs(value, transfer.type());
    }

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
        if (state != BehaviorInstanceState.RUNNING) return;
        if (result != BehaviorResult.RUNNING) {
            nextWakeTick = nextRootRoundTick(lastEvaluationTick,
                    plan.rootSchedule().recheckInterval(), rootScheduleOffset);
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
        visitCounts[nodeIndex]++;
        lastNanos[nodeIndex] = Math.max(0L, elapsedNanos);
        totalNanos[nodeIndex] = saturatingAdd(totalNanos[nodeIndex], lastNanos[nodeIndex]);
    }

    void recordEvaluationMetrics(long elapsedNanos, int nodeVisits, int immediateTransitions) {
        lastEvaluationNanos = Math.max(0L, elapsedNanos);
        totalEvaluationNanos = saturatingAdd(totalEvaluationNanos, lastEvaluationNanos);
        lastNodeVisits = Math.max(0, nodeVisits);
        peakNodeVisits = Math.max(peakNodeVisits, lastNodeVisits);
        lastImmediateTransitions = Math.max(0, immediateTransitions);
        peakImmediateTransitions = Math.max(peakImmediateTransitions, lastImmediateTransitions);
        if (lastEvaluationNanos > budget.instanceNanosPerEvaluation()) {
            evaluationTimeOverruns++;
        }
    }

    void recordTermination(int nodeIndex, BehaviorTerminationReason reason, long elapsedNanos,
                           @Nullable String failureCode, @Nullable String detail) {
        BehaviorResult result = reason.result();
        lastResults[nodeIndex] = result;
        lastReasons[nodeIndex] = reason;
        TraceEvent event = new TraceEvent(++traceSequence, host.gameTick(), nodeIndex,
                plan.getNodeId(nodeIndex), plan.getNodeType(nodeIndex), reason, result,
                Math.max(0L, elapsedNanos),
                failureCode, detail != null ? detail : "");
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
        if (conflictingResourceOwner(nodeIndex, resources) >= 0) return false;
        if (!host.acquireResources(nodeIndex, resources)) return false;
        for (NodeCapabilities.ResourceUse resource : resources) {
            if (resource != NodeCapabilities.ResourceUse.NONE) resourceOwners[resource.ordinal()] = nodeIndex;
        }
        resourcesAcquired[nodeIndex] = true;
        return true;
    }

    int conflictingResourceOwner(int nodeIndex, Set<NodeCapabilities.ResourceUse> resources) {
        for (NodeCapabilities.ResourceUse requested : resources) {
            if (requested == NodeCapabilities.ResourceUse.NONE) continue;
            for (NodeCapabilities.ResourceUse held : NodeCapabilities.ResourceUse.values()) {
                int owner = resourceOwners[held.ordinal()];
                if (owner != -1 && owner != nodeIndex && resourcesConflict(requested, held)) {
                    return owner;
                }
            }
        }
        return -1;
    }

    private static boolean resourcesConflict(NodeCapabilities.ResourceUse first,
                                             NodeCapabilities.ResourceUse second) {
        if (first == NodeCapabilities.ResourceUse.NONE || second == NodeCapabilities.ResourceUse.NONE) {
            return false;
        }
        if (first == second) return true;
        return first == NodeCapabilities.ResourceUse.COMBAT
                && (second == NodeCapabilities.ResourceUse.MOVEMENT
                || second == NodeCapabilities.ResourceUse.LOOK
                || second == NodeCapabilities.ResourceUse.TARGET)
                || second == NodeCapabilities.ResourceUse.COMBAT
                && (first == NodeCapabilities.ResourceUse.MOVEMENT
                || first == NodeCapabilities.ResourceUse.LOOK
                || first == NodeCapabilities.ResourceUse.TARGET);
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
                                    int peakNodeVisits, int lastImmediateTransitions,
                                    int peakImmediateTransitions) {
    }

    public record TraceEvent(long sequence, long gameTick, int nodeIndex, String nodeId,
                             String nodeType,
                             BehaviorTerminationReason reason, @Nullable BehaviorResult result,
                             long elapsedNanos, @Nullable String failureCode, String detail) {
    }

    public record BlackboardFrameSnapshot(int frameId, String assetId, String callNodePath,
                                          long revision,
                                          List<BehaviorBlackboard.EntrySnapshot> entries) {
        public BlackboardFrameSnapshot {
            entries = List.copyOf(entries);
        }
    }
}
