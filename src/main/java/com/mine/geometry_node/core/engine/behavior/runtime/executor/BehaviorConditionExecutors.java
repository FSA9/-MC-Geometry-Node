package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.node.nodes.behavior.condition.BehaviorUtilityConditionNode;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Executors for behavior predicates and utility conditions. */
public final class BehaviorConditionExecutors {
    private static final BehaviorNodeExecutor CONDITION = context ->
            require(context.input(StandardPorts.CONDITION.getId(), Boolean.class),
                    "Condition input is missing")
                    ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
    private static final BehaviorNodeExecutor HAS_VALID_TARGET = context -> {
        Entity entity = context.input(StandardPorts.ENTITY.getId(), Entity.class);
        return entity != null && entity.isAlive() && !entity.isRemoved()
                ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
    };
    private static final BehaviorNodeExecutor BLACKBOARD_VALUE_CHANGED =
            new BlackboardValueChangedExecutor();

    private BehaviorConditionExecutors() {
    }

    public static BehaviorNodeExecutor condition() {
        return CONDITION;
    }

    public static BehaviorNodeExecutor hasValidTarget() {
        return HAS_VALID_TARGET;
    }

    public static BehaviorNodeExecutor forKind(BehaviorUtilityConditionNode.Kind kind) {
        return switch (kind) {
            case BLACKBOARD_VALUE_CHANGED -> BLACKBOARD_VALUE_CHANGED;
            case CAN_NAVIGATE_TO -> BehaviorEntityExecutors.canNavigateTo();
        };
    }

    private static String key(BehaviorNodeContext context) {
        String key = context.input(StandardPorts.KEY.getId(), String.class);
        return key != null ? key : "";
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new BehaviorContractViolation(message);
        return value;
    }

    private static final class BlackboardValueChangedExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            String key = key(context);
            BehaviorBlackboard.ObservationToken current =
                    context.observeBlackboard(context.blackboardScope(), key);
            BehaviorBlackboard.ObservationToken previous = context.memory() instanceof
                    BehaviorBlackboard.ObservationToken value ? value : current;
            context.setMemory(current);
            return !current.equals(previous) ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
        }
    }
}
