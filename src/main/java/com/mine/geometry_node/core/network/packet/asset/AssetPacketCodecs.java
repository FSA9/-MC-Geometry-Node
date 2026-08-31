package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Shared wire format for asset packet values used by multiple protocol features. */
public final class AssetPacketCodecs {
    private AssetPacketCodecs() {
    }

    public static void writeRemoteAssetEntries(
            RegistryFriendlyByteBuf buffer,
            List<RemoteAssetEntry> entries,
            int maximum
    ) {
        List<RemoteAssetEntry> values = entries == null ? List.of() : entries;
        writeBoundedCount(buffer, values.size(), maximum, "remote asset entry");
        for (RemoteAssetEntry entry : values) writeRemoteAssetEntry(buffer, entry);
    }

    public static List<RemoteAssetEntry> readRemoteAssetEntries(
            RegistryFriendlyByteBuf buffer,
            int maximum
    ) {
        int size = readBoundedCount(buffer, maximum, "remote asset entry");
        List<RemoteAssetEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(readRemoteAssetEntry(buffer));
        return entries;
    }

    public static void writeRemoteAssetEntry(RegistryFriendlyByteBuf buffer, RemoteAssetEntry entry) {
        if (entry == null) throw new IllegalArgumentException("remote asset entry must not be null");
        buffer.writeUtf(entry.path(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeUtf(entry.name(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeBoolean(entry.directory());
        buffer.writeLong(entry.size());
        buffer.writeLong(entry.lastModified());
        buffer.writeUtf(entry.assetTypeId(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeUtf(entry.variantId(), AssetPacketLimits.MAX_PATH_LENGTH);
    }

    public static RemoteAssetEntry readRemoteAssetEntry(RegistryFriendlyByteBuf buffer) {
        return new RemoteAssetEntry(
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH)
        );
    }

    public static void writeBoundedCount(
            RegistryFriendlyByteBuf buffer,
            int count,
            int maximum,
            String label
    ) {
        buffer.writeVarInt(AssetPacketLimits.requireCount(count, maximum, label));
    }

    public static int readBoundedCount(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        return AssetPacketLimits.requireCount(buffer.readVarInt(), maximum, label);
    }
}
