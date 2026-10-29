package com.mine.geometry_node.client.ui.components.integration.asset.preview;

import com.mine.geometry_node.client.feature.asset.preview.AssetPreviewProviders;
import com.mine.geometry_node.client.ui.components.integration.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.components.integration.asset.schematic.SchematicThumbnailView;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/** Clears rendered thumbnails together with the remote preview artifacts they reference. */
public final class AssetPreviewCaches {
    private AssetPreviewCaches() { }

    /** Retires both local thumbnail kinds after a committed file or directory mutation. */
    public static void invalidateUnder(File file) {
        ImageThumbnailView.invalidateUnder(file);
        SchematicThumbnailView.invalidateUnder(file);
    }

    public static CompletableFuture<Void> clearCurrentRemoteCache() {
        ImageThumbnailView.clearCache();
        return AssetPreviewProviders.clearCurrentRemoteCache();
    }

    public static CompletableFuture<Void> clearAllRemoteCaches() {
        ImageThumbnailView.clearCache();
        return AssetPreviewProviders.clearAllRemoteCaches();
    }
}
