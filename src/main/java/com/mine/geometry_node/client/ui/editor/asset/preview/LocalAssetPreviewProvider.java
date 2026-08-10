package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;

final class LocalAssetPreviewProvider implements AssetPreviewProvider {
    @Override
    public boolean supports(AssetEntry entry) {
        return entry != null && entry.sourceKind() == AssetSourceKind.LOCAL
                && entry.supports(AssetTypeAction.PREVIEW) && entry.localFile() != null;
    }

    @Override
    public Subscription subscribe(AssetEntry entry, Listener listener) {
        if (!supports(entry) || !entry.localFile().isFile()) listener.unavailable();
        else listener.available(entry.localFile());
        return Subscription.NONE;
    }
}
