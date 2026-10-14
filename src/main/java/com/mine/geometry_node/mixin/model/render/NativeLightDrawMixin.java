package com.mine.geometry_node.mixin.model.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.model.render.bvh.ModelResidentLight;
import com.mine.geometry_node.client.runtime.light.NativeTerrainLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lighting hooks for indexed, base-vertex and instanced draws after the VAO is bound. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class NativeLightDrawMixin {
    @Redirect(method = "drawFromBuffers", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"))
    private void geometryNode$drawResidentLight(int mode, int count, int type, long offset) {
        ModelResidentLight.drawElements(mode, count, type, offset);
    }

    @WrapOperation(method = "drawFromBuffers", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glDrawElementsBaseVertex(IIIJI)V"))
    private void geometryNode$terrainBaseVertex(int mode, int count, int type, long indices,
                                               int baseVertex, Operation<Void> original) {
        NativeTerrainLights.draw(() -> original.call(mode, count, type, indices, baseVertex));
    }

    @WrapOperation(method = "drawFromBuffers", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL31;glDrawElementsInstanced(IIIJI)V"))
    private void geometryNode$terrainInstanced(int mode, int count, int type, long indices,
                                              int instances, Operation<Void> original) {
        NativeTerrainLights.draw(() -> original.call(mode, count, type, indices, instances));
    }

    @WrapOperation(method = "drawFromBuffers", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL32;glDrawElementsInstancedBaseVertex(IIIJII)V"))
    private void geometryNode$terrainInstancedBaseVertex(int mode, int count, int type, long indices,
                                                        int instances, int baseVertex, Operation<Void> original) {
        NativeTerrainLights.draw(() -> original.call(mode, count, type, indices, instances, baseVertex));
    }
}
