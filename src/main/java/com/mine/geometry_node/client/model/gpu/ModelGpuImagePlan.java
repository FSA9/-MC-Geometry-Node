package com.mine.geometry_node.client.model.gpu;

import java.util.List;

/** Immutable texture projection prepared off the render thread for one color-space role. */
public record ModelGpuImagePlan(ModelGpuTextureKey key, List<DecodedModelImage> levels) {
    public ModelGpuImagePlan {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        levels = levels == null ? List.of() : List.copyOf(levels);
        if (levels.isEmpty()) throw new IllegalArgumentException("GPU image plan requires a base level");
        for (int level = 1; level < levels.size(); level++) {
            DecodedModelImage previous = levels.get(level - 1);
            DecodedModelImage current = levels.get(level);
            if (current.width() != Math.max(1, previous.width() / 2)
                    || current.height() != Math.max(1, previous.height() / 2)) {
                throw new IllegalArgumentException("GPU image mip dimensions are not contiguous");
            }
        }
    }

    public ModelGpuImagePlan(int imageIndex, List<DecodedModelImage> levels) {
        this(new ModelGpuTextureKey(imageIndex, ModelTextureColorSpace.SRGB_COLOR), levels);
    }

    public int imageIndex() { return key.imageIndex(); }

    public DecodedModelImage base() { return levels.getFirst(); }
}
