package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.ModelIrisAlphaTest;
import com.mine.geometry_node.client.model.render.ModelIrisShadowUv;
import com.mine.geometry_node.client.model.render.ModelIrisBlendOverride;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compose with Iris's tessellation redirect and the resident UV2 hook at the actual indexed draw. */
@Mixin(GlStateManager.class)
public abstract class ModelIrisAlphaDrawMixin {
    @WrapMethod(method = "_drawElements")
    private static void geometryNode$scopedAlpha(int mode, int count, int type, long offset, Operation<Void> original) {
        if (!ModelIrisAlphaTest.isActive() && !ModelIrisBlendOverride.isActive() && !ModelIrisShadowUv.isActive()) {
            original.call(mode, count, type, offset);
            return;
        }
        ModelIrisShadowUv.draw(() -> ModelIrisAlphaTest.draw(() -> ModelIrisBlendOverride.draw(() -> original.call(mode, count, type, offset))));
    }

    @Inject(method = "glDeleteProgram", at = @At("HEAD"))
    private static void geometryNode$forgetAlpha(int program, CallbackInfo callback) {
        ModelIrisAlphaTest.deleted(program);
        ModelIrisShadowUv.deleted(program);
    }
}
