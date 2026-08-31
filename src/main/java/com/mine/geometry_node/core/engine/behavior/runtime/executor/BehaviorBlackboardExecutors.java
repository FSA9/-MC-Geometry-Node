package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

/** Executors that mutate scoped blackboard state. */
public final class BehaviorBlackboardExecutors {
    private static final BehaviorNodeExecutor SET = context -> {
        String key = key(context);
        Object value = require(context.input(StandardPorts.ANY_VALUE.getId()),
                "Blackboard value is missing: " + key);
        context.setBlackboard(context.blackboardScope(), key, value);
        return BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor CLEAR = context -> {
        String key = key(context);
        context.clearBlackboard(context.blackboardScope(), key);
        return BehaviorResult.SUCCESS;
    };

    private BehaviorBlackboardExecutors() {
    }

    public static BehaviorNodeExecutor set() {
        return SET;
    }

    public static BehaviorNodeExecutor clear() {
        return CLEAR;
    }

    private static String key(BehaviorNodeContext context) {
        String key = context.input(StandardPorts.KEY.getId(), String.class);
        return key != null ? key : "";
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new BehaviorContractViolation(message);
        return value;
    }
}
