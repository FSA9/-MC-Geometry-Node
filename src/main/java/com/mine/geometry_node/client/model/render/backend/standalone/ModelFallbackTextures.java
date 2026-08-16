package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.client.model.gpu.*;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuDevice;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.List;

/** Required bindings for absent textures in the fixed five-sampler contract. */
final class ModelFallbackTextures implements AutoCloseable {
    private ModelGpuTexture neutral;
    private ModelGpuTexture normal;

    GpuTextureView neutral() {
        if (neutral == null || neutral.isClosed()) neutral = texture("GeometryNode neutral material texture", 255, 255, 255, 255,
                ModelTextureColorSpace.LINEAR_DATA);
        return MinecraftModelGpuAccess.textureView(neutral);
    }

    GpuTextureView normal() {
        if (normal == null || normal.isClosed()) normal = texture("GeometryNode neutral normal texture", 128, 128, 255, 255,
                ModelTextureColorSpace.NORMAL_VECTOR);
        return MinecraftModelGpuAccess.textureView(normal);
    }

    private static ModelGpuTexture texture(String label, int red, int green, int blue, int alpha,
                                           ModelTextureColorSpace usage) {
        DecodedModelImage image = new DecodedModelImage(1, 1,
                new byte[]{(byte) red, (byte) green, (byte) blue, (byte) alpha});
        return new MinecraftModelGpuDevice().createTexture(label,
                new ModelGpuImagePlan(new ModelGpuTextureKey(0, usage), List.of(image)));
    }

    @Override public void close() {
        if (neutral != null && !neutral.isClosed()) neutral.close();
        if (normal != null && !normal.isClosed()) normal.close();
        neutral = null;
        normal = null;
    }
}
