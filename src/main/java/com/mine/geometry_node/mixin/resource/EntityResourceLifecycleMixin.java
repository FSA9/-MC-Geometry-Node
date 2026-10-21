package com.mine.geometry_node.mixin.resource;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracking end is not a destruction event: an inaccessible entity can later be removed silently. */
@Mixin(Entity.class)
public abstract class EntityResourceLifecycleMixin {
    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void geometryNode$finishResourceLifetime(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel level)
            GraphResourceLifecycleManager.INSTANCE.entityRemoved(level, entity, reason);
    }
}
