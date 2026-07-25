package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

@FunctionalInterface
public interface AssetTask<T> {
    T run(AssetTaskContext context) throws Exception;
}
