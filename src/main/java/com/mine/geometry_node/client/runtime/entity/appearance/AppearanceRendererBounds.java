package com.mine.geometry_node.client.runtime.entity.appearance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Implemented by the renderer access mixin, preserving renderer-specific culling bounds. */
public interface AppearanceRendererBounds {
    AABB geometryNode$appearanceBounds(Entity entity);
}
