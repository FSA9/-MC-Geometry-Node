package com.mine.geometry_node.mixin.game.input;

import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge 26.1.2 GUI context; IN_GAME delegates to the inverse of GUI.isActive(). */
@Mixin(targets = "net.neoforged.neoforge.client.settings.KeyConflictContext$2", remap = false)
public abstract class GameViewportKeyConflictContextMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void geometryNode$capturedGameContext(CallbackInfoReturnable<Boolean> cir) {
        if (GameViewportInput.isGameInputActive()) cir.setReturnValue(false);
    }
}
