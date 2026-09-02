package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.blueprint.projectile.ProjectileImpactController;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** FishingHook calls onHit directly instead of Projectile.hitTargetOrDeflectSelf. */
@Mixin(FishingHook.class)
public abstract class FishingHookImpactMixin {
    @Redirect(
            method = "checkCollision",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/FishingHook;onHit(Lnet/minecraft/world/phys/HitResult;)V")
    )
    private void geometryNode$handleImpact(FishingHook hook, HitResult hitResult) {
        if (!ProjectileImpactController.handleImpact(hook, hitResult)) {
            ((ProjectileAccessor) hook).geometryNode$onHit(hitResult);
        }
    }
}
