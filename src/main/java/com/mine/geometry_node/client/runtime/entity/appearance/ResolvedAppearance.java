package com.mine.geometry_node.client.runtime.entity.appearance;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** One extraction only: renderer and state are a matched pair. Do not cache or share the mutable state. */
public record ResolvedAppearance(EntityRenderer<Entity, EntityRenderState> renderer, EntityRenderState state,
                                 AABB visualBounds, Entity source, Entity proxy) {}
