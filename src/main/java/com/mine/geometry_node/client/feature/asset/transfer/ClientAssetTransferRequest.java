package com.mine.geometry_node.client.feature.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;

import java.nio.file.Path;
import java.util.Objects;

public record ClientAssetTransferRequest(
        AssetTransferDirection direction,
        Path localPath,
        String remotePath,
        AssetTransferConflictPolicy conflictPolicy,
        AssetTransferPurpose purpose
) {
    public ClientAssetTransferRequest {
        direction = Objects.requireNonNull(direction, "direction");
        localPath = Objects.requireNonNull(localPath, "localPath").toAbsolutePath().normalize();
        remotePath = Objects.requireNonNullElse(remotePath, "").trim().replace('\\', '/');
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        purpose = Objects.requireNonNull(purpose, "purpose");
        if (remotePath.isEmpty()) throw new IllegalArgumentException("Remote asset path cannot be blank");
        if (purpose == AssetTransferPurpose.ASSET_REPOSITORY) {
            remotePath = AssetRepositoryPath.parse(remotePath, false).serialize();
        }
    }

    public ClientAssetTransferRequest(AssetTransferDirection direction, Path localPath, String remotePath,
                                      AssetTransferConflictPolicy conflictPolicy) {
        this(direction, localPath, remotePath, conflictPolicy, AssetTransferPurpose.ASSET_REPOSITORY);
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
