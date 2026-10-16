package com.mine.geometry_node.mixin.game.compat.sodium;

import com.mine.geometry_node.client.runtime.game.chunk.ViewportChunkReadiness;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve readiness transitions that a final ready-set snapshot cannot show. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", remap = false)
public abstract class EditViewportSodiumChunkEventsMixin {
    @Shadow @Final private Long2IntOpenHashMap chunkStatus;

    @Inject(method = "onChunkStatusRemoved", at = @At("HEAD"), remap = false)
    private void geometryNode$invalidateCachedIncarnations(int x, int z, int flags, CallbackInfo ci) {
        // Sodium requires block-data and light bits (3) in the entire 3x3
        // neighborhood. Ignore no-op removal calls and unrelated status flags.
        if ((chunkStatus.get(ChunkPos.pack(x, z)) & flags & 3) == 0) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ViewportChunkReadiness.invalidate(this, ChunkPos.pack(x + dx, z + dz));
            }
        }
    }
}
