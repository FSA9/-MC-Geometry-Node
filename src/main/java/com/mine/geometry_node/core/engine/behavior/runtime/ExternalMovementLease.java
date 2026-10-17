package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.nodes.behavior.BehaviorExecutableNode.Resource;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** External commands share native Goal/Brain ownership with BT actions. Server-thread only. */
public final class ExternalMovementLease implements AutoCloseable {
    private static final Map<Mob, ExternalMovementLease> ACTIVE = new IdentityHashMap<>();
    private static final Set<Resource> RESOURCES = Set.of(Resource.MOVEMENT);
    private final Mob owner;
    private final BehaviorNativeAiController controller;
    private boolean closed;
    private ExternalMovementLease(Mob owner, BehaviorNativeAiController controller) {
        this.owner = owner; this.controller = controller;
    }
    /** Interrupts the previous controller through its normal cleanup before acquiring MOVEMENT. */
    public static ExternalMovementLease takeOver(Mob owner) {
        var previous = ACTIVE.get(owner);
        if (previous != null) previous.close();
        var behavior = com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime.INSTANCE;
        var process = behavior.getForOwner(owner);
        if (process != null && owner.level() instanceof net.minecraft.server.level.ServerLevel level) {
            behavior.stop(level.getServer(), process.instanceId(),
                    com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason.PRIORITY_PREEMPTED);
        }
        owner.getNavigation().stop();
        return acquire(owner);
    }
    public static ExternalMovementLease acquire(Mob owner) {
        if (ACTIVE.containsKey(owner) || !(owner instanceof BehaviorEntityLeaseAccess access)
                || (access.geometryNode$getBehaviorLeaseMask() & (1 << Resource.MOVEMENT.ordinal())) != 0) return null;
        var controller = new BehaviorNativeAiController(owner);
        if (!controller.acquire(RESOURCES)) return null;
        var lease = new ExternalMovementLease(owner, controller);
        ACTIVE.put(owner, lease);
        return lease;
    }
    public static boolean conflicts(Mob owner, Set<Resource> resources) {
        return resources.contains(Resource.MOVEMENT) && ACTIVE.containsKey(owner);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(owner, this);
        try { owner.getNavigation().stop(); }
        finally { controller.release(RESOURCES); }
    }
}
