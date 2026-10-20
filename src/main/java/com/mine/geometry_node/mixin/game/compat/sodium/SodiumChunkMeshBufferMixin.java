package com.mine.geometry_node.mixin.game.compat.sodium;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.foreign.MemorySegment;

/** Keep Sodium's externally allocated buffer bounded without restricted FFM access. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder", remap = false)
public abstract class SodiumChunkMeshBufferMixin {
    @Redirect(method = "reallocate(I)V", at = @At(value = "INVOKE", target =
            "Ljava/lang/foreign/MemorySegment;reinterpret(J)Ljava/lang/foreign/MemorySegment;"), remap = false)
    private MemorySegment geometryNode$boundedBuffer(MemorySegment address, long bytes) {
        // Sodium allocates an int-sized buffer through LWJGL and still owns its
        // realloc/free lifecycle. This direct buffer is only a non-owning view.
        return MemorySegment.ofBuffer(MemoryUtil.memByteBuffer(address.address(), Math.toIntExact(bytes)));
    }
}
