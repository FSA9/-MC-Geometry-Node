package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Lossless dictionary plus optimistic-concurrency fingerprint, only in response to an explicit read/edit. */
public record PacketWorldObjectDataResult(long requestId, Map<String, Object> values, String revision,
                                          String message) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectDataResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_data_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectDataResult> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.revision(), 64); b.writeUtf(p.message(), 128);
                b.writeByteArray(WorldObjectDataValues.encode(p.values(), b.registryAccess(), WorldObjectDataValues.MAX_READ_BYTES));
            }, b -> {
                long id = b.readLong(); String revision = b.readUtf(64), message = b.readUtf(128);
                return new PacketWorldObjectDataResult(id, WorldObjectDataValues.decode(b.readByteArray(WorldObjectDataValues.MAX_READ_BYTES), b.registryAccess()), revision, message);
            });
    public PacketWorldObjectDataResult {
        values = WorldObjectDataValues.snapshot(values);
        Objects.requireNonNull(revision); Objects.requireNonNull(message);
        if (message.length() > 128 || !revision.isEmpty() && !revision.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid custom data result");
        if (message.isEmpty() && revision.isEmpty() || !message.isEmpty() && (!revision.isEmpty() || !values.isEmpty()))
            throw new IllegalArgumentException("Inconsistent custom data result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
