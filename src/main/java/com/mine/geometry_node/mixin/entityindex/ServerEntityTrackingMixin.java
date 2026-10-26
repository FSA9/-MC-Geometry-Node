package com.mine.geometry_node.mixin.entityindex;

import com.mine.geometry_node.core.engine.system.entity.index.ServerEntityIndexService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe successful admission and tracking restarts, rather than a cancelable pre-join event. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerEntityTrackingMixin {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void geometryNode$entityAvailable(Entity entity, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel level) {
            try { ServerEntityIndexService.INSTANCE.entityTrackingStarted(level, entity); }
            catch (RuntimeException failure) {
                System.getLogger(ServerEntityTrackingMixin.class.getName()).log(System.Logger.Level.WARNING,
                        "Entity directory tracking update failed", failure);
            }
        }
    }
}
