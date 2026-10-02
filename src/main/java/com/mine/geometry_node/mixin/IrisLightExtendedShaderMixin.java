package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.ModelIrisHeldLight;
import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ExtendedShader owns the G-buffer programs, separately from composite Program.use. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.programs.ExtendedShader",remap=false)
public abstract class IrisLightExtendedShaderMixin {
    @Inject(method="iris$setupState",at=@At("TAIL"),remap=false)
    private void geometryNode$uploadLocalLighting(CallbackInfo callback) {
        ModelIrisHeldLight.uploadUniforms(((GlProgram)(Object)this).getProgramId());
    }
}
