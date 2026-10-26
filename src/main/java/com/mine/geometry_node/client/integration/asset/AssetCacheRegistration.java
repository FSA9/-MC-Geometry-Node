package com.mine.geometry_node.client.integration.asset;

import com.mine.geometry_node.client.feature.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.feature.asset.remote.RemoteGraphPathIndex;
import com.mine.geometry_node.client.ui.components.integration.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.components.integration.asset.schematic.SchematicThumbnailView;

/** Composes process-owned asset and presentation caches without opening an Area. */
public final class AssetCacheRegistration {
    private AssetCacheRegistration() {}

    public static void init() {
        ImageThumbnailView.registerCache();
        SchematicThumbnailView.registerCache();
        ClientAssetPreviewService.INSTANCE.registerCache();
        RemoteGraphPathIndex.INSTANCE.registerCache();
    }
}
