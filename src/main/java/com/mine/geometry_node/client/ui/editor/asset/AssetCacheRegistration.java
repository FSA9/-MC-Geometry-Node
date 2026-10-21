package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.asset.remote.RemoteGraphPathIndex;
import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.schematic.SchematicThumbnailView;

/** Registers process-owned asset caches without opening the asset browser. */
public final class AssetCacheRegistration {
    private AssetCacheRegistration() {}

    public static void init() {
        ImageThumbnailView.registerCache();
        SchematicThumbnailView.registerCache();
        ClientAssetPreviewService.INSTANCE.registerCache();
        RemoteGraphPathIndex.INSTANCE.registerCache();
    }
}
