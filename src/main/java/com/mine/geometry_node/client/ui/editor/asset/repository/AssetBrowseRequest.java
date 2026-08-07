package com.mine.geometry_node.client.ui.editor.asset.repository;

public record AssetBrowseRequest(AssetLocation location, AssetQuery query) {
    public AssetBrowseRequest {
        if (location == null) throw new IllegalArgumentException("asset location must not be null");
        query = query != null ? query : new AssetQuery("", "", false);
    }
}
