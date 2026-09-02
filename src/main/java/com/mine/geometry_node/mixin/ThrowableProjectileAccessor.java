package com.mine.geometry_node.mixin;

import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThrowableProjectile.class)
public interface ThrowableProjectileAccessor {
    @Invoker("applyInertia")
    void geometryNode$applyInertia();
}
