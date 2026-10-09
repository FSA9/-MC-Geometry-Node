package com.mine.geometry_node.mixin.model.collision;

import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains vanilla sneaking edge protection on model floors and stairs. */
@Mixin(Player.class)
public abstract class ModelPlayerCollisionMixin {
    @Inject(method = "canFallAtLeast", at = @At("RETURN"), cancellable = true)
    private void geometryNode$modelEdgeSupport(double dx, double dz, double height, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (!callback.getReturnValue() || ModelCollisionScene.snapshots(player.level()).isEmpty()) return;
        AABB b = player.getBoundingBox();
        AABB feet = new AABB(b.minX + 1.0e-7 + dx, b.minY - height - 1.0e-7, b.minZ + 1.0e-7 + dz,
                b.maxX - 1.0e-7 + dx, b.minY, b.maxZ - 1.0e-7 + dz);
        for (var candidate : ModelCollisionScene.query(player.level(), feet, player.getUUID())) {
            if (candidate.intersects(feet)) { callback.setReturnValue(false); return; }
        }
    }
}
