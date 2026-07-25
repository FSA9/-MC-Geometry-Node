package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

public interface AssetTaskListener<T> {
    default void onStarted() {
    }

    default void onProgress(AssetTaskProgress progress) {
    }

    default void onSuccess(T result) {
    }

    default void onFailure(Throwable error) {
    }

    default void onCancelled() {
    }
}
