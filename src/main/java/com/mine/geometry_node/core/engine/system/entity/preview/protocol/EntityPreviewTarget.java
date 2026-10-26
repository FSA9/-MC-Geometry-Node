package com.mine.geometry_node.core.engine.system.entity.preview.protocol;

import java.util.Objects;
import java.util.UUID;

/** Entity identity and dimension hint for a live appearance or registered unloaded snapshot. */
public record EntityPreviewTarget(UUID entityId, String dimension) {
    public static final int MAX_DIMENSION_LENGTH = 256;
    public EntityPreviewTarget {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH)
            throw new IllegalArgumentException("Invalid entity preview dimension");
    }
}
