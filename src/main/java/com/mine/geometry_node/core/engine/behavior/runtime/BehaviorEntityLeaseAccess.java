package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode;

import java.util.Set;

/** Per-entity lease counts that survive replacement of the entity's Brain object. */
public interface BehaviorEntityLeaseAccess {
    int geometryNode$getBehaviorLeaseMask();

    void geometryNode$acquireBehaviorLeases(Set<BehaviorExecutableNode.Resource> resources);

    void geometryNode$releaseBehaviorLeases(Set<BehaviorExecutableNode.Resource> resources);
}
