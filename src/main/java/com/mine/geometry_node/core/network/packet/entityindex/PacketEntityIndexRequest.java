package com.mine.geometry_node.core.network.packet.entityindex;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;
import java.util.UUID;

/** Bounded registration read/write; dimension is a hint, UUID is the server-wide identity. */
public record PacketEntityIndexRequest(long requestId, String dimension, UUID entity, boolean edit,
                                       boolean registered, long expectedRevision) implements CustomPacketPayload {
    public static final int MAX_REQUEST_BYTES = 1024, MAX_DIMENSION_LENGTH = 256;
    public static final Type<PacketEntityIndexRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_index_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityIndexRequest> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.dimension(), MAX_DIMENSION_LENGTH); b.writeUUID(p.entity());
                b.writeBoolean(p.edit()); b.writeBoolean(p.registered()); b.writeLong(p.expectedRevision());
            }, b -> {
                if (b.readableBytes() > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Entity index request too large");
                return new PacketEntityIndexRequest(b.readLong(), b.readUtf(MAX_DIMENSION_LENGTH), b.readUUID(),
                        b.readBoolean(), b.readBoolean(), b.readLong());
            });
    public PacketEntityIndexRequest {
        Objects.requireNonNull(entity); Objects.requireNonNull(dimension);
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH || Identifier.tryParse(dimension) == null
                || expectedRevision < 0 || !edit && (registered || expectedRevision != 0))
            throw new IllegalArgumentException("Invalid entity index request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
