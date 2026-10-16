package com.mine.geometry_node.mixin.viewport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
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
        Vec3 camera = EditCameraChunkService.position(player);
        if (camera == null) return body;
        Vec3 target = entity.position();
        double bodyDistance = (body.x - target.x) * (body.x - target.x) + (body.z - target.z) * (body.z - target.z);
        double cameraDistance = (camera.x - target.x) * (camera.x - target.x) + (camera.z - target.z) * (camera.z - target.z);
        return cameraDistance < bodyDistance ? camera : body;
    }
}
