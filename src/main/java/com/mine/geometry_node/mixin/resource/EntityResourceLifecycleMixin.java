package com.mine.geometry_node.mixin.resource;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.system.entity.index.ServerEntityIndexService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracking end is not a destruction event: an inaccessible entity can later be removed silently. */
@Mixin(Entity.class)
public abstract class EntityResourceLifecycleMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void geometryNode$observeBeforeRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.getRemovalReason() == null && entity.level() instanceof ServerLevel level) {
            try { ServerEntityIndexService.INSTANCE.entityRemoving(level, entity, reason); }
            catch (RuntimeException failure) {
                System.getLogger(EntityResourceLifecycleMixin.class.getName()).log(System.Logger.Level.WARNING,
                        "Entity directory observation failed before removal", failure);
            }
        }
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void geometryNode$finishResourceLifetime(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel level) {
            try { ServerEntityIndexService.INSTANCE.entityRemoved(level, entity, entity.getRemovalReason()); }
            catch (RuntimeException failure) {
                System.getLogger(EntityResourceLifecycleMixin.class.getName()).log(System.Logger.Level.WARNING,
                        "Entity directory removal update failed", failure);
            }
            GraphResourceLifecycleManager.INSTANCE.entityRemoved(level, entity, reason);
        }
    }
}
