package com.mine.geometry_node.core.engine.system.marker;

import net.minecraft.resources.Identifier;

/**
 * Common marker presentation metadata. The renderer id is resolved only on the client.
 */
public record MarkerType(
        String id,
        String translationKey,
        int color,
        String rendererId
) {
    public MarkerType {
        id = normalizeId(id);
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("translationKey must not be blank");
        }
        rendererId = normalizeId(rendererId);
    }

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("marker id must not be blank");
        }
        return Identifier.parse(id.trim()).toString();
    }
}
