package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** On-demand read or a bounded CAS replacement. Empty expectedRevision means read. */
public record PacketWorldObjectDataRequest(long requestId, String target, String key,
                                           String expectedRevision, Map<String, Object> replacement) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectDataRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_data_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectDataRequest> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.target(), 36); b.writeUtf(p.key(), 1024);
                b.writeUtf(p.expectedRevision(), 64);
                if (p.edit()) b.writeByteArray(GraphMetadataDataValues.encode(p.replacement(), b.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES));
            }, b -> {
                long id = b.readLong(); String target = b.readUtf(36), key = b.readUtf(1024), revision = b.readUtf(64);
                Map<String, Object> values = revision.isEmpty() ? Map.of()
                        : GraphMetadataDataValues.decode(b.readByteArray(GraphMetadataDataValues.MAX_WRITE_BYTES), b.registryAccess());
                return new PacketWorldObjectDataRequest(id, target, key, revision, values);
            });
    public PacketWorldObjectDataRequest {
        Objects.requireNonNull(target); Objects.requireNonNull(key); Objects.requireNonNull(expectedRevision);
        if (target.length() != 36 || key.isEmpty() || key.length() > 1024
                || !expectedRevision.isEmpty() && !expectedRevision.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid custom data request identity");
        UUID.fromString(target);
        replacement = GraphMetadataDataValues.snapshot(replacement);
        if (expectedRevision.isEmpty() && !replacement.isEmpty()) throw new IllegalArgumentException("Read cannot carry replacement data");
    }
    public boolean edit() { return !expectedRevision.isEmpty(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
