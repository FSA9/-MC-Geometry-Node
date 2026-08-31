package com.mine.geometry_node.core.engine.system.asset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetFormats;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Single content-aware source for asset type and variant identification. */
public final class AssetTypeCatalog {
    public static final String GRAPH_TYPE_ID = "graph";
    public static final String SCHEMATIC_TYPE_ID = "schematic";
    public static final String IMAGE_TYPE_ID = "image";

    private AssetTypeCatalog() {
    }

    public static AssetMetadata inspect(Path file) {
        String logicalPath = file != null && file.getFileName() != null ? file.getFileName().toString() : "";
        return inspect(file, logicalPath);
    }

    /**
     * Inspects content from {@code file} while using {@code logicalPath} for format recognition.
     * This supports verified upload temporary files whose temporary suffix differs from the target.
     */
    public static AssetMetadata inspect(Path file, String logicalPath) {
        String lowerPath = normalizePath(logicalPath);
        if (lowerPath.endsWith(".schem") || lowerPath.endsWith(".schematic")) {
            return new AssetMetadata(SCHEMATIC_TYPE_ID, "");
        }
        if (ImageAssetFormats.isSupportedPath(lowerPath)) {
            return new AssetMetadata(IMAGE_TYPE_ID, "");
        }
        if (!lowerPath.endsWith(".json") || file == null || !Files.isRegularFile(file)) {
            return AssetMetadata.UNKNOWN;
        }

        String graphTypeId = inspectGraphType(file);
        return graphTypeId.isEmpty()
                ? AssetMetadata.UNKNOWN
                : new AssetMetadata(GRAPH_TYPE_ID, graphTypeId);
    }

    public static boolean isTransferablePath(String path) {
        String lowerPath = normalizePath(path);
        return lowerPath.endsWith(".json")
                || lowerPath.endsWith(".schem")
                || lowerPath.endsWith(".schematic")
                || ImageAssetFormats.isSupportedPath(lowerPath);
    }

    public static boolean isType(Path file, String typeId) {
        return inspect(file).typeId().equals(normalizeId(typeId));
    }

    public static AssetMetadata inspectGraphJson(String json) {
        if (json == null) return AssetMetadata.UNKNOWN;
        try {
            String graphTypeId = inspectGraphType(JsonParser.parseString(json));
            return graphTypeId.isEmpty()
                    ? AssetMetadata.UNKNOWN
                    : new AssetMetadata(GRAPH_TYPE_ID, graphTypeId);
        } catch (Exception ignored) {
            return AssetMetadata.UNKNOWN;
        }
    }

    private static String inspectGraphType(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return inspectGraphType(JsonParser.parseReader(reader));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String inspectGraphType(JsonElement parsed) {
        if (parsed == null || !parsed.isJsonObject()) return "";
        JsonObject root = parsed.getAsJsonObject();
        JsonElement kind = root.get("graph_kind");
        if (kind == null || !kind.isJsonPrimitive() || !kind.getAsJsonPrimitive().isString()) return "";

        String typeId = GraphType.normalizeId(kind.getAsString());
        return GraphTypeRegistry.INSTANCE.get(typeId) != null ? typeId : "";
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
