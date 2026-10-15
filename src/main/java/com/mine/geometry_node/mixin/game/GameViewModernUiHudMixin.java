package com.mine.geometry_node.mixin.game;

import com.mine.geometry_node.client.runtime.game.SharedGameFrame;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ModernUI normally hides the crosshair with any of its screens open. The isolated
 * game HUD needs the vanilla crosshair, while other mods retain their cancellation.
 */
@Mixin(targets = "icyllis.modernui.mc.neoforge.UIManagerForge", remap = false)
public abstract class GameViewModernUiHudMixin {
    @Inject(method = "onRenderGameOverlayLayer", at = @At("HEAD"), cancellable = true)
    private void geometryNode$keepGameCrosshair(RenderGuiLayerEvent.Pre event, CallbackInfo ci) {
        if (SharedGameFrame.capturesGameHud() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) ci.cancel();
    }
}
