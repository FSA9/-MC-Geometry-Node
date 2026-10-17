package com.mine.geometry_node.mixin.game.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

/** The shared submission boundary also covers Iris shadow passes and all native entity renderers. */
@Mixin(EntityRenderDispatcher.class)
public abstract class ViewportEntityVisibilityMixin {
    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V")
    private void geometryNode$hideEditorEntities(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack pose, SubmitNodeCollector collector, Operation<Void> original) {
        // Keep Iris' paired HEAD/RETURN entity-context hooks together when suppressing a submission.
        if (!ViewportVisualization.inEditorPass()
                || ViewportVisualization.current().visible(ViewportVisualization.Layer.ENTITY))
            original.call(state, camera, x, y, z, pose, collector);
    }
}
