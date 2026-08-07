package com.mine.geometry_node.client.ui.editor.asset.task;

public interface AssetTaskContext {
    boolean isCancelled();

    void progress(String message, int processed, int total);

    void enterCommitPhase() throws InterruptedException;

    default void checkCancelled() throws InterruptedException {
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("asset task cancelled");
        }
    }
}
