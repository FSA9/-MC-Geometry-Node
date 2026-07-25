package com.mine.geometry_node.client.ui.bottom_window.asset_library.task;

public record AssetTaskProgress(String message, int processed, int total) {
    public AssetTaskProgress {
        message = message == null ? "" : message;
        processed = Math.max(0, processed);
        total = Math.max(0, total);
    }
}
