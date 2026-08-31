package com.mine.geometry_node.client.ui.editor.asset.model;

import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssetTypeRegistry {
    public static final String DIRECTORY_ID = "directory";
    public static final String GRAPH_ID = AssetTypeCatalog.GRAPH_TYPE_ID;
    public static final String SCHEMATIC_ID = AssetTypeCatalog.SCHEMATIC_TYPE_ID;
    public static final String IMAGE_ID = AssetTypeCatalog.IMAGE_TYPE_ID;
    public static final String FILE_ID = "file";

    public static final AssetTypeRegistry INSTANCE = new AssetTypeRegistry();

    private final Map<String, AssetType> mTypes = new LinkedHashMap<>();

    private AssetTypeRegistry() {
        register(new AssetType(
                DIRECTORY_ID, 0xFFFFC857, true, true, AssetPreviewKind.NONE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.COPY, AssetTypeAction.MOVE,
                        AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD)));
        register(assetType(
                GRAPH_ID, 0xFF88CCFF, false, true,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.OPEN, AssetTypeAction.PICK, AssetTypeAction.FAVORITE,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD, AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME)));
        register(assetType(
                SCHEMATIC_ID, 0xFF86B8FF, false, true,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.PREVIEW, AssetTypeAction.FAVORITE,
                        AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD)));
        register(assetType(
                IMAGE_ID, 0xFF77C99D, false, true,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.PREVIEW, AssetTypeAction.FAVORITE,
                        AssetTypeAction.COPY,
                        AssetTypeAction.MOVE, AssetTypeAction.DELETE, AssetTypeAction.RENAME,
                        AssetTypeAction.UPLOAD, AssetTypeAction.DOWNLOAD)));
        register(new AssetType(
                FILE_ID, 0xFF88CCFF, false, false, AssetPreviewKind.NONE,
                EnumSet.allOf(AssetSourceKind.class),
                EnumSet.of(AssetTypeAction.PICK, AssetTypeAction.COPY, AssetTypeAction.MOVE,
                        AssetTypeAction.DELETE, AssetTypeAction.RENAME)));
    }

    public synchronized void register(AssetType type) {
        if (type == null) throw new IllegalArgumentException("asset type must not be null");
        if (mTypes.containsKey(type.id())) {
            throw new IllegalArgumentException("duplicate asset type: " + type.id());
        }
        mTypes.put(type.id(), type);
    }

    private static AssetType assetType(String id, int defaultColor, boolean directory, boolean displayInBrowser,
                                       java.util.Set<AssetSourceKind> sources,
                                       java.util.Set<AssetTypeAction> actions) {
        if (AssetTypeCatalog.definition(id) == null) {
            throw new IllegalArgumentException("asset type is not registered in the common catalog: " + id);
        }
        return new AssetType(id, defaultColor, directory, displayInBrowser,
                AssetTypeCatalog.previewKind(id), sources, actions);
    }

    public synchronized AssetType get(String id) {
        return mTypes.get(AssetType.normalizeId(id));
    }

    public synchronized List<AssetType> all() {
        return List.copyOf(mTypes.values());
    }

    public synchronized AssetType resolve(String typeId, AssetSourceKind source, boolean directory) {
        AssetType type = directory ? mTypes.get(DIRECTORY_ID) : mTypes.get(AssetType.normalizeId(typeId));
        if (type == null || !type.supportsSource(source)) return mTypes.get(FILE_ID);
        return type;
    }

    public AssetType resolveLocal(java.io.File file) {
        if (file == null) return get(FILE_ID);
        String typeId = file.isDirectory() ? DIRECTORY_ID : AssetTypeCatalog.inspect(file.toPath()).typeId();
        return resolve(typeId, AssetSourceKind.LOCAL, file.isDirectory());
    }

    public boolean isType(AssetEntry entry, String typeId) {
        return entry != null && entry.type() != null
                && entry.type().id().equals(AssetType.normalizeId(typeId));
    }

    public boolean isType(java.io.File file, String typeId) {
        if (file == null) return false;
        AssetType type = resolveLocal(file);
        return type != null && type.id().equals(AssetType.normalizeId(typeId));
    }
}
