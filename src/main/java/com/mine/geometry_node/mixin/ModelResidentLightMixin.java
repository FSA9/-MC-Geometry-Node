package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.bvh.ModelResidentLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Version-bound MC 26.1 hook: the VAO is already bound at this exact draw site. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class ModelResidentLightMixin {
    @Redirect(method = "drawFromBuffers", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"))
    private void geometryNode$drawResidentLight(int mode, int count, int type, long offset) {
        ModelResidentLight.drawElements(mode, count, type, offset);
    }
}
