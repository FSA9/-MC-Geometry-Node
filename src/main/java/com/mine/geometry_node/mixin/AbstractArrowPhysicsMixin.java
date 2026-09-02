package com.mine.geometry_node.mixin;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies projectile control flags to the arrow's vanilla physics steps. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowPhysicsMixin {
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;applyInertia(F)V",
                    ordinal = 1))
    private void geometryNode$ignoreAirResistance(AbstractArrow arrow, float inertia) {
        if (!arrow.getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT).ignoreAirResistance()) {
            ((AbstractArrowAccessor) arrow).geometryNode$applyInertia(inertia);
        }
    }
}
