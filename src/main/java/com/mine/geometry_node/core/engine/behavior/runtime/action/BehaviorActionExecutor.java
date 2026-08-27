package com.mine.geometry_node.core.engine.behavior.runtime.action;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import org.jetbrains.annotations.Nullable;

/** Shared lifecycle template for actions with ordinary Success/Running/Failure outcomes. */
public abstract class BehaviorActionExecutor<S> implements BehaviorNodeExecutor {
    protected abstract BehaviorActionStep<S> start(BehaviorNodeContext context);

    protected abstract BehaviorActionStep<S> tick(BehaviorNodeContext context, S state);

    protected void stop(BehaviorNodeContext context, @Nullable S state,
                        BehaviorTerminationReason reason) {
    }

    @Override
    public final void enter(BehaviorNodeContext context) {
        BehaviorActionStep<S> step = requireStep(start(context));
        context.setMemory(new ActionFrame<>(step, step.state()));
    }

    @Override
    public final BehaviorResult update(BehaviorNodeContext context) {
        ActionFrame<S> frame = readFrame(context);
        BehaviorActionStep<S> step = frame.step();
        S lastState = frame.lastState();
        if (step.result() == BehaviorResult.RUNNING) {
            step = requireStep(tick(context, requireState(step)));
            if (step.state() != null) lastState = step.state();
            frame = new ActionFrame<>(step, lastState);
            context.setMemory(frame);
        }
        context.reportActionFailure(step.failure());
        return step.result();
    }

    @Override
    public final void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
        ActionFrame<S> frame = null;
        BehaviorContractViolation frameFailure = null;
        try {
            frame = readFrameOrNull(context);
        } catch (BehaviorContractViolation exception) {
            frameFailure = exception;
        }
        try {
            stop(context, frame != null ? frame.lastState() : null, reason);
        } finally {
            context.setMemory(null);
        }
        if (frameFailure != null) throw frameFailure;
    }

    private static <S> BehaviorActionStep<S> requireStep(@Nullable BehaviorActionStep<S> step) {
        if (step == null) throw new BehaviorContractViolation("Action returned no step");
        return step;
    }

    private static <S> S requireState(BehaviorActionStep<S> step) {
        S state = step.state();
        if (state == null) throw new BehaviorContractViolation("Running action state is missing");
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <S> ActionFrame<S> readFrame(BehaviorNodeContext context) {
        Object memory = context.memory();
        if (!(memory instanceof ActionFrame<?> frame)) {
            throw new BehaviorContractViolation("Action lifecycle state is missing or invalid");
        }
        return (ActionFrame<S>) frame;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <S> ActionFrame<S> readFrameOrNull(BehaviorNodeContext context) {
        Object memory = context.memory();
        if (memory == null) return null;
        if (!(memory instanceof ActionFrame<?> frame)) {
            throw new BehaviorContractViolation("Action lifecycle state is invalid");
        }
        return (ActionFrame<S>) frame;
    }

    private record ActionFrame<S>(BehaviorActionStep<S> step, @Nullable S lastState) {
    }
}
