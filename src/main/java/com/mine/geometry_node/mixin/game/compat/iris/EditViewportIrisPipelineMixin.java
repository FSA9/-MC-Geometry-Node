package com.mine.geometry_node.mixin.game.compat.iris;

import com.mine.geometry_node.client.runtime.game.render.EditViewportShaderScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Orthographic views use Iris' original basic pipeline, without destroying the
 * pack, changing its configuration, or rebuilding meshes on projection changes.
 * Object signatures keep Iris optional; no Iris classes are linked without it.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class EditViewportIrisPipelineMixin {
    @Unique private Object geometryNode$basicPipeline;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void geometryNode$retainInitialPipeline(CallbackInfo ci) throws ReflectiveOperationException {
        // PipelineManager creates this before loading any pack. Constructing a
        // new VanillaRenderingPipeline later would reset WorldRenderingSettings
        // (vertex format, material IDs, AO, etc.) underneath live chunk workers.
        Object initial = getClass().getMethod("getPipelineNullable").invoke(this);
        if (!initial.getClass().getName().equals("net.irisshaders.iris.pipeline.VanillaRenderingPipeline")) {
            throw new IllegalStateException("Iris no longer supplies an initial basic rendering pipeline");
        }
        geometryNode$basicPipeline = initial;
    }

    @Inject(method = {"preparePipeline", "getPipelineNullable"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void geometryNode$orthographicPipeline(CallbackInfoReturnable<Object> cir) {
        if (geometryNode$basicPipeline != null && EditViewportShaderScope.isActive()) {
            cir.setReturnValue(geometryNode$basicPipeline);
        }
    }

    @Inject(method = "getPipeline", at = @At("HEAD"), cancellable = true, remap = false)
    private void geometryNode$orthographicPipelineOptional(CallbackInfoReturnable<Optional<?>> cir) {
        if (geometryNode$basicPipeline != null && EditViewportShaderScope.isActive()) {
            cir.setReturnValue(Optional.of(geometryNode$basicPipeline));
        }
    }
}
