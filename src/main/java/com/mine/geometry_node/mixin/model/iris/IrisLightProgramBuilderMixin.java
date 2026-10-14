package com.mine.geometry_node.mixin.model.iris;

import com.mine.geometry_node.client.model.render.bvh.ModelPackLightAdapters;
import com.mine.geometry_node.client.runtime.light.NodeLightIrisPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Patches and caches composite lighting programs; samplers register in IrisLightSamplerBuilderMixin. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramBuilder", remap = false)
public abstract class IrisLightProgramBuilderMixin {
    @ModifyVariable(method = "begin", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false)
    private static String geometryNode$patchLighting(String fragment, String name, String vertex, String geometry,
                                                            String originalFragment, com.google.common.collect.ImmutableSet<Integer> reservedUnits) {
        NodeLightIrisPrograms.reset();
        com.mine.geometry_node.client.runtime.light.NodeLightShaderBuilds.begin(name);
        String result = com.mine.geometry_node.client.runtime.light.NodeLightShader.patch(ModelPackLightAdapters.patch(fragment), vertex);
        com.mine.geometry_node.client.runtime.light.NodeLightShaderBuilds.patched(result);
        return result;
    }
    @Inject(method = "begin", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramBuilder;buildShader(Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;Ljava/lang/String;)Lnet/irisshaders/iris/gl/shader/GlShader;",
            ordinal = 0), cancellable = true, remap = false)
    private static void geometryNode$loadProgram(String name, String vertex, String geometry, String fragment,
                                                com.google.common.collect.ImmutableSet<Integer> reservedUnits,
                                                CallbackInfoReturnable<Object> callback) {
        Object builder = NodeLightIrisPrograms.load(name, vertex, geometry, fragment, reservedUnits);
        if (builder != null) {
            com.mine.geometry_node.client.runtime.light.NodeLightShaderBuilds.finish();
            callback.setReturnValue(builder);
        }
    }
    @Inject(method = "begin", at = @At("RETURN"), remap = false)
    private static void geometryNode$recordBuild(CallbackInfoReturnable<Object> callback) {
        NodeLightIrisPrograms.save(callback.getReturnValue());
        com.mine.geometry_node.client.runtime.light.NodeLightShaderBuilds.finish();
    }
}
