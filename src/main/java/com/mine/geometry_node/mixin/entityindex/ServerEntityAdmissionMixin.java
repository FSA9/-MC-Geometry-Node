package com.mine.geometry_node.mixin.entityindex;

import com.mine.geometry_node.core.engine.system.entity.index.ServerEntityIndexService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Successful admission also covers hidden sections and the player's event-free manager path. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class ServerEntityAdmissionMixin {
    @Inject(method = "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("RETURN"))
    private void geometryNode$entityAdmitted(EntityAccess candidate, boolean loaded, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && candidate instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            try { ServerEntityIndexService.INSTANCE.entityAdmitted(level, entity); }
            catch (RuntimeException failure) {
                System.getLogger(ServerEntityAdmissionMixin.class.getName()).log(System.Logger.Level.WARNING,
                        "Entity directory admission update failed", failure);
            }
        }
    }
}
