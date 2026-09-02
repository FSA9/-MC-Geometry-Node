package com.mine.geometry_node.mixin;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the shared air-resistance flag to snowballs and other throwables. */
@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectilePhysicsMixin {
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ThrowableProjectile;applyInertia()V"))
    private void geometryNode$ignoreAirResistance(ThrowableProjectile projectile) {
        if (!projectile.getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT).ignoreAirResistance()) {
            ((ThrowableProjectileAccessor) projectile).geometryNode$applyInertia();
        }
    }
}
