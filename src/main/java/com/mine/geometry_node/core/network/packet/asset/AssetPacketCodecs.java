package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.AssetMetadata;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Shared wire format for asset packet values used by multiple protocol features. */
public final class AssetPacketCodecs {
    private AssetPacketCodecs() {
    }

    public static void writeAssetDescriptors(
            RegistryFriendlyByteBuf buffer,
            List<AssetDescriptor> entries,
            int maximum
    ) {
        List<AssetDescriptor> values = entries == null ? List.of() : entries;
        writeBoundedCount(buffer, values.size(), maximum, "asset descriptor");
        for (AssetDescriptor entry : values) writeAssetDescriptor(buffer, entry);
    }

    public static List<AssetDescriptor> readAssetDescriptors(
            RegistryFriendlyByteBuf buffer,
            int maximum
    ) {
        int size = readBoundedCount(buffer, maximum, "asset descriptor");
        List<AssetDescriptor> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(readAssetDescriptor(buffer));
        return entries;
    }

    public static void writeAssetDescriptor(RegistryFriendlyByteBuf buffer, AssetDescriptor entry) {
        if (entry == null) throw new IllegalArgumentException("asset descriptor must not be null");
        buffer.writeUtf(entry.path(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeUtf(entry.name(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeBoolean(entry.directory());
        buffer.writeLong(entry.size());
        buffer.writeLong(entry.lastModified());
        buffer.writeUtf(entry.metadata().typeId(), AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeUtf(entry.metadata().variantId(), AssetPacketLimits.MAX_PATH_LENGTH);
    }

    public static AssetDescriptor readAssetDescriptor(RegistryFriendlyByteBuf buffer) {
        return new AssetDescriptor(
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                new AssetMetadata(
                        buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                        buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH))
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
