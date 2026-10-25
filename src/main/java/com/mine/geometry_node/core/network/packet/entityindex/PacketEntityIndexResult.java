package com.mine.geometry_node.core.network.packet.entityindex;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

/** A successful cancellation still returns authoritative state after the row has disappeared. */
public record PacketEntityIndexResult(long requestId, boolean registered, long revision, String message) implements CustomPacketPayload {
    public static final int MAX_RESPONSE_BYTES = 1024, MAX_MESSAGE_LENGTH = 128;
    public static final Type<PacketEntityIndexResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_index_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityIndexResult> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.requestId()); b.writeBoolean(p.registered()); b.writeLong(p.revision()); b.writeUtf(p.message(), MAX_MESSAGE_LENGTH); },
            b -> {
                if (b.readableBytes() > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Entity index result too large");
                return new PacketEntityIndexResult(b.readLong(), b.readBoolean(), b.readLong(), b.readUtf(MAX_MESSAGE_LENGTH));
            });
    public PacketEntityIndexResult {
        Objects.requireNonNull(message);
        if (message.length() > MAX_MESSAGE_LENGTH || revision < 0 || registered != (revision > 0))
            throw new IllegalArgumentException("Invalid entity index result");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
