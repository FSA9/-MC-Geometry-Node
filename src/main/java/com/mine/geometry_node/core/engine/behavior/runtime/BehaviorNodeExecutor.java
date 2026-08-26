package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;

/**
 * Stateless node lifecycle adapter. Per-instance state belongs in the execution context,
 * which must not be retained after a lifecycle callback returns.
 */
public interface BehaviorNodeExecutor {
    default void enter(BehaviorNodeContext context) throws Exception {
    }

    BehaviorResult update(BehaviorNodeContext context) throws Exception;

    default void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) throws Exception {
    }
}
