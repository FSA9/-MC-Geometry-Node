package com.mine.geometry_node.client.runtime.game.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

/** Render-thread producer. The channel owns publication textures until all UI/GPU references retire. */
final class ViewportFrameSurface {
    final GameFrameImage.Channel image;
    private GpuTexture texture;
    private final FramePublicationPool<GpuTexture> publications = new FramePublicationPool<>(3, GpuTexture::close);

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

    /** Each EDIT publication owns immutable pixels until all recorded/GPU draws retire. */
    void capture(GpuTexture source, PresentedViewportFrame frame) {
        if (source == null || source.isClosed() || frame == null) { clear(); return; }
        int width = source.getWidth(0), height = source.getHeight(0);
        var lease = publications.acquire(candidate -> !candidate.isClosed()
                        && candidate.getWidth(0) == width && candidate.getHeight(0) == height,
                () -> RenderSystem.getDevice().createTexture(() -> "GeometryNode immutable viewport frame",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                        source.getFormat(), width, height, 1, 1));
        // Never allocate an unbounded queue when the UI/GPU is behind; retain the last displayed frame.
        if (lease == null) return;
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, lease.resource,
                    0, 0, 0, 0, 0, width, height);
            if (!image.publish(lease.resource, frame,
                    () -> net.minecraft.client.Minecraft.getInstance().execute(() -> publications.release(lease))))
                publications.release(lease);
        } catch (RuntimeException | Error failure) {
            publications.release(lease);
            throw failure;
        }
    }

    void clear() { image.clear(); texture = null; publications.clear(); }
}
