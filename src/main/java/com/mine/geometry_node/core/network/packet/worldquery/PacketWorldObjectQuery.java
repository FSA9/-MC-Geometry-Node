package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectQuery;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

public record PacketWorldObjectQuery(long sessionId, long requestId, WorldObjectQuery query) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectQuery> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectQuery> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeLong(p.sessionId); b.writeLong(p.requestId); WorldObjectPacketCodecs.query(b, p.query); },
            b -> new PacketWorldObjectQuery(b.readLong(), b.readLong(), WorldObjectPacketCodecs.query(b)));
    /** Legacy callers represent independent requests, not a shared window session. */
    public PacketWorldObjectQuery(long requestId, WorldObjectQuery query) { this(requestId, requestId, query); }
    public PacketWorldObjectQuery { Objects.requireNonNull(query); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
