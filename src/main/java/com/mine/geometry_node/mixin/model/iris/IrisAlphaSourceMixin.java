package com.mine.geometry_node.mixin.model.iris;

import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelIrisAlphaTest;
import com.mine.geometry_node.client.runtime.model.render.shadow.ModelSundialShadowUv;
import com.mine.geometry_node.client.runtime.model.render.shadow.ModelIrisShadowUv;
import com.mine.geometry_node.client.runtime.model.render.pipeline.ModelSundialMaskCoverage;
import com.mine.geometry_node.client.runtime.model.render.shadow.ModelSundialShadowCoverage;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Observe final transformed shader source and opt in known solid-MASK handoff; optional Iris dependency. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderCreator", remap = false)
public abstract class IrisAlphaSourceMixin {
    /** The two stages must agree on the interpolated normal varying. Shadow programs stay separate. */
    @ModifyArgs(method = "create", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;link(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;Z)Lnet/irisshaders/iris/pipeline/programs/PartialShader;"), remap = false)
    private static void geometryNode$preserveModelNormals(Args args) {
        if (args.get(2) != null || args.get(3) != null || args.get(4) != null) return;
        var patched = com.mine.geometry_node.client.runtime.model.render.geometry.ModelPackSmoothNormals.patch(args.get(1), args.get(5));
        args.set(1, patched.vertex());
        args.set(5, patched.fragment());
    }

    /** Patch both stages atomically before link/compilation; unknown sources remain untouched. */
    @ModifyArgs(method = "createShadow", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/pipeline/programs/ShaderCreator;link(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;Z)Lnet/irisshaders/iris/pipeline/programs/PartialShader;"), remap = false)
    private static void geometryNode$normalizeKnownShadowMask(Args args) {
        // SEUS emits a second, block-only voxel primitive from its shadow geometry stage.
        if (args.get(3) == null && args.get(4) == null) {
            args.set(2, com.mine.geometry_node.client.runtime.model.render.shadow.ModelSeusShadowVoxel.patch(args.get(2)));
            var seusDepth = com.mine.geometry_node.client.runtime.model.render.shadow.ModelSeusShadowDepth.patch(
                    args.get(1), args.get(2), args.get(5));
            args.set(2, seusDepth.geometry());
            args.set(5, seusDepth.fragment());
        }
        // Geometry/tessellation may alter alpha/atlas semantics; only the observed pair is supported.
        if (args.get(2) != null || args.get(3) != null || args.get(4) != null) return;
        var patched = ModelSundialShadowCoverage.patch(args.get(1), args.get(5));
        var depth = com.mine.geometry_node.client.runtime.model.render.shadow.ModelSundialShadowDepth.patch(
                ModelSundialShadowUv.patch(patched.vertex()), patched.fragment());
        args.set(1, depth.vertex());
        args.set(2, depth.geometry());
        args.set(5, depth.fragment());
    }

    @ModifyVariable(method = "link", at = @At("HEAD"), argsOnly = true, ordinal = 5, remap = false)
    private static String geometryNode$normalizeKnownSolidMask(String fragment) {
        return com.mine.geometry_node.client.runtime.model.render.bvh.ModelPackLightAdapters.patch(com.mine.geometry_node.client.runtime.model.render.pipeline.ModelSundialMaterialSampling.patch(ModelSundialMaskCoverage.patch(fragment)));
    }

    @Redirect(method = "link", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/opengl/GlStateManager;glLinkProgram(I)V"), remap = false)
    private static void geometryNode$observeAlpha(int program, String name, String vertex, String geometry,
                                                String tessControl, String tessEval, String fragment,
                                                VertexFormat format, boolean fallback) {
        GlStateManager.glLinkProgram(program);
        ModelIrisAlphaTest.linked(program, name, fragment);
        com.mine.geometry_node.client.runtime.model.compat.ModelIrisPbrBindings.linked(program, fragment);
        ModelIrisShadowUv.linked(program, vertex, geometry);
    }
}
