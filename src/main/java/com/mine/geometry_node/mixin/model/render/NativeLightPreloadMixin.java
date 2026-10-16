package com.mine.geometry_node.mixin.model.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mine.geometry_node.client.runtime.light.shader.NativeLightPreloads;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the preload copy changes; registration and on-demand compilation remain intact. */
@Mixin(ShaderManager.class)
public abstract class NativeLightPreloadMixin {
    @ModifyExpressionValue(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderPipelines;getStaticPipelines()Ljava/util/List;"))
    private List<RenderPipeline> geometryNode$deferUnusedTerrain(List<RenderPipeline> pipelines) {
        return NativeLightPreloads.filter(pipelines);
    }
}
