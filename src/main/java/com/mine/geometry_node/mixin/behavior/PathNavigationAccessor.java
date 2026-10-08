package com.mine.geometry_node.mixin.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes pathfinder tuning needed to mirror a live navigator in an isolated probe. */
@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Accessor("mob")
    Mob geometryNode$getMob();

    @Accessor("maxVisitedNodesMultiplier")
    float geometryNode$getMaxVisitedNodesMultiplier();

    @Accessor("requiredPathLength")
    float geometryNode$getRequiredPathLength();
}
