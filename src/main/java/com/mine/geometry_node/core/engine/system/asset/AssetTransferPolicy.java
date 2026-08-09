package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetFormats;

import java.util.Locale;

/** Shared allowlist for asset types that may cross the client/server asset boundary. */
public final class AssetTransferPolicy {
    public static final String GRAPH_TYPE_ID = "graph";
    public static final String SCHEMATIC_TYPE_ID = "schematic";
    public static final String IMAGE_TYPE_ID = "image";

    private AssetTransferPolicy() {
    }

    public static String resolveTypeId(String path) {
        if (path == null || path.isBlank()) return "";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) return GRAPH_TYPE_ID;
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) return SCHEMATIC_TYPE_ID;
        if (ImageAssetFormats.isSupportedPath(lower)) return IMAGE_TYPE_ID;
        return "";
    }

    public static boolean isTransferablePath(String path) {
        return !resolveTypeId(path).isEmpty();
    }

    public static boolean isGraphPath(String path) {
        return GRAPH_TYPE_ID.equals(resolveTypeId(path));
    }
}
