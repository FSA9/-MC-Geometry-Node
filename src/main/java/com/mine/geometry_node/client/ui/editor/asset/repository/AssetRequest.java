package com.mine.geometry_node.client.ui.editor.asset.repository;

@FunctionalInterface
public interface AssetRequest {
    AssetRequest NONE = () -> {};

    void cancel();
}
