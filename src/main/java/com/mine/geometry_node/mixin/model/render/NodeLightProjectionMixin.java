package com.mine.geometry_node.mixin.model.render;

import com.mine.geometry_node.client.runtime.light.NativeVolumeLights;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Capture the world projection after hurt, view bobbing and portal distortion. */
@Mixin(GameRenderer.class)
public abstract class NodeLightProjectionMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f nodeLight$projection(Matrix4f projection) {
        NativeVolumeLights.setProjection(projection);
        return projection;
    }
}
