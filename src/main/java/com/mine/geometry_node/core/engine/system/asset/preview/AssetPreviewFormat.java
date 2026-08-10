package com.mine.geometry_node.core.engine.system.asset.preview;

import java.util.Locale;

public enum AssetPreviewFormat {
    JPEG("jpg"),
    PNG("png");

    private final String extension;

    AssetPreviewFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static AssetPreviewFormat fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
