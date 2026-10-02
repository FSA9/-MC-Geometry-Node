package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.ModelIrisHeldLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upload only after Iris has activated and populated the composite program. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class IrisHeldLightProgramMixin {
    @Shadow public abstract int getProgramId();
    @Inject(method = "use", at = @At("TAIL"), remap = false)
    private void geometryNode$uploadHeldLight(CallbackInfo callback) {
        ModelIrisHeldLight.uploadUniforms(getProgramId());
    }
}
