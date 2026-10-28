package com.mine.geometry_node.core.engine.system.worldquery.model;

import net.minecraft.resources.Identifier;
import java.util.Objects;
import java.util.UUID;

/** Exact owner lookup. Providers opt in explicitly; resolving never scans or loads chunks. */
public record WorldObjectLookup(String provider, String dimension, UUID objectId) {
    public WorldObjectLookup {
        Objects.requireNonNull(provider); Objects.requireNonNull(dimension); Objects.requireNonNull(objectId);
        if (provider.isBlank() || provider.length() > 128 || dimension.length() > 128
                || Identifier.tryParse(dimension) == null)
            throw new IllegalArgumentException("Invalid world object lookup");
    }
}
