package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.*;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MinecraftModelGpuDevice implements ModelGpuDevice {
    @Override
    public ModelGpuBuffer createBuffer(String label, ModelGpuBufferKind kind, byte[] data) {
        RenderSystem.assertOnRenderThread();
        int usage = kind == ModelGpuBufferKind.VERTEX ? GpuBuffer.USAGE_VERTEX : GpuBuffer.USAGE_INDEX;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label, usage, direct(data));
        return new MinecraftModelGpuBuffer(buffer);
    }

    @Override
    public ModelGpuTexture createTexture(String label, ModelGpuImagePlan image) {
        RenderSystem.assertOnRenderThread();
        java.util.List<DecodedModelImage> levels = image.levels();
        DecodedModelImage base = image.base();
        GpuTexture texture = RenderSystem.getDevice().createTexture(label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, base.width(), base.height(), 1, levels.size());
        GpuTextureView view = null;
        try {
            GlStateManager.clearGlErrors();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < levels.size(); level++) {
                DecodedModelImage mip = levels.get(level);
                encoder.writeToTexture(texture, direct(mip.rgba()), NativeImage.Format.RGBA,
                        level, 0, 0, 0, mip.width(), mip.height());
            }
            int error = GlStateManager._getError();
            if (error != 0) throw new IllegalStateException("OpenGL texture upload failed with error " + error);
            view = RenderSystem.getDevice().createTextureView(texture);
            MinecraftModelGpuTexture result = new MinecraftModelGpuTexture(texture, view);
            texture = null;
            view = null;
            return result;
        } finally {
            if (view != null && !view.isClosed()) view.close();
            if (texture != null && !texture.isClosed()) texture.close();
        }
    }

    private static ByteBuffer direct(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        buffer.put(data).flip();
        return buffer;
    }

}
