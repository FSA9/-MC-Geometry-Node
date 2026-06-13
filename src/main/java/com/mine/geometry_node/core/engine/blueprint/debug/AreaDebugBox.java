package com.mine.geometry_node.core.engine.blueprint.debug;

import net.minecraft.world.phys.Vec3;

public record AreaDebugBox(
        String id,
        String graphId,
        String shape,
        Vec3 center,
        Vec3 size,
        Vec3 rotation
) {
}
