package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorActionFailure;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.Objects;
import java.util.UUID;

/** Node-scoped view of one deterministic evaluation epoch. */
public final class BehaviorNodeContext {
    private final BehaviorTreeEvaluator evaluator;
    private final BehaviorTreeInstance instance;
    private final int nodeIndex;
    private final int depth;
    private final long epochTick;
    @Nullable private BehaviorActionFailure actionFailure;
    private boolean valid = true;

    BehaviorNodeContext(BehaviorTreeEvaluator evaluator, BehaviorTreeInstance instance,
                        int nodeIndex, int depth, long epochTick) {
        this.evaluator = evaluator;
        this.instance = instance;
        this.nodeIndex = nodeIndex;
        this.depth = depth;
        this.epochTick = epochTick;
    }

    public UUID instanceId() { return instance.instanceId(); }
    public String graphId() { return instance.graphId(); }
    public int nodeIndex() { return nodeIndex; }
    public String nodeId() { return instance.plan().getNodeId(nodeIndex); }
    public long gameTick() { ensureValid(); return epochTick; }
    public int childCount() { ensureValid(); return instance.plan().getChildCount(nodeIndex); }
    @Nullable public ServerLevel level() { ensureValid(); return instance.host().level(); }
    @Nullable public Entity owner() { ensureValid(); return instance.host().owner(); }
    public Random random() { ensureValid(); return instance.random(); }
    public BehaviorBlackboard blackboard() { ensureValid(); return instance.blackboard(); }

    public BehaviorResult tickChild(int childIndex) {
        ensureValid();
        if (childIndex < 0 || childIndex >= childCount()) {
            throw new BehaviorContractViolation("Behavior child index out of bounds: " + childIndex);
        }
        return evaluator.evaluateNode(instance, instance.plan().getChild(nodeIndex, childIndex),
                depth + 1, epochTick);
    }

    BehaviorResult tickChildReplacing(int childIndex, int previousChildIndex,
                                      BehaviorTerminationReason reason) {
        ensureValid();
        if (childIndex < 0 || childIndex >= childCount()
                || previousChildIndex < 0 || previousChildIndex >= childCount()) {
            throw new BehaviorContractViolation("Behavior child index out of bounds");
        }
        return evaluator.evaluateNodeReplacing(instance,
                instance.plan().getChild(nodeIndex, childIndex),
                instance.plan().getChild(nodeIndex, previousChildIndex),
                Objects.requireNonNull(reason, "reason"), depth + 1, epochTick);
    }

    public void abortChild(int childIndex, BehaviorTerminationReason reason) {
        ensureValid();
        if (childIndex < 0 || childIndex >= childCount()) {
            throw new BehaviorContractViolation("Behavior child index out of bounds: " + childIndex);
        }
        evaluator.abortChild(instance, instance.plan().getChild(nodeIndex, childIndex),
                Objects.requireNonNull(reason, "reason"));
    }

    @Nullable
    public Object input(String portName) {
        ensureValid();
        return evaluator.resolveInput(instance, nodeIndex, portName);
    }

    @Nullable
    public <T> T input(String portName, Class<T> type) {
        ensureValid();
        return evaluator.resolveInput(instance, nodeIndex, portName, type);
    }

    /** Reads an optional input while distinguishing absence from a conversion failure. */
    @Nullable
    public <T> T optionalTypedInput(String portName, Class<T> type) {
        ensureValid();
        Object raw = input(portName);
        if (raw == null) return null;
        T converted = evaluator.convertInput(instance, nodeIndex, raw, type);
        if (converted == null) {
            throw new BehaviorContractViolation(portName + " does not match " + type.getSimpleName());
        }
        return converted;
    }

    /** Reads an input that must be present and convertible to its declared runtime type. */
    public <T> T requiredInput(String portName, Class<T> type) {
        T value = optionalTypedInput(portName, type);
        if (value == null) throw new BehaviorContractViolation(portName + " is required");
        return value;
    }

    @Nullable
    public Object staticInput(String portName) {
        ensureValid();
        return instance.plan().getStaticInput(nodeIndex, portName);
    }

    @Nullable
    public Object memory() {
        ensureValid();
        return instance.nodeMemory(nodeIndex);
    }

    public void setMemory(@Nullable Object value) {
        ensureValid();
        instance.setNodeMemory(nodeIndex, value);
    }

    public void requestWakeupAt(long gameTick) {
        ensureValid();
        instance.requestWakeup(gameTick);
    }

    public void requestWakeupAfter(long ticks) {
        ensureValid();
        if (ticks < 0) throw new BehaviorContractViolation("Wakeup delay cannot be negative");
        long due = ticks > Long.MAX_VALUE - epochTick ? Long.MAX_VALUE : epochTick + ticks;
        instance.requestWakeup(due);
    }

    @Nullable
    public Object getBlackboard(BlackboardScope scope, String name) {
        ensureValid();
        return instance.blackboard().get(scope, name);
    }

    public void setBlackboard(BlackboardScope scope, String name, @Nullable Object value) {
        ensureValid();
        instance.blackboard().set(scope, name, value, nodeId(), epochTick);
        instance.dataEvaluation().clearValues();
    }

    public boolean clearBlackboard(BlackboardScope scope, String name) {
        ensureValid();
        boolean changed = instance.blackboard().clear(scope, name, nodeId(), epochTick);
        if (changed) instance.dataEvaluation().clearValues();
        return changed;
    }

    public void reportActionFailure(@Nullable BehaviorActionFailure failure) {
        ensureValid();
        actionFailure = failure;
    }

    @Nullable
    BehaviorActionFailure actionFailure() {
        return actionFailure;
    }

    void close() {
        valid = false;
    }

    private void ensureValid() {
        if (!valid) throw new IllegalStateException("Behavior node context is no longer active");
    }
}
