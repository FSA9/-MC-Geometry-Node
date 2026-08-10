package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.AssetTransferPolicy;

public enum AssetPreviewKind {
    IMAGE,
    SCHEMATIC;

    public static AssetPreviewKind fromAssetType(String assetTypeId) {
        if (AssetTransferPolicy.IMAGE_TYPE_ID.equals(assetTypeId)) return IMAGE;
        if (AssetTransferPolicy.SCHEMATIC_TYPE_ID.equals(assetTypeId)) return SCHEMATIC;
        return null;
    }
}
