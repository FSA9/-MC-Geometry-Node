package com.mine.geometry_node.mixin.model.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.runtime.model.render.bvh.ModelBvhLightResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs lighting passes, tracks GPU timings and releases resources with the composite renderer. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer", remap = false)
public abstract class IrisLightCompositeMixin {
    @Unique private String geometryNode$gpuPassName;

    @Inject(method = "renderAll", at = @At("HEAD"), remap = false, require = 0)
    private void geometryNode$collectReadyQueries(CallbackInfo callback) {
        geometryNode$gpuPassName = null;
        ModelShaderGpuTimings.poll();
    }

    // The first group names the whole renderer; the second names the actual pack pass.
    @WrapOperation(method = "renderAll", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V"), remap = false, require = 0)
    private void geometryNode$capturePassName(int id, String name, Operation<Void> original) {
        original.call(id, name);
        geometryNode$gpuPassName = name;
    }

    @WrapOperation(method = "renderAll", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"), remap = false, require = 0)
    private void geometryNode$drawLighting(int mode, int count, int type, long offset, Operation<Void> original) {
        com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.draw(() ->
                ModelBvhLightResolution.draw(geometryNode$gpuPassName, () -> original.call(mode, count, type, offset)));
    }

    @Inject(method = "destroy", at = @At("TAIL"), remap = false, require = 0)
    private void geometryNode$releaseLighting(CallbackInfo callback) {
        geometryNode$gpuPassName = null;
        ModelBvhLightResolution.clear();
        com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.clear();
        ModelShaderGpuTimings.clear();
    }
}
