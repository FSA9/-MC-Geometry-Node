package com.mine.geometry_node.mixin.preview;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.ui.components.integration.nativepreview.DeferredEntityPreviewGuard;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Contain preview submission failures without catching feature, GPU, or unrelated GUI errors. */
@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityPreviewSubmissionMixin {
    @WrapMethod(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
    private void geometryNode$isolatePreviewNodes(GuiEntityRenderState state, PoseStack pose, Operation<Void> original) {
        DeferredEntityPreviewGuard.render(state.renderState(),
                Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage(),
                () -> original.call(state, pose));
    }

    @WrapOperation(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void geometryNode$containPreviewSubmission(EntityRenderDispatcher dispatcher,
            EntityRenderState state, CameraRenderState camera, double x, double y, double z,
            PoseStack pose, SubmitNodeCollector collector, Operation<Void> original) {
        DeferredEntityPreviewGuard.submit(state, collector,
                () -> original.call(dispatcher, state, camera, x, y, z, pose, collector));
    }

    @WrapOperation(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures()V"))
    private void geometryNode$skipFailedPreviewFeatures(FeatureRenderDispatcher dispatcher, Operation<Void> original) {
        DeferredEntityPreviewGuard.renderFeatures(dispatcher.getSubmitNodeStorage(), () -> original.call(dispatcher));
    }
}
