package com.mine.geometry_node.client.feature.asset.repository;

import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;

import java.util.function.Consumer;

public interface AssetRepository {
    AssetSourceKind sourceKind();

    boolean supports(AssetRepositoryOperation operation);

    /** Delivers results on the Minecraft client thread. */
    AssetRequest browse(AssetBrowseRequest request, Consumer<AssetListing> onResult);
}
