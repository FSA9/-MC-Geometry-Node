package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.NodeCapabilities;

import java.util.Set;

/** Per-entity lease counts that survive replacement of the entity's Brain object. */
public interface BehaviorEntityLeaseAccess {
    int geometryNode$getBehaviorLeaseMask();

    void geometryNode$acquireBehaviorLeases(Set<NodeCapabilities.ResourceUse> resources);

    void geometryNode$releaseBehaviorLeases(Set<NodeCapabilities.ResourceUse> resources);
}
