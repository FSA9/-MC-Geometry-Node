package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.ModelGpuTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class MinecraftModelGpuTexture implements ModelGpuTexture {
    private final GpuTexture texture;
    private final GpuTextureView view;

    MinecraftModelGpuTexture(GpuTexture texture, GpuTextureView view) {
        this.texture = texture;
        this.view = view;
    }

    public GpuTexture texture() { return texture; }
    public GpuTextureView view() { return view; }

    @Override public int width() { return texture.getWidth(0); }
    @Override public int height() { return texture.getHeight(0); }
    @Override public int mipLevels() { return texture.getMipLevels(); }
    @Override public boolean isClosed() { return texture.isClosed(); }

    @Override
    public void close() {
        if (!view.isClosed()) view.close();
        if (!texture.isClosed()) texture.close();
    }
}
