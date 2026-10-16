package com.mine.geometry_node.mixin.model.render;

import com.mine.geometry_node.client.runtime.light.render.NodeLightEntityShadows;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Variant selection occurs in subclass submit; stop before the base class emits render events or layers. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityShadowCaptureMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void geometryNode$captureBody(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        if (NodeLightEntityShadows.captureSubmission((LivingEntityRenderer<?, ?, ?>) (Object) this, state, pose)) ci.cancel();
    }
}
