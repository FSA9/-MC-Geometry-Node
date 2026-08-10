package com.mine.geometry_node.client.ui.editor.asset.model;

import com.mine.geometry_node.core.engine.system.asset.AssetTransferPolicy;
import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetFormats;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssetTypeRegistry {
    public static final String DIRECTORY_ID = "directory";
    public static final String GRAPH_ID = AssetTransferPolicy.GRAPH_TYPE_ID;
    public static final String SCHEMATIC_ID = AssetTransferPolicy.SCHEMATIC_TYPE_ID;
    public static final String IMAGE_ID = AssetTransferPolicy.IMAGE_TYPE_ID;
    public static final String FILE_ID = "file";

    public static final AssetTypeRegistry INSTANCE = new AssetTypeRegistry();

    private final Map<String, AssetType> mTypes = new LinkedHashMap<>();

    private AssetTypeRegistry() {
        register(new AssetType(
                DIRECTORY_ID, 0xFFFFC857, true, true, AssetPreviewKind.NONE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.COPY, AssetTypeAction.MOVE,
                        AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD),
                (name, directory) -> directory));
        register(new AssetType(
                GRAPH_ID, 0xFF88CCFF, false, true, AssetPreviewKind.NONE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.OPEN, AssetTypeAction.PICK, AssetTypeAction.FAVORITE,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD, AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME),
                extensionMatcher(".json")));
        register(new AssetType(
                SCHEMATIC_ID, 0xFF86B8FF, false, true, AssetPreviewKind.SCHEMATIC,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.PREVIEW, AssetTypeAction.FAVORITE,
                        AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD),
                extensionMatcher(".schem", ".schematic")));
        register(new AssetType(
                IMAGE_ID, 0xFF77C99D, false, true, AssetPreviewKind.IMAGE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.PREVIEW, AssetTypeAction.FAVORITE,
                        AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD),
                (name, directory) -> !directory && ImageAssetFormats.isSupportedPath(name)));
        register(new AssetType(
                FILE_ID, 0xFF88CCFF, false, false, AssetPreviewKind.NONE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.COPY, AssetTypeAction.MOVE,
                        AssetTypeAction.DELETE, AssetTypeAction.RENAME),
                (name, directory) -> !directory));
    }

    public synchronized void register(AssetType type) {
        if (type == null) throw new IllegalArgumentException("asset type must not be null");
        if (mTypes.containsKey(type.id())) {
            throw new IllegalArgumentException("duplicate asset type: " + type.id());
        }
        mTypes.put(type.id(), type);
    }

    public synchronized AssetType get(String id) {
        return mTypes.get(AssetType.normalizeId(id));
    }

    public synchronized List<AssetType> all() {
        return List.copyOf(mTypes.values());
    }

    public synchronized AssetType resolve(AssetSourceKind source, String name, boolean directory) {
        AssetType fallback = mTypes.get(FILE_ID);
        for (AssetType type : mTypes.values()) {
            if (FILE_ID.equals(type.id())) continue;
            if (type.matches(source, name, directory)) return type;
        }
        return fallback;
    }

    public boolean isType(AssetEntry entry, String typeId) {
        return entry != null && entry.type() != null
                && entry.type().id().equals(AssetType.normalizeId(typeId));
    }

    public boolean isType(java.io.File file, String typeId) {
        if (file == null) return false;
        AssetType type = resolve(AssetSourceKind.LOCAL, file.getName(), file.isDirectory());
        return type != null && type.id().equals(AssetType.normalizeId(typeId));
    }

    private static AssetType.Matcher extensionMatcher(String... extensions) {
        List<String> normalized = new ArrayList<>();
        for (String extension : extensions) {
            if (extension != null && !extension.isBlank()) {
                String value = extension.toLowerCase(Locale.ROOT);
                normalized.add(value.startsWith(".") ? value : "." + value);
            }
        }
        return (name, directory) -> {
            if (directory || name == null) return false;
            String lowerName = name.toLowerCase(Locale.ROOT);
            for (String extension : normalized) {
                if (lowerName.endsWith(extension)) return true;
            }
            return false;
        };
    }
}
