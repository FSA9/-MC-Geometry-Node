package com.mine.geometry_node.client.runtime.game.render;

import com.mojang.blaze3d.textures.GpuTexture;

/** One source's publication, owned by its runtime surface. Implementations retain consumer/GPU references. */
public interface ViewportFramePublication {
    /** Render thread only. Success transfers one texture lease to the publication; failure or an
     * exception leaves it with the producer. Metadata must remain bound to these exact pixels.
     * The release callback runs once, only after publication, recorded and GPU references retire;
     * the producer supplies any dispatch needed to close/recycle the texture on Minecraft's thread.
     */
    boolean publish(GpuTexture texture, PresentedViewportFrame metadata, Runnable released);

    /** Render thread only. Withdraw the publication without invalidating retained consumer work. */
    void clear();

    /** Thread-safe, atomic dimension snapshot. */
    ViewportFrameSize size();

    default boolean isAvailable() { return size().available(); }
}
