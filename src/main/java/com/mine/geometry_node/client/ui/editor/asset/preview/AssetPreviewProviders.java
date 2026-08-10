package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AssetPreviewProviders {
    private static final List<AssetPreviewProvider> PROVIDERS = List.of(
            new LocalAssetPreviewProvider(), new RemoteAssetPreviewProvider());

    private AssetPreviewProviders() {}

    public static AssetPreviewProvider resolve(AssetEntry entry) {
        for (AssetPreviewProvider provider : PROVIDERS) {
            if (provider.supports(entry)) return provider;
        }
        return null;
    }

    public static CompletableFuture<Void> clearCurrentRemoteCache() {
        return RemoteAssetPreviewProvider.clearCurrentServerCache();
    }

    public static CompletableFuture<Void> clearAllRemoteCaches() {
        return RemoteAssetPreviewProvider.clearAllCaches();
    }
}
