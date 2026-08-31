package com.mine.geometry_node.core.engine.system.asset.transfer.model;

/** Shared validated sequence/offset pair carried by chunk and acknowledgement packets. */
public record AssetTransferCursor(int sequence, long offset) {
    public AssetTransferCursor {
        if (sequence < 0 || offset < 0L) {
            throw new IllegalArgumentException("Negative asset transfer position");
        }
    }
}
