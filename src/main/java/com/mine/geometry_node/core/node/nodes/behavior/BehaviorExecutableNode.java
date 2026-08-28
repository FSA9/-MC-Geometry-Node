package com.mine.geometry_node.core.node.nodes.behavior;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;

/** A behavior node that owns the runtime executor registered with its NodeDef. */
public interface BehaviorExecutableNode {
    BehaviorNodeExecutor behaviorExecutor();
}
