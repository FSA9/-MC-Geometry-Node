package com.mine.geometry_node.client.feature.asset.repository;

@FunctionalInterface
public interface AssetRequest {
    AssetRequest NONE = () -> {};

    void cancel();
}
