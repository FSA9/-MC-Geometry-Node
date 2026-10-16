package com.mine.geometry_node.mixin.game.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.SharedGameFrame;
import net.minecraft.client.gui.render.GuiRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.function.Consumer;

/** Preserve the renderer's per-frame cache lifetime across HUD/editor submissions. */
@Mixin(GuiRenderer.class)
public abstract class GameViewGuiRendererMixin {
    @Inject(method = "clearUnusedOversizedItemRenderers", at = @At("HEAD"), cancellable = true)
    private void geometryNode$deferOversizedCleanup(CallbackInfo ci) {
        // blitTexture marks an oversized renderer used; only this cleanup resets
        // that mark. Keeping it until the editor pass combines both submissions.
        if (SharedGameFrame.deferGuiCacheCleanup((GuiRenderer) (Object) this)) ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V"))
    private void geometryNode$deferPictureInPictureCleanup(
            Collection<PictureInPictureRendererPool<?>> pools,
            Consumer<? super PictureInPictureRendererPool<?>> cleanup,
            Operation<Void> original) {
        // NeoForge swaps last-frame/this-frame maps in clearUnusedRenderers.
        // Defer that swap so the editor can still reuse last frame's previews.
        if (!SharedGameFrame.deferGuiCacheCleanup((GuiRenderer) (Object) this)) {
            original.call(pools, cleanup);
        }
    }
}
