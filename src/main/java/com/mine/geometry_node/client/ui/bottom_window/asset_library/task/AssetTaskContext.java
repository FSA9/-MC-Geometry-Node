package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

public interface AssetTaskContext {
    boolean isCancelled();

    void progress(String message, int processed, int total);

    default void checkCancelled() throws InterruptedException {
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("asset task cancelled");
        }
    }
}
