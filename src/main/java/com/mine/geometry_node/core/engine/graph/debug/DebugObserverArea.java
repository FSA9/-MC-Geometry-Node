package com.mine.geometry_node.core.engine.graph.debug;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

record DebugObserverArea(Vec3 origin, double radius) {
    AABB bounds() {
        return AABB.ofSize(origin, radius * 2.0D, radius * 2.0D, radius * 2.0D);
    }
}
