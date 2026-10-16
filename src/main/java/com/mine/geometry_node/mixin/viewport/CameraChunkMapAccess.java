package com.mine.geometry_node.mixin.viewport;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface CameraChunkMapAccess {
    @Invoker("updateChunkTracking") void geometryNode$updateTracking(ServerPlayer player);
    @Invoker("getPlayerViewDistance") int geometryNode$viewDistance(ServerPlayer player);
}
