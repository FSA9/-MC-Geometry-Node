package com.mine.geometry_node.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creates a navigation instance for isolated path probes without mutating the live navigator. */
@Mixin(Mob.class)
public interface MobNavigationInvoker {
    @Invoker("createNavigation")
    PathNavigation geometryNode$createNavigation(Level level);
}
