package com.mine.geometry_node.mixin.model.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightNativePrograms;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class NativeLightShaderBuildMixin {
    @WrapMethod(method = "compileProgram")
    private GlProgram geometryNode$timePipeline(RenderPipeline pipeline, ShaderSource source, Operation<GlProgram> original) {
        return NodeLightNativePrograms.pipeline(pipeline.getLocation().toString(), () -> original.call(pipeline, source));
    }

    @WrapOperation(method = "compileProgram", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlDevice;getOrCompileShader(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;Lnet/minecraft/client/renderer/ShaderDefines;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/opengl/GlShaderModule;"))
    private GlShaderModule geometryNode$timeStage(@Coerce Object device, Identifier id, ShaderType type, ShaderDefines defines,
                                                 ShaderSource source, Operation<GlShaderModule> original) {
        long start = System.nanoTime();
        try { return original.call(device, id, type, defines, source); }
        finally { NodeLightNativePrograms.shader(type, System.nanoTime() - start); }
    }
}
