package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;

import java.util.Arrays;

/** Immutable validated content frame shared by upload and download packet directions. */
public record AssetTransferChunkData(AssetTransferCursor cursor, byte[] content) {
    public AssetTransferChunkData {
        if (cursor == null) throw new IllegalArgumentException("cursor must not be null");
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        if (content.length == 0 || content.length > AssetTransferProtocolLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid asset transfer chunk length: " + content.length);
        }
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
