package com.mine.geometry_node.client.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;

import java.nio.file.Path;
import java.util.Objects;

public record ClientAssetTransferRequest(
        AssetTransferDirection direction,
        Path localPath,
        String remotePath,
        AssetTransferConflictPolicy conflictPolicy
) {
    public ClientAssetTransferRequest {
        direction = Objects.requireNonNull(direction, "direction");
        localPath = Objects.requireNonNull(localPath, "localPath").toAbsolutePath().normalize();
        remotePath = Objects.requireNonNullElse(remotePath, "").trim().replace('\\', '/');
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        if (remotePath.isEmpty()) throw new IllegalArgumentException("Remote asset path cannot be blank");
    }

    public static ClientAssetTransferRequest upload(Path source, String remotePath,
                                                    AssetTransferConflictPolicy conflictPolicy) {
        return new ClientAssetTransferRequest(AssetTransferDirection.UPLOAD, source, remotePath, conflictPolicy);
    }

    public static ClientAssetTransferRequest download(String remotePath, Path target,
                                                      AssetTransferConflictPolicy conflictPolicy) {
        return new ClientAssetTransferRequest(AssetTransferDirection.DOWNLOAD, target, remotePath, conflictPolicy);
    }
}
