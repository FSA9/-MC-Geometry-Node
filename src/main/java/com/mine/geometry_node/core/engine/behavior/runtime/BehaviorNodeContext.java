package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/** Node-scoped view of one deterministic evaluation epoch. */
public final class BehaviorNodeContext {
    private final BehaviorTreeEvaluator evaluator;
    private final BehaviorTreeInstance instance;
    private final int nodeIndex;
    private final int depth;
    private final long epochTick;
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
            throw new IllegalArgumentException("Behavior child index out of bounds: " + childIndex);
        }
        return evaluator.evaluateNode(instance, instance.plan().getChild(nodeIndex, childIndex),
                depth + 1, epochTick);
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
        if (ticks < 0) throw new IllegalArgumentException("Wakeup delay cannot be negative");
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

    void close() {
        valid = false;
    }

    private void ensureValid() {
        if (!valid) throw new IllegalStateException("Behavior node context is no longer active");
    }
}
