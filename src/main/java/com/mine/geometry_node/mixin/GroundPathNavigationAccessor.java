package com.mine.geometry_node.mixin;

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes mutable ground-navigation probe tuning. */
@Mixin(GroundPathNavigation.class)
public interface GroundPathNavigationAccessor {
    @Accessor("canPathToTargetsBelowSurface")
    boolean geometryNode$getCanPathToTargetsBelowSurface();
}
