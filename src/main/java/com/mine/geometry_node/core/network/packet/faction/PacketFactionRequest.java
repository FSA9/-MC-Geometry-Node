package com.mine.geometry_node.core.network.packet.faction;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;

/** Small faction commands; catalog replacement is deliberately not a client operation. */
public record PacketFactionRequest(int requestId, long expectedRevision, String operation,
                                   Map<String, Object> values) implements CustomPacketPayload {
    private static final Set<String> OPERATIONS = Set.of("READ", "CREATE", "UPDATE", "DELETE", "RELATION", "RANK", "REMOVE_RANK",
            "ADD_TAG", "REMOVE_TAG", "REPLACE_DATA");
    public static final Type<PacketFactionRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "faction_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFactionRequest> STREAM_CODEC = StreamCodec.of(
            (buffer, request) -> {
                buffer.writeInt(request.requestId()); buffer.writeLong(request.expectedRevision());
                buffer.writeUtf(request.operation(), 16);
                buffer.writeByteArray(GraphMetadataDataValues.encode(request.values(), buffer.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES));
            }, buffer -> new PacketFactionRequest(buffer.readInt(), buffer.readLong(), buffer.readUtf(16),
                    GraphMetadataDataValues.decode(buffer.readByteArray(GraphMetadataDataValues.MAX_WRITE_BYTES), buffer.registryAccess())));

    public PacketFactionRequest {
        if (!OPERATIONS.contains(operation) || expectedRevision < -1)
            throw new IllegalArgumentException("Invalid faction request");
        values = GraphMetadataDataValues.snapshot(values);
        if (operation.equals("READ") && !values.isEmpty()) throw new IllegalArgumentException("Read carries changes");
    }
    public boolean edit() { return !operation.equals("READ"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
