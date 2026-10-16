package com.mine.geometry_node.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.EditChunkCache;
import com.mine.geometry_node.client.runtime.game.EditChunkCacheAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Only storage lookup changes. Vanilla packet decoding, light/empty-section and
 * chunk callbacks remain intact, including Sodium's injections into those calls. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class EditClientChunkStorageMixin {
    @Shadow @Final private ClientChunkCache this$0;
    @Shadow @Final private AtomicReferenceArray<LevelChunk> chunks;
    @Shadow @Final private int chunkRadius;
    @Shadow @Final private int viewRange;
    @Shadow private volatile int viewCenterX;
    @Shadow private volatile int viewCenterZ;

    @Unique private EditChunkCache<LevelChunk> geometryNode$extra() {
        return ((EditChunkCacheAccess) this$0).geometryNode$cameraChunks();
    }
    @Unique private int geometryNode$nativeIndex(int x, int z) {
        return Math.floorMod(z, viewRange) * viewRange + Math.floorMod(x, viewRange);
    }
    @Unique private boolean geometryNode$nativeContains(int x, int z) {
        LevelChunk chunk = chunks.get(geometryNode$nativeIndex(x, z));
        return chunk != null && chunk.getPos().x() == x && chunk.getPos().z() == z;
    }
    @Inject(method = "inRange", at = @At("RETURN"), cancellable = true)
    private void geometryNode$cameraRange(int x, int z, CallbackInfoReturnable<Boolean> cir) {
        var extra = geometryNode$extra();
        if (!cir.getReturnValueZ() && extra.enabled()
                && (extra.accepts(x, z) || extra.has(x, z) || geometryNode$nativeContains(x, z))) cir.setReturnValue(true);
    }
    @Inject(method = "getIndex", at = @At("RETURN"), cancellable = true)
    private void geometryNode$cameraIndex(int x, int z, CallbackInfoReturnable<Integer> cir) {
        var extra = geometryNode$extra();
        if (!extra.enabled()) return;
        boolean nativeRange = Math.abs((long) x - viewCenterX) <= chunkRadius && Math.abs((long) z - viewCenterZ) <= chunkRadius;
        cir.setReturnValue(extra.index(x, z, cir.getReturnValueI(), nativeRange, geometryNode$nativeContains(x, z)));
    }
    @WrapOperation(method = {"getChunk", "dumpChunks"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;get(I)Ljava/lang/Object;"))
    private Object geometryNode$read(AtomicReferenceArray<?> array, int index, Operation<Object> original) {
        return index < 0 ? geometryNode$extra().get(index) : original.call(array, index);
    }
    @WrapOperation(method = "replace", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;getAndSet(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object geometryNode$replace(AtomicReferenceArray<?> array, int index, Object value, Operation<Object> original) {
        var extra = geometryNode$extra();
        if (index < 0) return extra.replace(index, (LevelChunk) value);
        LevelChunk previous = (LevelChunk) original.call(array, index, value);
        if (previous != null && extra.accepts(previous.getPos().x(), previous.getPos().z())) {
            // A player ring collision must not unload a chunk still watched by
            // the editor. Its native count and empty-section entries remain valid.
            extra.retain(previous.getPos().x(), previous.getPos().z(), previous);
            return null;
        }
        return previous;
    }
    @WrapOperation(method = "drop", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;compareAndSet(ILjava/lang/Object;Ljava/lang/Object;)Z"))
    private boolean geometryNode$drop(AtomicReferenceArray<?> array, int index, Object expected, Object value, Operation<Boolean> original) {
        return index < 0 ? geometryNode$extra().drop(index, (LevelChunk) expected) : original.call(array, index, expected, value);
    }
}
