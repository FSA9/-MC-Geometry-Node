package com.mine.geometry_node.client.ui.editor.asset.task;

@FunctionalInterface
public interface AssetTask<T> {
    T run(AssetTaskContext context) throws Exception;
}
