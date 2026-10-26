package com.mine.geometry_node.mixin.model.iris;
import com.mine.geometry_node.client.runtime.model.compat.ModelIrisPbrBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
/** Runs after Iris's once-per-pass setup so same-program material changes also update PBR samplers. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder", priority=900)
public abstract class ModelPbrRenderPassMixin {
    @Inject(method="trySetup",at=@At("RETURN"))
    private void geometryNode$pbrMaterial(@Coerce Object pass,Collection<String> uniforms,CallbackInfoReturnable<Boolean> callback) {
        if(!Boolean.TRUE.equals(callback.getReturnValue()) || !ModelIrisPbrBindings.requiresDrawTracking())return;
        var state=(IrisSamplerRenderPassAccessor)pass;
        var pipeline=state.geometryNode$getPipeline();
        Object sampler=state.geometryNode$getSamplers().get("Sampler0");
        if(pipeline!=null)
            ModelIrisPbrBindings.beforeDraw(pipeline.program(),sampler instanceof ModelPbrSamplerViewAccessor view ? view.geometryNode$pbrView() : null);
    }
}
