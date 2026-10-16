package com.mine.geometry_node.mixin.viewport;

import com.mine.geometry_node.core.engine.system.viewport.CameraChunkTrackingView;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketEditCameraView;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class CameraChunkMapMixin {
    @Shadow private int getPlayerViewDistance(ServerPlayer player) { throw new AssertionError(); }
    @Shadow private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view) { throw new AssertionError(); }

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void geometryNode$trackCamera(ServerPlayer player, CallbackInfo ci) {
        var position = EditCameraChunkService.position(player);
        if (position == null) return;
        int radius = getPlayerViewDistance(player);
        ChunkPos center = new ChunkPos((int) Math.floor(position.x / 16), (int) Math.floor(position.z / 16));
        var next = new CameraChunkTrackingView(new ChunkTrackingView.Positioned(player.chunkPosition(), radius),
                new ChunkTrackingView.Positioned(center, radius));
        if (!next.equals(player.getChunkTrackingView())) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(player.chunkPosition().x(), player.chunkPosition().z()));
            NetworkHandler.sendToPlayer(player, new PacketEditCameraView(player.level().dimension().identifier(), true, center.x(), center.z(), radius));
            applyChunkTrackingView(player, next);
        }
        ci.cancel();
    }
}
