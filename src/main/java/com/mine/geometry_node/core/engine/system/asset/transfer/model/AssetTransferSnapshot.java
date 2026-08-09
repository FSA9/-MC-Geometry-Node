package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.List;

public record AssetTransferSnapshot(
        long revision,
        List<AssetTransferJobSnapshot> activeJobs,
        List<AssetTransferFileSnapshot> completedHistory,
        List<AssetTransferFileSnapshot> failedHistory
) {
    public AssetTransferSnapshot {
        revision = Math.max(0L, revision);
        activeJobs = activeJobs == null ? List.of() : List.copyOf(activeJobs);
        completedHistory = completedHistory == null ? List.of() : List.copyOf(completedHistory);
        failedHistory = failedHistory == null ? List.of() : List.copyOf(failedHistory);
    }

    public static AssetTransferSnapshot empty() {
        return new AssetTransferSnapshot(0L, List.of(), List.of(), List.of());
    }
}
