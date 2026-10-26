package com.mine.geometry_node.client.runtime.game.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.function.Supplier;

/** Render-thread producer. The channel owns publication textures until all UI/GPU references retire. */
final class ViewportFrameSurface {
    private final Supplier<? extends ViewportFramePublication> factory;
    private volatile ViewportFramePublication image;
    private GpuTexture texture;
    private final FramePublicationPool<GpuTexture> publications = new FramePublicationPool<>(3, GpuTexture::close);

    ViewportFrameSurface() { this(ViewportFramePublications::create); }

    ViewportFrameSurface(Supplier<? extends ViewportFramePublication> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    /** Readers never create a channel. A surface keeps its single channel through clear/resize. */
    ViewportFramePublication publication() { return image; }

    private ViewportFramePublication publisher() {
        if (image == null) image = Objects.requireNonNull(factory.get(), "Viewport frame publication factory returned null");
        return image;
    }

    void capture(GpuTexture source) {
        if (source == null || source.isClosed()) { clear(); return; }
        // Resolve the installed bridge before allocating/copying any publication resources.
        var image = publisher();
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
            if (fresh) {
                GpuTexture published = texture;
                if (!image.publish(published, null, () -> Minecraft.getInstance().execute(published::close))) {
                    published.close();
                    texture = null;
                }
            }
        } catch (RuntimeException | Error failure) {
            if (fresh) texture.close(); // Not published: no UI/GPU bridge owns it yet.
            else image.clear();
            texture = null;
            throw failure;
        }
    }

    /** Each EDIT publication owns immutable pixels until all recorded/GPU draws retire. */
    void capture(GpuTexture source, PresentedViewportFrame frame) {
        if (source == null || source.isClosed() || frame == null) { clear(); return; }
        var image = publisher();
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
                    () -> Minecraft.getInstance().execute(() -> publications.release(lease))))
                publications.release(lease);
        } catch (RuntimeException | Error failure) {
            publications.release(lease);
            throw failure;
        }
    }

    void clear() {
        var current = image;
        if (current != null) current.clear();
        texture = null;
        publications.clear();
    }
}
