package com.mine.geometry_node.core.node.nodes.behavior;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;

import java.util.Set;

/** A behavior node that owns the runtime executor registered with its NodeDef. */
public interface BehaviorExecutableNode {
    BehaviorNodeExecutor behaviorExecutor();

    default Set<Resource> requiredResources() {
        return Set.of();
    }

    enum Resource { MOVEMENT, LOOK, TARGET }
}
