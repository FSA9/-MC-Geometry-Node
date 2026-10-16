package com.mine.geometry_node.mixin.game.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.EditChunkCache;
import com.mine.geometry_node.client.runtime.game.EditChunkCacheAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(ClientChunkCache.class)
public abstract class EditClientChunkCacheMixin implements EditChunkCacheAccess {
    @Unique private final EditChunkCache<LevelChunk> geometryNode$extra = new EditChunkCache<>();
    @Override public EditChunkCache<LevelChunk> geometryNode$cameraChunks() { return geometryNode$extra; }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void geometryNode$initialRadius(ClientLevel level, int radius, CallbackInfo ci) { geometryNode$extra.playerRadius(radius); }
    @Inject(method = "updateViewCenter", at = @At("HEAD"))
    private void geometryNode$playerCenter(int x, int z, CallbackInfo ci) { geometryNode$extra.playerCenter(x, z); }
    @Inject(method = "updateViewRadius", at = @At("HEAD"), cancellable = true)
    private void geometryNode$playerRadius(int radius, CallbackInfo ci) {
        geometryNode$extra.playerRadius(radius);
        // Preserve overflow slots and native empty-section bookkeeping. The sparse
        // region accepts the new radius without reallocating or copying the ring.
        if (geometryNode$extra.enabled()) ci.cancel();
    }

    @WrapOperation(method = {"replaceWithPacketData", "replaceBiomes"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;get(I)Ljava/lang/Object;"))
    private Object geometryNode$readOverflow(AtomicReferenceArray<?> array, int index, Operation<Object> original) {
        return index < 0 ? geometryNode$extra.get(index) : original.call(array, index);
    }
}
