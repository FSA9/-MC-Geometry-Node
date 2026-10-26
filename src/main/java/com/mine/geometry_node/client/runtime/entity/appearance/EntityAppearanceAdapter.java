package com.mine.geometry_node.client.runtime.entity.appearance;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/** Client-thread extension for one target type. Register its matching common parameter validator too. */
public interface EntityAppearanceAdapter {
    boolean validateParameters(Map<String, String> parameters);

    /** Called once at construction. Mutate only the detached proxy; do not spawn, tick or keep it. */
    void apply(LivingEntity proxy, Map<String, String> parameters);

    /** Called after generic source pose/equipment mapping, before full target renderer extraction. */
    default void synchronize(Entity source, LivingEntity proxy, float partialTicks) {}

    /** Must describe the same visual used by the target renderer, in world coordinates. */
    default AABB visualBounds(LivingEntity proxy, EntityRenderer<Entity, EntityRenderState> renderer) {
        return renderer instanceof AppearanceRendererBounds access
                ? access.geometryNode$appearanceBounds(proxy) : proxy.getBoundingBox();
    }

    /** Optional target-specific animation correction. Never retain this mutable state. */
    default void finish(Entity source, LivingEntity proxy, EntityRenderState state, float partialTicks) {}

    /** Called once before releasing the detached proxy, including failed initialization. */
    default void release(LivingEntity proxy) {}
}
