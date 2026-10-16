package com.mine.geometry_node.mixin.viewport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerChunkSender.class)
public abstract class CameraChunkSenderMixin {
    @WrapOperation(method = "sendNextChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos geometryNode$prioritizeCamera(ServerPlayer player, Operation<ChunkPos> original) {
        var camera = EditCameraChunkService.position(player);
        return camera == null ? original.call(player)
                : new ChunkPos((int) Math.floor(camera.x / 16), (int) Math.floor(camera.z / 16));
    }
}
