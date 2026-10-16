package com.mine.geometry_node.mixin.game.input;

import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import icyllis.modernui.mc.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ModernUI observes global NeoForge mouse events even when vanilla has no input Screen. */
@Mixin(value = UIManager.class, remap = false)
public abstract class GameViewportModernUiInputMixin {
    @Inject(method = {"onPostMouseInput", "onHoverMove", "onScroll", "onKeyPress", "onKeyRelease"},
            at = @At("HEAD"), cancellable = true)
    private void geometryNode$muteInactiveEditorInput(CallbackInfo ci) {
        if (GameViewportInput.suppressModernUiInput()) ci.cancel();
    }
}
