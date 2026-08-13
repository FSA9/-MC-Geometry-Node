package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.gpu.ModelGpuTexture;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class MinecraftModelGpuAccess {
    private MinecraftModelGpuAccess() {}

    public static GpuBuffer buffer(ModelGpuBuffer handle) {
        if (handle instanceof MinecraftModelGpuBuffer minecraft) return minecraft.buffer();
        throw new IllegalArgumentException("model buffer was not created by the Minecraft GPU device");
    }

    public static GpuTextureView textureView(ModelGpuTexture handle) {
        if (handle instanceof MinecraftModelGpuTexture minecraft) return minecraft.view();
        throw new IllegalArgumentException("model texture was not created by the Minecraft GPU device");
    }
}
