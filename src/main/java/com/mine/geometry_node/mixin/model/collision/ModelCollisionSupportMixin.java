package com.mine.geometry_node.mixin.model.collision;

import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Model support participates in the server's existing floating check. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ModelCollisionSupportMixin {
    @Inject(method = "noBlocksAround", at = @At("RETURN"), cancellable = true)
    private void geometryNode$modelSupport(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() || ModelCollisionScene.snapshots(entity.level()).isEmpty()) return;
        AABB box = entity.getBoundingBox();
        Vec3 down = new Vec3(0, -0.55, 0);
        for (var candidate : ModelCollisionScene.query(entity.level(), box.expandTowards(down).inflate(1.0e-6), entity.getUUID())) {
            var hit = candidate.sweep(box, down);
            if (hit != null && hit.normal().y >= 0.5) { callback.setReturnValue(false); return; }
        }
    }
}
