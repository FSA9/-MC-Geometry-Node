package com.mine.geometry_node.mixin.model.iris;

import com.mine.geometry_node.client.runtime.model.compat.IrisSamplerFallbacks;
import com.mojang.blaze3d.systems.RenderPassBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Fill Iris's optional samplers before host validation; leave all validation enabled. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class IrisSamplerValidationMixin {
    @Inject(method = "trySetup", at = @At("HEAD"))
    private void geometryNode$bindIrisFallbacks(@Coerce Object pass, Collection<String> dynamicUniforms,
                                               CallbackInfoReturnable<Boolean> callback) {
        IrisSamplerRenderPassAccessor state = (IrisSamplerRenderPassAccessor) pass;
        var pipeline = state.geometryNode$getPipeline();
        if (pipeline != null) IrisSamplerFallbacks.bindMissing(pipeline.program(), state.geometryNode$getSamplers(),
                (RenderPassBackend) pass);
    }
}
