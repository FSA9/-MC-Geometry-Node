package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;

import java.nio.file.Path;

/** Builds the fixed transfer requests used by the server-owned Data Library. */
final class DataLibraryTransferRequests {
    private static final String REMOTE_PATH = "data-library";

    private DataLibraryTransferRequests() {
    }

    static ClientAssetTransferRequest upload(Path source, AssetTransferPurpose purpose) {
        if (purpose != AssetTransferPurpose.DATA_LIBRARY_CREATE
                && purpose != AssetTransferPurpose.DATA_LIBRARY_UPDATE) {
            throw new IllegalArgumentException("Invalid Data Library upload purpose: " + purpose);
        }
        return new ClientAssetTransferRequest(AssetTransferDirection.UPLOAD, source, REMOTE_PATH,
                AssetTransferConflictPolicy.OVERWRITE, purpose);
    }

    static ClientAssetTransferRequest download(Path target) {
        return new ClientAssetTransferRequest(AssetTransferDirection.DOWNLOAD, target, REMOTE_PATH,
                AssetTransferConflictPolicy.OVERWRITE, AssetTransferPurpose.DATA_LIBRARY_DOWNLOAD);
    }
}
