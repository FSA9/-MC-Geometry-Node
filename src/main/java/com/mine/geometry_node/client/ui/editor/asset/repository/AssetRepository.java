package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;

import java.util.function.Consumer;

public interface AssetRepository {
    AssetSourceKind sourceKind();

    boolean supports(AssetRepositoryOperation operation);

    AssetRequest browse(AssetBrowseRequest request, Consumer<AssetListing> onResult);
}
