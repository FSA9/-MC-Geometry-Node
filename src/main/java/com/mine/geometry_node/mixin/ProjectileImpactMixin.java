package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.blueprint.projectile.ProjectileImpactController;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileImpactMixin {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void geometryNode$handleImpact(HitResult hitResult,
                                           CallbackInfoReturnable<ProjectileDeflection> cir) {
        Projectile projectile = (Projectile) (Object) this;
        if (ProjectileImpactController.handleImpact(projectile, hitResult)) {
            // A non-NONE sentinel also terminates AbstractArrow's precomputed
            // piercing-hit collection; the deflection callback is not invoked here.
            cir.setReturnValue(ProjectileDeflection.REVERSE);
        }
    }
}
