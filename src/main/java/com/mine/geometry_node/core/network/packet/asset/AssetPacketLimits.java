package com.mine.geometry_node.core.network.packet.asset;

/** Shared structural limits for asset repository and transfer packets. */
public final class AssetPacketLimits {
    public static final int MAX_PATH_LENGTH = 32_767;
    public static final int MAX_MESSAGE_LENGTH = 32_767;
    public static final int MAX_REPOSITORY_ENTRIES = 65_536;
    public static final int MAX_FILE_OPERATION_PATHS = 4_096;
    public static final int MAX_TRANSFER_PLAN_PATHS = 16_384;
    public static final int MAX_TRANSFER_MANIFEST_ENTRIES = 65_536;
    public static final int MAX_TRANSFER_CONFLICTS = 16_384;

    private AssetPacketLimits() {
    }

    public static int requireCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }
}
