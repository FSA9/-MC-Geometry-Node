package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.ModelIrisAlphaTest;
import com.mine.geometry_node.client.model.render.ModelNativeLight;
import com.mine.geometry_node.client.model.render.ModelIrisShadowUv;
import com.mine.geometry_node.client.model.render.ModelIrisHeldLight;
import com.mine.geometry_node.client.model.render.ModelIrisBlendOverride;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compose with Iris's tessellation redirect and the resident UV2 hook at the actual indexed draw. */
@Mixin(GlStateManager.class)
public abstract class ModelIrisAlphaDrawMixin {
    @WrapMethod(method = "_drawElements")
    private static void geometryNode$scopedAlpha(int mode, int count, int type, long offset, Operation<Void> original) {
        if (!ModelIrisAlphaTest.isActive() && !ModelIrisBlendOverride.isActive() && !ModelIrisShadowUv.isActive() && !ModelNativeLight.isActive()) {
            original.call(mode, count, type, offset);
            return;
        }
        ModelIrisShadowUv.draw(() -> ModelIrisAlphaTest.draw(() -> ModelIrisBlendOverride.draw(() -> ModelNativeLight.draw(() -> original.call(mode, count, type, offset)))));
    }

    @ModifyVariable(method = "glShaderSource", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String geometryNode$nativeReceiverSource(String source) {
        return ModelNativeLight.patch(source);
    }

    @Inject(method = "glDeleteProgram", at = @At("HEAD"))
    private static void geometryNode$forgetAlpha(int program, CallbackInfo callback) {
        ModelNativeLight.deleted(program);
        ModelIrisAlphaTest.deleted(program);
        ModelIrisShadowUv.deleted(program);
        ModelIrisHeldLight.deleted(program);
    }
}
