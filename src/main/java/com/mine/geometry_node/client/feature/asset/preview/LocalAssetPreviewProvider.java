package com.mine.geometry_node.client.feature.asset.preview;

import com.mine.geometry_node.client.platform.ClientThread;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;

final class LocalAssetPreviewProvider implements AssetPreviewProvider {
    @Override
    public boolean supports(AssetEntry entry) {
        return entry != null && entry.sourceKind() == AssetSourceKind.LOCAL
                && entry.supports(AssetTypeAction.PREVIEW) && entry.localFile() != null;
    }

    @Override
    public Subscription subscribe(AssetEntry entry, Listener listener) {
        ClientThread.execute(() -> {
            if (!supports(entry) || !entry.localFile().isFile()) listener.unavailable();
            else listener.available(new AssetPreviewArtifact(entry.localFile(), entry.type().previewKind(), ""));
        });
        return Subscription.NONE;
    }
}
