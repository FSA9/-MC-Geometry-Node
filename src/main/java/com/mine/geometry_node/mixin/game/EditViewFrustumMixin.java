package com.mine.geometry_node.mixin.game;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.EditViewportRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The vanilla camera-cube expansion assumes that moving a frustum backwards
 * widens its cross-section. An orthographic frustum cannot satisfy that assumption.
 */
@Mixin(LevelRenderer.class)
public abstract class EditViewFrustumMixin {
    @Shadow private double prevCamRotX;
    @Unique private final Matrix4f geometryNode$previousCullMatrix = new Matrix4f();
    @Unique private final Matrix4f geometryNode$currentCullMatrix = new Matrix4f();
    @Unique private boolean geometryNode$wasEditing;

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean geometryNode$hidePlayerAroundEditorEye(boolean detached, Camera camera, Frustum frustum,
                                                           DeltaTracker delta, LevelRenderState output) {
        var player = Minecraft.getInstance().player;
        if (EditViewportRuntime.isRendering() && player != null && camera.entity() == player
                && player.getBoundingBox().inflate(0.3).contains(camera.position())) {
            // Initial alignment can place the editor eye inside the player's head
            // or held-item model. Hide only that camera entity until we move away.
            // The camera itself stays detached: Iris must still suppress its hand
            // pass, and other camera consumers retain the independent-view policy.
            return false;
        }
        return detached;
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"))
    private void geometryNode$updateEditorCulling(LevelRenderer renderer, Camera camera, Frustum frustum,
                                                 boolean spectator, Operation<Void> original) {
        boolean editing = EditViewportRuntime.isRendering();
        camera.getViewRotationProjectionMatrix(geometryNode$currentCullMatrix);
        if ((editing || geometryNode$wasEditing)
                && (editing != geometryNode$wasEditing
                    || !geometryNode$currentCullMatrix.equals(geometryNode$previousCullMatrix))) {
            // Vanilla only checks coarse yaw/pitch changes before applying a new
            // frustum. Force that existing branch when projection/zoom changes or
            // we return to the player. Sodium replaces cullTerrain and already
            // compares its culling matrix, so this unused vanilla cache is harmless.
            prevCamRotX = Double.NaN;
        }
        geometryNode$previousCullMatrix.set(geometryNode$currentCullMatrix);
        geometryNode$wasEditing = editing;

        Minecraft minecraft = Minecraft.getInstance();
        boolean smartCull = minecraft.smartCull;
        try {
            // Point-camera occlusion cannot describe parallel orthographic rays.
            // Both vanilla and Sodium read this flag while creating their cull
            // task; never change the user's lasting setting or the world itself.
            if (EditViewportRuntime.isOrthographic()) minecraft.smartCull = false;
            original.call(renderer, camera, frustum, spectator);
        } finally {
            minecraft.smartCull = smartCull;
        }
    }

    @Inject(method = "offsetFrustum", at = @At("HEAD"), cancellable = true)
    private static void geometryNode$keepOrthographicFrustum(Frustum frustum,
                                                            CallbackInfoReturnable<Frustum> cir) {
        if (EditViewportRuntime.isOrthographic()) {
            // In particular, a narrow orthographic view can never fully contain
            // the eight-block camera cube: vanilla's unbounded loop would hang.
            // Copy without offsetting so callers still receive their own frustum.
            cir.setReturnValue(new Frustum(frustum));
        }
    }
}
