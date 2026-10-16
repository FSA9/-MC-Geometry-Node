package com.mine.geometry_node.mixin.model.iris;

import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers both composite ProgramBuilder and ShaderCreator/ExtendedShader sampler layouts. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.gl.program.ProgramSamplers$Builder",remap=false)
public abstract class IrisLightSamplerBuilderMixin {
    @Inject(method="build",at=@At("HEAD"),remap=false)
    private void geometryNode$registerLightSamplers(CallbackInfoReturnable<Object> callback) {
        ModelIrisHeldLight.registerProgram(this);
        com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.registerProgram(this);
    }
}
