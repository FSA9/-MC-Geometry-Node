package com.mine.geometry_node.core.network.packet.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.*;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class AssetPreviewPacketCodecs {
    private AssetPreviewPacketCodecs() {}
    public static void writeRevision(RegistryFriendlyByteBuf b, AssetPreviewRevision r) {
        b.writeUtf(r.identity().remotePath(), AssetPreviewLimits.MAX_PATH_LENGTH);
        b.writeUtf(r.identity().kind().id(), AssetPreviewLimits.MAX_PATH_LENGTH);
        b.writeLong(r.sourceSize()); b.writeLong(r.sourceLastModified()); b.writeVarInt(r.formatVersion());
    }
    public static AssetPreviewRevision readRevision(RegistryFriendlyByteBuf b) {
        String path = b.readUtf(AssetPreviewLimits.MAX_PATH_LENGTH);
        AssetPreviewKind kind = AssetPreviewKind.fromId(b.readUtf(AssetPreviewLimits.MAX_PATH_LENGTH));
        if (kind == null || !kind.isConcrete()) throw new IllegalArgumentException("Invalid asset preview kind");
        return new AssetPreviewRevision(new AssetPreviewIdentity(path, kind), b.readLong(), b.readLong(), b.readVarInt());
    }
    public static void writeDescriptor(RegistryFriendlyByteBuf b, AssetPreviewDescriptor d) {
        writeRevision(b, d.revision()); b.writeVarInt(d.format().ordinal()); b.writeVarInt(d.width()); b.writeVarInt(d.height());
        b.writeVarInt(d.encodedBytes()); b.writeUtf(d.sha256(), 64);
    }
    public static AssetPreviewDescriptor readDescriptor(RegistryFriendlyByteBuf b) {
        return new AssetPreviewDescriptor(readRevision(b), readEnum(b, AssetPreviewFormat.values()),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(64));
    }
    public static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf b, E[] values) {
        int i = b.readVarInt(); if (i < 0 || i >= values.length) throw new IllegalArgumentException("Invalid nativepreview enum ordinal");
        return values[i];
    }
}
