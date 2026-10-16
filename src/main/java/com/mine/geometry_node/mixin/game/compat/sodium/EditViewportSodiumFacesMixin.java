package com.mine.geometry_node.mixin.game.compat.sodium;

import com.mine.geometry_node.client.runtime.game.EditViewportShaderScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium's point-camera face mask is separate from terrain occlusion culling.
 * A parallel ray can see faces on either side of the orbit eye. Keep all actual
 * mesh slices here; ordinary GPU winding-based backface culling still applies.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class EditViewportSodiumFacesMixin {
    @Inject(method = "getVisibleFaces", at = @At("HEAD"), cancellable = true, remap = false)
    private static void geometryNode$parallelFaces(CallbackInfoReturnable<Integer> cir) {
        if (EditViewportShaderScope.isActive()) cir.setReturnValue(-1);
    }
}
