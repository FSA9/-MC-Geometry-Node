package com.mine.geometry_node.mixin.game.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.TextureFilteringMethod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Change only the world target, after real resize and before GUI; never mutate Window/GUI scale. */
@Mixin(GameRenderer.class)
public abstract class EditViewportTargetMixin {
    @Shadow @Final private GameRenderState gameRenderState;

    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private void geometryNode$beginWorldTarget(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        EditViewportRuntime.beginWorldTarget();
    }

    @Inject(method = "resize", at = @At("RETURN"))
    private void geometryNode$resizePlayerAttachments(int width, int height, CallbackInfo ci) {
        var context = EditViewportRuntime.renderContext();
        if (context != null) context.resizePlayer(width, height);
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"))
    private void geometryNode$worldPixelUniform(Args args) {
        args.set(0, EditViewportRuntime.worldWidth(args.get(0)));
        args.set(1, EditViewportRuntime.worldHeight(args.get(1)));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void geometryNode$restoreUiTarget(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        EditViewportRuntime.endWorldTarget();
        if (!EditViewportRuntime.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        var options = gameRenderState.optionsRenderState;
        ((GameRenderer) (Object) this).getGlobalSettingsUniform().update(
                gameRenderState.windowRenderState.width, gameRenderState.windowRenderState.height,
                options.glintStrength, mc.level == null ? 0L : mc.level.getGameTime(), delta,
                options.menuBackgroundBlurriness, gameRenderState.levelRenderState.cameraRenderState.pos,
                options.textureFiltering == TextureFilteringMethod.RGSS);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void geometryNode$ageWorldResourcesOnWorldFrames(CrossFrameResourcePool pool, Operation<Void> original) {
        if (!EditViewportRuntime.isBudgetIdleFrame()) original.call(pool);
    }

    @WrapMethod(method = "render")
    private void geometryNode$restoreAfterFailure(DeltaTracker delta, boolean advance, Operation<Void> original) {
        try { original.call(delta, advance); }
        finally { EditViewportRuntime.endWorldTarget(); }
    }

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("HEAD"), cancellable = true)
    private void geometryNode$keepPlayerWorldIcon(CallbackInfo ci) {
        if (EditViewportRuntime.isRendering()) ci.cancel();
    }
}
