package com.mine.geometry_node.client.ui.components.integration.asset.preview;

import com.mine.geometry_node.client.feature.asset.preview.AssetPreviewArtifact;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

/** Creates a view for a resolved artifact. */
public interface AssetPreviewRenderer {
    boolean supports(AssetEntry entry, AssetPreviewArtifact artifact);

    View create(Context context, AssetEntry entry, AssetPreviewArtifact artifact);
}
