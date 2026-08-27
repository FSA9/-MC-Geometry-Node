package com.mine.geometry_node.core.engine.behavior.runtime.action;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;

/** Base for actions that always complete during their first evaluation. */
public abstract class InstantBehaviorActionExecutor extends BehaviorActionExecutor<Void> {
    protected abstract BehaviorActionStep<Void> execute(BehaviorNodeContext context);

    @Override
    protected final BehaviorActionStep<Void> start(BehaviorNodeContext context) {
        return execute(context);
    }

    @Override
    protected final BehaviorActionStep<Void> tick(BehaviorNodeContext context, Void state) {
        throw new BehaviorContractViolation("Instant action cannot enter Running state");
    }
}
