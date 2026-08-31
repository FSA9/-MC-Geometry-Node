package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;

public enum AssetPreviewKind {
    IMAGE,
    SCHEMATIC;

    public static AssetPreviewKind fromAssetType(String assetTypeId) {
        if (AssetTypeCatalog.IMAGE_TYPE_ID.equals(assetTypeId)) return IMAGE;
        if (AssetTypeCatalog.SCHEMATIC_TYPE_ID.equals(assetTypeId)) return SCHEMATIC;
        return null;
    }
}
