package com.mine.geometry_node.mixin;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashMap;

/** Access to host state, with no compile-time or loading dependency on Iris. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
public interface IrisSamplerRenderPassAccessor {
    @Accessor("pipeline") GlRenderPipeline geometryNode$getPipeline();
    @Accessor("samplers") HashMap<String, ?> geometryNode$getSamplers();
}
