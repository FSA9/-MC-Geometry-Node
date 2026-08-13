package com.mine.geometry_node.client.model.gpu;

import java.util.List;

/** Immutable, linear-color texture levels prepared off the render thread. */
public record ModelGpuImagePlan(int imageIndex, List<DecodedModelImage> levels) {
    public ModelGpuImagePlan {
        if (imageIndex < 0) throw new IllegalArgumentException("imageIndex must be non-negative");
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

    public DecodedModelImage base() { return levels.getFirst(); }
}
