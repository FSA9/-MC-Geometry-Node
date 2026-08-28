package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorLifecycleContract;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorNodeState;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionFailure;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorBudgetExceededException;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.port.TypeConverter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** Deterministic lifecycle evaluator for one immutable plan and isolated instance state. */
public final class BehaviorTreeEvaluator {
    private final BehaviorNodeExecutorRegistry executors;
    private final ThreadLocal<EvaluationPass> currentPass = new ThreadLocal<>();

    public BehaviorTreeEvaluator(BehaviorNodeExecutorRegistry executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    public EvaluationOutcome evaluate(BehaviorTreeInstance instance) {
        Objects.requireNonNull(instance, "instance");
        long tick = instance.host().gameTick();
        if (!instance.host().isValid()) {
            stop(instance, BehaviorTerminationReason.OWNER_INVALID);
            return EvaluationOutcome.failed(BehaviorTerminationReason.OWNER_INVALID, "Behavior owner is unavailable");
        }
        if (!instance.beginEvaluation(tick)) {
            return EvaluationOutcome.skipped(instance.nextWakeTick());
        }

        long started = instance.host().nanoTime();
        EvaluationPass pass = new EvaluationPass(instance);
        currentPass.set(pass);
        try {
            BehaviorResult result = evaluateNode(instance, instance.plan().getRootNode(), 1, tick);
            long nextTick = safeIncrement(tick);
            instance.finishEvaluation(result, nextTick);
            long elapsedNanos = elapsed(started, instance.host().nanoTime());
            instance.recordEvaluationMetrics(elapsedNanos, pass.visits, pass.immediateTransitions);
            return new EvaluationOutcome(result, null, instance.nextWakeTick(), pass.visits,
                    elapsedNanos, "");
        } catch (EvaluationFault fault) {
            long elapsedNanos = elapsed(started, instance.host().nanoTime());
            instance.recordEvaluationMetrics(elapsedNanos, pass.visits, pass.immediateTransitions);
            instance.failEvaluation(fault.reason);
            return EvaluationOutcome.failed(fault.reason, fault.getMessage(), pass.visits, elapsedNanos);
        } finally {
            currentPass.remove();
        }
    }

    public void stop(BehaviorTreeInstance instance, BehaviorTerminationReason reason) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(reason, "reason");
        int root = instance.plan().getRootNode();
        if (root >= 0) abortSubtree(instance, root, reason);
        instance.markStopped(reason);
    }

    BehaviorResult evaluateNode(BehaviorTreeInstance instance, int nodeIndex, int depth, long epochTick) {
        EvaluationPass pass = requirePass(instance);
        if (depth > instance.budget().maxTreeDepth()) {
            throw new EvaluationFault(BehaviorTerminationReason.BUDGET_EXHAUSTED,
                    "Behavior tree depth budget exceeded");
        }
        if (++pass.visits > instance.budget().maxNodeVisitsPerEvaluation()) {
            throw new EvaluationFault(BehaviorTerminationReason.BUDGET_EXHAUSTED,
                    "Behavior node visit budget exceeded");
        }
        if (nodeIndex < 0 || nodeIndex >= instance.plan().getNodeCount()) {
            throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                    "Behavior plan references an invalid node index");
        }

