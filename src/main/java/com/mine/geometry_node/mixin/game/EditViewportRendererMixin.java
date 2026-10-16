package com.mine.geometry_node.mixin.game;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.EditViewportShaderScope;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each vanilla stage gets the same frozen editor camera; even exceptions restore the player camera. */
@Mixin(GameRenderer.class)
public abstract class EditViewportRendererMixin {
    @Shadow @Final @Mutable private Camera mainCamera;
    @Shadow @Final private GameRenderState gameRenderState;

    @WrapMethod(method = "update")
    private void geometryNode$updateEditorCamera(DeltaTracker delta, boolean advance, Operation<Void> original) {
        Camera player = mainCamera;
        // Initialize from a fresh player pose, not the previous editor pose.
        if (EditViewportRuntime.hasVisibleViews()) player.update(delta);
        EditViewportRuntime.beginFrame(player);
        try (var ignored = EditViewportShaderScope.enter(EditViewportRuntime.isOrthographic())) {
            if (EditViewportRuntime.isRendering()) mainCamera = EditViewportRuntime.camera();
            original.call(delta, advance);
        } finally { mainCamera = player; }
    }

    @WrapMethod(method = "extract")
    private void geometryNode$extractEditorCamera(DeltaTracker delta, boolean advance, Operation<Void> original) {
        Camera player = mainCamera;
        try (var ignored = EditViewportShaderScope.enter(EditViewportRuntime.isOrthographic())) {
            if (EditViewportRuntime.isRendering()) mainCamera = EditViewportRuntime.camera();
            original.call(delta, advance);
        } finally { mainCamera = player; }
    }

    @WrapMethod(method = "render")
    private void geometryNode$renderEditorCamera(DeltaTracker delta, boolean advance, Operation<Void> original) {
        Camera player = mainCamera;
        try (var ignored = EditViewportShaderScope.enter(EditViewportRuntime.isOrthographic())) {
            if (EditViewportRuntime.isRendering()) mainCamera = EditViewportRuntime.camera();
            original.call(delta, advance);
        } finally { mainCamera = player; }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void geometryNode$tickEditEnvironment(CallbackInfo ci) { EditViewportRuntime.tickCamera(); }

    @Inject(method = "extractOptions", at = @At("TAIL"))
    private void geometryNode$editorOptions(CallbackInfo ci) {
        if (!EditViewportRuntime.isRendering()) return;
        var options = gameRenderState.optionsRenderState;
        options.bobView = false;
        options.screenEffectScale = 0;
        options.hideGui = true;
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V"), index = 1)
    private ProjectionType geometryNode$editorProjection(ProjectionType original) {
        return EditViewportRuntime.isOrthographic() ? ProjectionType.ORTHOGRAPHIC : original;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void geometryNode$noEditorHurt(CameraRenderState state, PoseStack stack, CallbackInfo ci) {
        if (EditViewportRuntime.isRendering()) ci.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void geometryNode$noEditorHand(CameraRenderState state, float partialTick, Matrix4fc matrix, CallbackInfo ci) {
        if (EditViewportRuntime.isRendering()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void geometryNode$noPlayerOutline(CallbackInfoReturnable<Boolean> cir) {
        if (EditViewportRuntime.isRendering()) cir.setReturnValue(false);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"))
    private void geometryNode$noEditorScreenEffects(ScreenEffectRenderer renderer, boolean firstPerson,
            boolean sleeping, float partialTick, SubmitNodeCollector storage, boolean hideGui, Operation<Void> original) {
        if (!EditViewportRuntime.isRendering()) original.call(renderer, firstPerson, sleeping, partialTick, storage, hideGui);
    }
}
