package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.node.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

/** Executors for general-purpose behavior actions. */
public final class BehaviorActionExecutors {
    private static final BehaviorNodeExecutor WAIT = new WaitExecutor();
    private static final BehaviorNodeExecutor IDLE = new IdleExecutor();

    private BehaviorActionExecutors() {
    }

    public static BehaviorNodeExecutor waitExecutor() {
        return WAIT;
    }

    public static BehaviorNodeExecutor idleExecutor() {
        return IDLE;
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new BehaviorContractViolation(message);
        return value;
    }

    private static long deadline(long tick, int delay) {
        if (delay < 0) throw new BehaviorContractViolation("Tick delay cannot be negative");
        return delay > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + delay;
    }

    private static final class WaitExecutor implements BehaviorNodeExecutor {
        @Override
        public void enter(BehaviorNodeContext context) {
            Integer ticks = require(context.input(StandardPorts.BEHAVIOR_TICKS.getId(), Integer.class),
                    "Wait ticks are missing");
            context.setMemory(deadline(context.gameTick(), ticks));
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            Long deadline = require(context.memory() instanceof Long value ? value : null,
                    "Wait deadline is unavailable");
            if (context.gameTick() >= deadline) return BehaviorResult.SUCCESS;
            context.requestWakeupAt(deadline);
            return BehaviorResult.RUNNING;
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }
    }

    private static final class IdleExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            Integer interval = require(context.input(
                    StandardPorts.POLL_INTERVAL.getId(), Integer.class),
                    "Idle poll interval is missing");
            if (interval <= 0) {
                throw new BehaviorContractViolation("Idle poll interval must be positive");
            }
            context.requestWakeupAt(deadline(context.gameTick(), interval));
            return BehaviorResult.RUNNING;
        }
    }
}
