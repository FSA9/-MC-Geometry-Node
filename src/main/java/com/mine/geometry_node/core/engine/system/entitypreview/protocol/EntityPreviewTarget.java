package com.mine.geometry_node.core.engine.system.entitypreview.protocol;

import java.util.Objects;
import java.util.UUID;

/** A loaded entity's identity in an explicitly selected dimension. */
public record EntityPreviewTarget(UUID entityId, String dimension) {
    public static final int MAX_DIMENSION_LENGTH = 256;
    public EntityPreviewTarget {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH)
            throw new IllegalArgumentException("Invalid entity preview dimension");
    }
}
