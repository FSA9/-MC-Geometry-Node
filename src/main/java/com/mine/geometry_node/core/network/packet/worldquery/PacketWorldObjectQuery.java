package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectQuery;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

public record PacketWorldObjectQuery(long sessionId, long requestId, WorldObjectQuery query,
                                    WorldObjectLookup lookup) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectQuery> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectQuery> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.sessionId); b.writeLong(p.requestId); WorldObjectPacketCodecs.query(b, p.query);
                b.writeBoolean(p.lookup != null);
                if (p.lookup != null) {
                    b.writeUtf(p.lookup.provider(), 128); b.writeUtf(p.lookup.dimension(), 128); b.writeUUID(p.lookup.objectId());
                }
            },
            b -> {
                if (b.readableBytes() > WorldObjectQuery.MAX_REQUEST_BYTES)
                    throw new IllegalArgumentException("World query request too large");
                long session = b.readLong(), request = b.readLong();
                var query = WorldObjectPacketCodecs.query(b);
                var lookup = b.readBoolean() ? new WorldObjectLookup(b.readUtf(128), b.readUtf(128), b.readUUID()) : null;
                return new PacketWorldObjectQuery(session, request, query, lookup);
            });
    /** Legacy callers represent independent requests, not a shared window session. */
    public PacketWorldObjectQuery(long requestId, WorldObjectQuery query) { this(requestId, requestId, query); }
    public PacketWorldObjectQuery(long sessionId, long requestId, WorldObjectQuery query) { this(sessionId, requestId, query, null); }
    public PacketWorldObjectQuery { Objects.requireNonNull(query); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