        BehaviorNodeExecutor executor = executors.get(instance.plan().getNodeType(nodeIndex));
        if (executor == null) {
            throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                    "No behavior executor is registered for " + instance.plan().getNodeType(nodeIndex));
        }
        BehaviorNodeContext context = new BehaviorNodeContext(this, instance, nodeIndex, depth, epochTick);
        long started = instance.host().nanoTime();
        try {
            BehaviorNodeState state = instance.rawNodeState(nodeIndex);
            if (state == BehaviorNodeState.IDLE) {
                enterNode(instance, nodeIndex, executor, context);
            } else if (state != BehaviorNodeState.RUNNING) {
                throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                        "Node cannot be evaluated from state " + state);
            }

            BehaviorResult result = executor.update(context);
            if (result == null) {
                throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                        "Behavior executor returned no result");
            }
            if (result == BehaviorResult.RUNNING) {
                if (instance.rawNodeState(nodeIndex) == BehaviorNodeState.ENTERING) {
                    transition(instance, nodeIndex, BehaviorNodeState.RUNNING);
                }
                return result;
            }

            if (++pass.immediateTransitions > instance.budget().maxImmediateTransitions()) {
                throw new EvaluationFault(BehaviorTerminationReason.BUDGET_EXHAUSTED,
                        "Behavior immediate-transition budget exceeded");
            }
            BehaviorTerminationReason reason = executor.completionReason(context, result);
            if (reason == null || (reason.result() != null && reason.result() != result)) {
                throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                        "Behavior executor returned an incompatible completion reason");
            }
            BehaviorActionFailure actionFailure = result == BehaviorResult.FAILURE
                    ? context.actionFailure() : null;
            EvaluationFault exitFailure = terminateNode(instance, nodeIndex, executor, context,
                    reason, actionFailure != null ? actionFailure.code() : null,
                    actionFailure != null ? actionFailure.detail() : null, true,
                    elapsed(started, instance.host().nanoTime()));
            if (exitFailure != null) throw exitFailure;
            return result;
        } catch (EvaluationFault fault) {
            if (instance.rawNodeState(nodeIndex).isActive()) {
                EvaluationFault cleanupFailure = terminateNode(instance, nodeIndex, executor, context,
                        fault.reason, null, fault.getMessage(), true,
                        elapsed(started, instance.host().nanoTime()));
                if (cleanupFailure != null) throw cleanupFailure;
            }
            throw fault;
        } catch (BehaviorBudgetExceededException exception) {
            String detail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            if (instance.rawNodeState(nodeIndex).isActive()) {
                EvaluationFault cleanupFailure = terminateNode(instance, nodeIndex, executor, context,
                        BehaviorTerminationReason.BUDGET_EXHAUSTED, null, detail, true,
                        elapsed(started, instance.host().nanoTime()));
                if (cleanupFailure != null) throw cleanupFailure;
            }
            throw new EvaluationFault(BehaviorTerminationReason.BUDGET_EXHAUSTED, detail);
        } catch (ScopedStateAccessException | BehaviorContractViolation exception) {
            String detail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            if (instance.rawNodeState(nodeIndex).isActive()) {
                EvaluationFault cleanupFailure = terminateNode(instance, nodeIndex, executor, context,
                        BehaviorTerminationReason.INVALID_DATA, null, detail, true,
                        elapsed(started, instance.host().nanoTime()));
                if (cleanupFailure != null) throw cleanupFailure;
            }
            throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA, detail);
        } catch (Exception exception) {
            String detail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            if (instance.rawNodeState(nodeIndex).isActive()) {
                EvaluationFault cleanupFailure = terminateNode(instance, nodeIndex, executor, context,
                        BehaviorTerminationReason.NODE_EXCEPTION, null, detail, true,
                        elapsed(started, instance.host().nanoTime()));
                if (cleanupFailure != null) throw cleanupFailure;
            }
            throw new EvaluationFault(BehaviorTerminationReason.NODE_EXCEPTION, detail);
        } finally {
            context.close();
            instance.recordVisit(nodeIndex, elapsed(started, instance.host().nanoTime()));
        }
    }

    BehaviorResult evaluateNodeReplacing(BehaviorTreeInstance instance, int candidateNodeIndex,
                                         int previousNodeIndex, BehaviorTerminationReason reason,
                                         int depth, long epochTick) {
        EvaluationPass pass = requirePass(instance);
        PendingPreemption outer = pass.pendingPreemption;
        pass.pendingPreemption = new PendingPreemption(previousNodeIndex, reason, outer);
        try {
            return evaluateNode(instance, candidateNodeIndex, depth, epochTick);
        } finally {
            pass.pendingPreemption = outer;
        }
    }

    void abortChild(BehaviorTreeInstance instance, int nodeIndex, BehaviorTerminationReason reason) {
        EvaluationFault failure = abortSubtree(instance, nodeIndex, reason);
        if (failure != null) throw failure;
    }

    @Nullable
    Object resolveInput(BehaviorTreeInstance instance, int targetNodeIndex, String portName) {
        CompiledDataIndex.DataConnectionSource source = instance.plan()
                .findDataInput(targetNodeIndex, portName);
        if (source == null) return instance.plan().getStaticInput(targetNodeIndex, portName);
        return instance.dataEvaluation().evaluate(source.sourceNodeId(), source.sourcePortName(),
                (nodeIndex, outputPort) -> computeDataNode(instance, nodeIndex, outputPort));
    }

    @Nullable
    <T> T resolveInput(BehaviorTreeInstance instance, int targetNodeIndex,
                       String portName, Class<T> type) {
        return TypeConverter.convert(resolveInput(instance, targetNodeIndex, portName),
                type, new BehaviorDataContext(instance, targetNodeIndex));
    }

    @Nullable
    <T> T convertInput(BehaviorTreeInstance instance, int targetNodeIndex,
                       Object value, Class<T> type) {
        return TypeConverter.convert(value, type,
                new BehaviorDataContext(instance, targetNodeIndex));
    }

    private Object computeDataNode(BehaviorTreeInstance instance, int nodeIndex, String outputPort) {
        BaseNode node = NodeRegistry.INSTANCE.get(instance.plan().getNodeType(nodeIndex));
        if (node == null) {
            throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                    "Data node implementation is unavailable: " + instance.plan().getNodeType(nodeIndex));
        }
        return node.compute(new BehaviorDataContext(instance, nodeIndex), outputPort);
    }

    private void enterNode(BehaviorTreeInstance instance, int nodeIndex, BehaviorNodeExecutor executor,
                           BehaviorNodeContext context) throws Exception {
        Set<NodeCapabilities.ResourceUse> resources = instance.plan()
                .getNodeCapabilities(nodeIndex).resources();
        int conflictOwner = instance.conflictingResourceOwner(nodeIndex, resources);
        if (conflictOwner >= 0) {
            PendingPreemption pending = matchingPreemption(
                    requirePass(instance).pendingPreemption, instance, conflictOwner);
            if (pending != null) {
                pending.consumed = true;
                EvaluationFault abortFailure = abortSubtree(
                        instance, pending.previousNodeIndex, pending.reason);
                if (abortFailure != null) throw abortFailure;
            }
        }
        transition(instance, nodeIndex, BehaviorNodeState.ENTERING);
        if (!instance.acquireResources(nodeIndex, resources)) {
            throw new EvaluationFault(BehaviorTerminationReason.CAPABILITY_LOST,
                    "Behavior resources are already owned by another active node");
        }
        executor.enter(context);
    }

    @Nullable
    private static PendingPreemption matchingPreemption(@Nullable PendingPreemption pending,
                                                        BehaviorTreeInstance instance,
                                                        int conflictOwner) {
        for (PendingPreemption candidate = pending; candidate != null; candidate = candidate.outer) {
            if (!candidate.consumed && isWithinSubtree(
                    instance, conflictOwner, candidate.previousNodeIndex)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isWithinSubtree(BehaviorTreeInstance instance, int nodeIndex, int rootIndex) {
        for (int current = nodeIndex; current >= 0; current = instance.plan().getParent(current)) {
            if (current == rootIndex) return true;
        }
        return false;
    }

    @Nullable
    private EvaluationFault terminateNode(BehaviorTreeInstance instance, int nodeIndex,
                                           BehaviorNodeExecutor executor, BehaviorNodeContext context,
                                           BehaviorTerminationReason reason,
                                           @Nullable String failureCode, @Nullable String detail,
                                           boolean propagateExitFailure, long elapsedNanos) {
        EvaluationFault childFailure = abortChildren(
                instance, nodeIndex, executor.childTerminationReason(reason));
        BehaviorNodeState current = instance.rawNodeState(nodeIndex);
        if (!current.isActive()) return propagateExitFailure ? childFailure : null;
        transition(instance, nodeIndex, BehaviorNodeState.EXITING);
        BehaviorTerminationReason finalReason = childFailure != null
                ? childFailure.reason : reason;
        String finalDetail = childFailure != null ? childFailure.getMessage() : detail;
        String finalFailureCode = childFailure == null
                && reason == BehaviorTerminationReason.COMPLETED_FAILURE ? failureCode : null;
        EvaluationFault exitFailure = childFailure;
        try {
            executor.exit(context, finalReason);
        } catch (BehaviorBudgetExceededException exception) {
            finalReason = BehaviorTerminationReason.BUDGET_EXHAUSTED;
            finalFailureCode = null;
            finalDetail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            exitFailure = new EvaluationFault(finalReason, finalDetail);
        } catch (BehaviorContractViolation exception) {
            finalReason = BehaviorTerminationReason.INVALID_DATA;
            finalFailureCode = null;
            finalDetail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            exitFailure = new EvaluationFault(finalReason, finalDetail);
        } catch (Exception exception) {
            finalReason = BehaviorTerminationReason.NODE_EXCEPTION;
            finalFailureCode = null;
            finalDetail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            exitFailure = new EvaluationFault(finalReason, finalDetail);
        }
        try {
            instance.releaseResources(nodeIndex,
                    instance.plan().getNodeCapabilities(nodeIndex).resources());
        } catch (Exception exception) {
            finalReason = BehaviorTerminationReason.NODE_EXCEPTION;
            finalFailureCode = null;
            finalDetail = exception.getMessage() != null
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            exitFailure = new EvaluationFault(finalReason, finalDetail);
        }
        transition(instance, nodeIndex, BehaviorLifecycleContract.terminalState(finalReason));
        instance.recordTermination(nodeIndex, finalReason, elapsedNanos,
                finalFailureCode, finalDetail);
        transition(instance, nodeIndex, BehaviorNodeState.IDLE);
        return propagateExitFailure ? exitFailure : null;
    }

    @Nullable
    private EvaluationFault abortChildren(BehaviorTreeInstance instance, int nodeIndex,
                                          BehaviorTerminationReason reason) {
        EvaluationFault firstFailure = null;
        for (int childIndex = instance.plan().getChildCount(nodeIndex) - 1; childIndex >= 0; childIndex--) {
            EvaluationFault failure = abortSubtree(
                    instance, instance.plan().getChild(nodeIndex, childIndex), reason);
            if (firstFailure == null) firstFailure = failure;
        }
        return firstFailure;
    }

    @Nullable
    private EvaluationFault abortSubtree(BehaviorTreeInstance instance, int nodeIndex,
                                         BehaviorTerminationReason reason) {
        if (nodeIndex < 0 || nodeIndex >= instance.plan().getNodeCount()) return null;
        if (!instance.rawNodeState(nodeIndex).isActive()) {
            return abortChildren(instance, nodeIndex, reason);
        }
        BehaviorNodeExecutor executor = executors.get(instance.plan().getNodeType(nodeIndex));
        if (executor == null) {
            String detail = "No behavior executor is registered for "
                    + instance.plan().getNodeType(nodeIndex);
            transition(instance, nodeIndex, BehaviorNodeState.EXITING);
            try {
                instance.releaseResources(nodeIndex,
                        instance.plan().getNodeCapabilities(nodeIndex).resources());
            } catch (Exception exception) {
                detail = exception.getMessage() != null
                        ? exception.getMessage() : exception.getClass().getSimpleName();
            }
            transition(instance, nodeIndex, BehaviorNodeState.ERROR);
            instance.recordTermination(nodeIndex, BehaviorTerminationReason.INVALID_DATA,
                    0L, null, detail);
            transition(instance, nodeIndex, BehaviorNodeState.IDLE);
            return new EvaluationFault(BehaviorTerminationReason.INVALID_DATA, detail);
        }
        BehaviorNodeContext context = new BehaviorNodeContext(this, instance, nodeIndex, 0,
                instance.host().gameTick());
        try {
            return terminateNode(instance, nodeIndex, executor, context,
                    reason, null, null, true, 0L);
        } finally {
            context.close();
        }
    }

    private static void transition(BehaviorTreeInstance instance, int nodeIndex,
                                   BehaviorNodeState target) {
        BehaviorNodeState current = instance.rawNodeState(nodeIndex);
        if (!BehaviorLifecycleContract.allows(current, target)) {
            throw new EvaluationFault(BehaviorTerminationReason.INVALID_DATA,
                    "Illegal behavior lifecycle transition: " + current + " -> " + target);
        }
        instance.setNodeState(nodeIndex, target);
    }

    private EvaluationPass requirePass(BehaviorTreeInstance instance) {
        EvaluationPass pass = currentPass.get();
        if (pass == null || pass.instance != instance) {
            throw new IllegalStateException("Behavior child evaluation escaped its evaluation epoch");
        }
        return pass;
    }

    private static long elapsed(long start, long end) {
        return Math.max(0L, end - start);
    }

    private static long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    public record EvaluationOutcome(@Nullable BehaviorResult result,
                                    @Nullable BehaviorTerminationReason failure,
                                    long nextWakeTick, int nodeVisits,
                                    long elapsedNanos, String detail) {
        static EvaluationOutcome failed(BehaviorTerminationReason reason, String detail) {
            return failed(reason, detail, 0, 0L);
        }

        static EvaluationOutcome failed(BehaviorTerminationReason reason, String detail,
                                        int nodeVisits, long elapsedNanos) {
            return new EvaluationOutcome(null, reason, Long.MAX_VALUE, nodeVisits, elapsedNanos,
                    detail != null ? detail : "");
        }

        static EvaluationOutcome skipped(long nextWakeTick) {
            return new EvaluationOutcome(null, null, nextWakeTick, 0, 0L, "");
        }

        public boolean succeeded() {
            return failure == null;
        }
    }

    private static final class EvaluationPass {
        private final BehaviorTreeInstance instance;
        @Nullable private PendingPreemption pendingPreemption;
        private int visits;
        private int immediateTransitions;

        private EvaluationPass(BehaviorTreeInstance instance) {
            this.instance = instance;
        }
    }

    private static final class PendingPreemption {
        private final int previousNodeIndex;
        private final BehaviorTerminationReason reason;
        @Nullable private final PendingPreemption outer;
        private boolean consumed;

        private PendingPreemption(int previousNodeIndex, BehaviorTerminationReason reason,
                                  @Nullable PendingPreemption outer) {
            this.previousNodeIndex = previousNodeIndex;
            this.reason = reason;
            this.outer = outer;
        }
    }

    private static final class EvaluationFault extends RuntimeException {
        private final BehaviorTerminationReason reason;

        private EvaluationFault(BehaviorTerminationReason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    private final class BehaviorDataContext implements GraphDataContext,
            com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboardView {
        private final BehaviorTreeInstance instance;
        private final int nodeIndex;

        private BehaviorDataContext(BehaviorTreeInstance instance, int nodeIndex) {
            this.instance = instance;
            this.nodeIndex = nodeIndex;
        }

        @Override public ServerLevel getLevel() { return instance.host().level(); }
        @Override public Entity getEntity() { return instance.host().owner(); }
        @Override public Entity getGraphOwnerEntity() { return instance.host().owner(); }
        @Override public String getGraphId() { return instance.plan().getNodeAssetId(nodeIndex); }

        @Override
        public Object getVariable(String name) {
            try {
                return instance.blackboard(nodeIndex).get(
                        ScopedStateScope.INSTANCE, name);
            } catch (ScopedStateAccessException exception) {
                return null;
            }
        }

        @Override
        public boolean hasVariable(String name) {
            try {
                return instance.blackboard(nodeIndex).contains(
                        ScopedStateScope.INSTANCE, name);
            } catch (ScopedStateAccessException exception) {
                return false;
            }
        }

        @Override
        public Object getBlackboard(
                ScopedStateScope scope,
                String name) {
            return instance.blackboard(nodeIndex).get(scope, name);
        }

        @Override
        public boolean hasBlackboard(
                ScopedStateScope scope,
                String name) {
            return instance.blackboard(nodeIndex).contains(scope, name);
        }

        @Override public Object getInputValue(String portName) {
            return resolveInput(instance, nodeIndex, portName);
        }

        @Override public Object getStaticInput(String portName) {
            return instance.plan().getStaticInput(nodeIndex, portName);
        }

        @Override public Object getEventData(String key) { return null; }
        @Override public boolean hasPort(String portName) { return instance.plan().hasPort(nodeIndex, portName); }

        @Override
        public Object getScopedState(ScopedStateTarget target, String name) {
            ServerLevel level = instance.host().level();
            if (level == null) return null;
            return GraphEngineServices.INSTANCE.scopedState().get(
                    new GraphRuntimeContext(level, instance.host().owner()), target, name);
        }
    }
}
