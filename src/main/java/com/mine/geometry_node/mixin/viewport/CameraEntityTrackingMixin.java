package com.mine.geometry_node.mixin.viewport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraObservers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class CameraEntityTrackingMixin {
    @Shadow @Final private Entity entity;
    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 geometryNode$nearestObserver(ServerPlayer player, Operation<Vec3> original) {
        Vec3 body = original.call(player);
        return EditCameraObservers.nearest(body, EditCameraChunkService.positions(player), entity.position());
    }
}
