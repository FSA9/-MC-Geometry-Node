package com.mine.geometry_node.mixin.viewport;

import com.mine.geometry_node.core.engine.system.viewport.CameraChunkTrackingView;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(ChunkTrackingView.class)
public interface CameraChunkTrackingMixin {
    @Inject(method = "difference", at = @At("HEAD"), cancellable = true)
    private static void geometryNode$diffCameraUnion(ChunkTrackingView from, ChunkTrackingView to,
            Consumer<ChunkPos> enter, Consumer<ChunkPos> leave, CallbackInfo ci) {
        if (from instanceof CameraChunkTrackingView || to instanceof CameraChunkTrackingView) {
            CameraChunkTrackingView.difference(from, to, enter, leave);
            ci.cancel();
        }
    }
}
