package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import java.util.List;

/** Structural facts supplied by the active Iris shadow target generation. */
public record IrisShadowCapabilities(long generation, int colorAttachmentCount,
                                     List<String> colorFormats, boolean irisOwnsProgramFramebuffer) {
    public IrisShadowCapabilities {
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        if (colorAttachmentCount < 1) throw new IllegalArgumentException("shadow pass needs a color attachment");
        colorFormats = List.copyOf(colorFormats);
        if (colorFormats.size() != colorAttachmentCount) {
            throw new IllegalArgumentException("shadow color format count mismatch");
        }
    }

    public boolean multiRenderTarget() { return colorAttachmentCount > 1; }
}
