package com.mine.geometry_node.mixin.model.iris;

import com.mine.geometry_node.client.runtime.light.NodeLightIrisPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.shader.ProgramCreator", remap = false)
public abstract class IrisLightProgramCacheMixin {
    @ModifyArg(method = "create", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glLinkProgram(I)V"), index = 0, remap = false)
    private static int geometryNode$prepareBinary(int program) {
        return NodeLightIrisPrograms.prepareLink(program);
    }
}
