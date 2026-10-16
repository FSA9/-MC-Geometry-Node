package com.mine.geometry_node.mixin.game.render;

import com.mine.geometry_node.client.runtime.game.render.SharedGameFrame;
import com.mine.geometry_node.client.runtime.game.diagnostic.GameViewTiming;
import com.mine.geometry_node.client.runtime.game.render.GameFrameCleanup;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameViewRendererMixin {
    @Shadow @Final private GameRenderState gameRenderState;
    @Unique private long geometryNode$extractStart;
    @Unique private long geometryNode$worldStart;

    @Inject(method = "extract", at = @At("HEAD"))
    private void geometryNode$planGameFrame(DeltaTracker delta, boolean advanceGameTime, CallbackInfo ci) {
        geometryNode$extractStart = GameViewTiming.start();
        SharedGameFrame.beginFrame();
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void geometryNode$timeExtraction(DeltaTracker delta, boolean advanceGameTime, CallbackInfo ci) {
        GameViewTiming.finish("extraction including ModernUI", geometryNode$extractStart);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void geometryNode$startWorldTiming(DeltaTracker delta, CallbackInfo ci) {
        geometryNode$worldStart = GameViewTiming.start();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void geometryNode$finishWorldTiming(DeltaTracker delta, CallbackInfo ci) {
        GameViewTiming.finish("world rendering", geometryNode$worldStart);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean geometryNode$skipOccludedWorld(boolean advanceGameTime) {
        boolean renderWorld = SharedGameFrame.shouldRenderWorld(advanceGameTime);
        boolean discarded = advanceGameTime && !renderWorld;
        GameViewTiming.frameDecision(discarded, gameRenderState.levelRenderState);
        if (discarded) {
            // Vanilla resets extracted lists at the END of world rendering, not
            // at extraction. A skipped draw must still retire that frame, including
            // particles (which LevelRenderState.reset deliberately does not clear).
            var levelRenderer = (GameViewLevelRendererAccessor) Minecraft.getInstance().levelRenderer;
            GameFrameCleanup.discard(gameRenderState.levelRenderState, levelRenderer.geometryNode$getCollectedGizmos());
        }
        return renderWorld;
    }

    @Inject(method = "extractGui", at = @At("HEAD"))
    private void geometryNode$beginGameFrame(DeltaTracker delta, boolean renderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        SharedGameFrame.beginExtraction(renderLevel,
                gameRenderState.windowRenderState.width, gameRenderState.windowRenderState.height);
    }

    @ModifyArg(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"), index = 0)
    private GuiGraphicsExtractor geometryNode$isolateHud(GuiGraphicsExtractor original) {
        return SharedGameFrame.hudGraphics(original);
    }

    @ModifyArg(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"), index = 0)
    private GuiGraphicsExtractor geometryNode$isolateSavingIndicator(GuiGraphicsExtractor original) {
        return SharedGameFrame.hudGraphics(original);
    }

    @ModifyArg(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractDebugOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"), index = 0)
    private GuiGraphicsExtractor geometryNode$isolateDebugOverlay(GuiGraphicsExtractor original) {
        return SharedGameFrame.hudGraphics(original);
    }

    @ModifyArg(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"), index = 0)
    private GuiGraphicsExtractor geometryNode$isolateToasts(GuiGraphicsExtractor original) {
        return SharedGameFrame.hudGraphics(original);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void geometryNode$renderHudThenCapture(GuiRenderer renderer, GpuBufferSlice fog) {
        SharedGameFrame.renderGui(renderer, fog);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void geometryNode$closeGameFrame(CallbackInfo ci) {
        SharedGameFrame.close();
    }
}
