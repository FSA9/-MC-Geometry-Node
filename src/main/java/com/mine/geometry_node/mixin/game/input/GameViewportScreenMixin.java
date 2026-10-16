package com.mine.geometry_node.mixin.game.input;

import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent a container's second close operation from closing its restored workspace. */
@Mixin(Screen.class)
public abstract class GameViewportScreenMixin {
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void geometryNode$keepRestoredEditor(CallbackInfo ci) {
        if (GameViewportInput.ignoreStaleScreenClose((Screen) (Object) this)) ci.cancel();
    }
}
