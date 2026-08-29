package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorEntityLeaseAccess;
import com.mine.geometry_node.core.node.NodeCapabilities;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;

@Mixin(Mob.class)
public abstract class MobBehaviorLeaseMixin implements BehaviorEntityLeaseAccess {
    @Unique private int[] geometryNode$behaviorLeaseCounts;
    @Unique private int geometryNode$behaviorLeaseMask;

    @Override
    public int geometryNode$getBehaviorLeaseMask() {
        return geometryNode$behaviorLeaseMask;
    }

    @Override
    public void geometryNode$acquireBehaviorLeases(Set<NodeCapabilities.ResourceUse> resources) {
        if (resources.isEmpty()) return;
        if (geometryNode$behaviorLeaseCounts == null) {
            geometryNode$behaviorLeaseCounts = new int[NodeCapabilities.ResourceUse.values().length];
        }
        for (NodeCapabilities.ResourceUse resource : resources) {
            int ordinal = resource.ordinal();
            if (geometryNode$behaviorLeaseCounts[ordinal]++ == 0) {
                geometryNode$behaviorLeaseMask |= 1 << ordinal;
            }
        }
    }

    @Override
    public void geometryNode$releaseBehaviorLeases(Set<NodeCapabilities.ResourceUse> resources) {
        if (geometryNode$behaviorLeaseCounts == null || resources.isEmpty()) return;
        for (NodeCapabilities.ResourceUse resource : resources) {
            int ordinal = resource.ordinal();
            int count = geometryNode$behaviorLeaseCounts[ordinal];
            if (count <= 1) {
                geometryNode$behaviorLeaseCounts[ordinal] = 0;
                geometryNode$behaviorLeaseMask &= ~(1 << ordinal);
            } else {
                geometryNode$behaviorLeaseCounts[ordinal] = count - 1;
            }
        }
    }
}
