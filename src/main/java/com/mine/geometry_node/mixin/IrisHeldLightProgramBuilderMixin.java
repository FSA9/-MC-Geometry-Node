package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.bvh.ModelPackLightAdapters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Composite shaders use ProgramBuilder rather than the gbuffers ShaderCreator. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramBuilder", remap = false)
public abstract class IrisHeldLightProgramBuilderMixin {
    @ModifyVariable(method = "begin", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false)
    private static String geometryNode$occludeKnownHeldLight(String fragment) {
        return ModelPackLightAdapters.patch(fragment);
    }
    @Inject(method = "build", at = @At("HEAD"), remap = false)
    private void geometryNode$registerHeldLight(CallbackInfoReturnable<Object> callback) {
        ModelIrisHeldLight.registerProgram(this);
    }
}
