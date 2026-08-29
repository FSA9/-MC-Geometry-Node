package com.mine.geometry_node.core.engine.behavior.runtime;

/** Per-Brain behavior resource leases used by the Brain mixin hot path. */
public interface BehaviorBrainLeaseAccess {
    int geometryNode$getBehaviorLeaseMask();

    void geometryNode$setBehaviorLeaseMask(int mask);
}
