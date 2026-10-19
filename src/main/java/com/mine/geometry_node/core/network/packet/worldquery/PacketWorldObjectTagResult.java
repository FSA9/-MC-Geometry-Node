package com.mine.geometry_node.core.network.packet.worldquery;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

/** Empty message means committed (including idempotent no-op). Otherwise a database.tag_update_* key. */
public record PacketWorldObjectTagResult(long requestId, String message) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectTagResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_tag_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectTagResult> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.requestId()); b.writeUtf(p.message(), 128); },
            b -> new PacketWorldObjectTagResult(b.readLong(), b.readUtf(128)));
    public PacketWorldObjectTagResult {
        Objects.requireNonNull(message);
        if (message.length() > 128) throw new IllegalArgumentException("Tag result too long");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
