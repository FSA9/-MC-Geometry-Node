package com.mine.geometry_node.client.runtime.game;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

/** Render-thread producer. The channel owns publication textures until all UI/GPU references retire. */
final class ViewportFrameSurface {
    final GameFrameImage.Channel image;
    private GpuTexture texture;

    ViewportFrameSurface(GameFrameImage.Channel image) { this.image = image; }

    void capture(GpuTexture source) {
        if (source == null || source.isClosed()) { clear(); return; }
        int width = source.getWidth(0), height = source.getHeight(0);
        boolean fresh = texture == null || texture.getWidth(0) != width || texture.getHeight(0) != height;
        if (fresh) {
            clear();
            texture = RenderSystem.getDevice().createTexture(() -> "GeometryNode viewport publication",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    source.getFormat(), width, height, 1, 1);
        }
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height);
        } catch (RuntimeException | Error failure) {
            if (fresh) texture.close(); // Not published: no UI/GPU bridge owns it yet.
            else image.clear();
            texture = null;
            throw failure;
        }
        if (fresh && !image.publish(texture)) { texture.close(); texture = null; }
    }

    void clear() { image.clear(); texture = null; }
}
