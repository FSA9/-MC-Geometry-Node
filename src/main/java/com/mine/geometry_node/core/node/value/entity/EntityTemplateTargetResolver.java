package com.mine.geometry_node.core.node.value.entity;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the owning entity of a selectable entity part.
 * Implementations return {@code null} when they do not recognize the entity.
 */
@FunctionalInterface
public interface EntityTemplateTargetResolver {
    @Nullable
    Entity resolveParent(Entity selected);
}
