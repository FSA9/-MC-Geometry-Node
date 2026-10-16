package com.mine.geometry_node.mixin.model.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightNativePrograms;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlProgram.class)
public abstract class NativeLightProgramCacheMixin {
    @WrapMethod(method = "link")
    private static GlProgram geometryNode$cachedLink(GlShaderModule vertex, GlShaderModule fragment,
                                                     VertexFormat format, String name, Operation<GlProgram> original) {
        return NodeLightNativePrograms.link(vertex, fragment, format, name,
                () -> original.call(vertex, fragment, format, name));
    }

    @ModifyArg(method = "link", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glLinkProgram(I)V"), index = 0)
    private static int geometryNode$binaryHint(int program) {
        return NodeLightNativePrograms.prepareLink(program);
    }
}
