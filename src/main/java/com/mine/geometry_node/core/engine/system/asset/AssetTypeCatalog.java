package com.mine.geometry_node.core.engine.system.asset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetFormats;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Single content-aware source for asset type and variant identification. */
public final class AssetTypeCatalog {
    public static final String GRAPH_TYPE_ID = "graph";
    public static final String SCHEMATIC_TYPE_ID = "schematic";
    public static final String IMAGE_TYPE_ID = "image";

    private static final Map<String, AssetTypeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static volatile Map<String, AssetTypeDefinition> DEFINITION_BY_ID = Map.of();
    private static volatile List<AssetTypeDefinition> DEFINITION_SNAPSHOT = List.of();

    static {
        register(new AssetTypeDefinition(SCHEMATIC_TYPE_ID,
                simpleExtensionRecognizer(".schem", ".schematic"), AssetPreviewKind.SCHEMATIC));
        register(new AssetTypeDefinition(IMAGE_TYPE_ID, new AssetTypeRecognizer() {
            @Override
            public boolean supportsCandidatePath(String normalizedPath) {
                return ImageAssetFormats.isSupportedPath(normalizedPath);
            }

            @Override
            public String inspectVariant(Path file, String normalizedPath) {
                return file != null && Files.isRegularFile(file) ? "" : null;
            }
        }, AssetPreviewKind.IMAGE));
        register(new AssetTypeDefinition(GRAPH_TYPE_ID, new AssetTypeRecognizer() {
            @Override
            public boolean supportsCandidatePath(String normalizedPath) {
                return normalizedPath.endsWith(".json");
            }

            @Override
            public String inspectVariant(Path file, String normalizedPath) {
                if (file == null || !Files.isRegularFile(file)) return null;
                String graphTypeId = inspectGraphType(file);
                return graphTypeId.isEmpty() ? null : graphTypeId;
            }
        }, AssetPreviewKind.NONE));
    }

    private AssetTypeCatalog() {
    }

    public static synchronized void register(AssetTypeDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("asset type definition must not be null");
        if (DEFINITIONS.containsKey(definition.id())) {
            throw new IllegalArgumentException("duplicate asset type definition: " + definition.id());
        }
        DEFINITIONS.put(definition.id(), definition);
        DEFINITION_BY_ID = Map.copyOf(DEFINITIONS);
        DEFINITION_SNAPSHOT = List.copyOf(DEFINITIONS.values());
    }

    public static AssetTypeDefinition definition(String typeId) {
        return DEFINITION_BY_ID.get(AssetTypeDefinition.normalizeId(typeId));
    }

    public static List<AssetTypeDefinition> definitions() {
        return DEFINITION_SNAPSHOT;
    }

    public static AssetPreviewKind previewKind(String typeId) {
        AssetTypeDefinition definition = definition(typeId);
        return definition != null ? definition.previewKind() : AssetPreviewKind.NONE;
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
        for (AssetTypeDefinition definition : definitions()) {
            if (!definition.supportsCandidatePath(lowerPath)) continue;
            AssetMetadata metadata = definition.inspect(file, lowerPath);
            if (metadata != null && metadata.isKnown()) return metadata;
        }
        return AssetMetadata.UNKNOWN;
    }

    /**
     * Checks only whether a logical path could contain a registered asset type.
     * Callers handling an existing file must use {@link #inspect(Path, String)} instead.
     */
    public static boolean isCandidatePath(String path) {
        String lowerPath = normalizePath(path);
        for (AssetTypeDefinition definition : definitions()) {
            if (definition.supportsCandidatePath(lowerPath)) return true;
        }
        return false;
    }

    public static boolean isRecognizedAsset(Path file) {
        return file != null && Files.isRegularFile(file) && inspect(file).isKnown();
    }

    public static boolean isRecognizedAsset(Path file, String logicalPath) {
        return file != null && Files.isRegularFile(file) && inspect(file, logicalPath).isKnown();
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

    private static AssetTypeRecognizer simpleExtensionRecognizer(String... extensions) {
        List<String> suffixes = List.of(extensions);
        return new AssetTypeRecognizer() {
            @Override
            public boolean supportsCandidatePath(String normalizedPath) {
                for (String suffix : suffixes) {
                    if (normalizedPath.endsWith(suffix)) return true;
                }
                return false;
            }

            @Override
            public String inspectVariant(Path file, String normalizedPath) {
                return file != null && Files.isRegularFile(file) ? "" : null;
            }
        };
    }
}
