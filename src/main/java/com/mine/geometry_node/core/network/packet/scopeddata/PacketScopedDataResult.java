package com.mine.geometry_node.core.network.packet.scopeddata;

import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataDataValues;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Empty collections/strings denote absent fields; the wire carries only the selected result body. */
public record PacketScopedDataResult(long requestId, PacketScopedDataRequest.Action action, String target,
                                     List<String> dimensions, Map<String, Object> values, String revision,
                                     String message) implements CustomPacketPayload {
    public static final int MAX_DIMENSIONS = 1024, MAX_PACKET_BYTES = 300 * 1024;
    public static final String ERROR_PREFIX = "database.data_update_";
    public static final Type<PacketScopedDataResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "scoped_data_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketScopedDataResult> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                int start = b.writerIndex();
                b.writeLong(p.requestId()); b.writeEnum(p.action()); b.writeUtf(p.target(), PacketScopedDataRequest.MAX_TARGET_LENGTH);
                b.writeUtf(p.message(), 128);
                if (p.message().isEmpty()) {
                    if (p.action() == PacketScopedDataRequest.Action.CATALOG) {
                        b.writeVarInt(p.dimensions().size());
                        for (String dimension : p.dimensions()) b.writeUtf(dimension, PacketScopedDataRequest.MAX_TARGET_LENGTH);
                    } else {
                        b.writeUtf(p.revision(), 64);
                        b.writeByteArray(GraphMetadataDataValues.encode(p.values(), b.registryAccess(), GraphMetadataDataValues.MAX_READ_BYTES));
                    }
                }
                if (b.writerIndex() - start > MAX_PACKET_BYTES) throw new IllegalArgumentException("Scoped data result exceeds byte limit");
            }, b -> {
                int start = b.readerIndex();
                long id = b.readLong(); var action = b.readEnum(PacketScopedDataRequest.Action.class);
                String target = b.readUtf(PacketScopedDataRequest.MAX_TARGET_LENGTH), message = b.readUtf(128);
                List<String> dimensions = new ArrayList<>(); Map<String, Object> values = Map.of(); String revision = "";
                if (message.isEmpty()) {
                    if (action == PacketScopedDataRequest.Action.CATALOG) {
                        int count = b.readVarInt();
                        if (count < 0 || count > MAX_DIMENSIONS) throw new IllegalArgumentException("Too many dimensions");
                        for (int i = 0; i < count; i++) dimensions.add(b.readUtf(PacketScopedDataRequest.MAX_TARGET_LENGTH));
                    } else {
                        revision = b.readUtf(64);
                        values = GraphMetadataDataValues.decode(b.readByteArray(GraphMetadataDataValues.MAX_READ_BYTES), b.registryAccess());
                    }
                }
                if (b.readerIndex() - start > MAX_PACKET_BYTES) throw new IllegalArgumentException("Scoped data result exceeds byte limit");
                return new PacketScopedDataResult(id, action, target, dimensions, values, revision, message);
            });
    public PacketScopedDataResult {
        PacketScopedDataRequest.validateTarget(action, target);
        Objects.requireNonNull(revision); Objects.requireNonNull(message);
        if (dimensions.size() > MAX_DIMENSIONS) throw new IllegalArgumentException("Too many dimensions");
        dimensions = List.copyOf(dimensions); values = GraphMetadataDataValues.snapshot(values);
        Set<String> unique = new HashSet<>();
        for (String dimension : dimensions) {
            PacketScopedDataRequest.validateDimension(dimension);
            if (!unique.add(dimension)) throw new IllegalArgumentException("Duplicate dimension");
        }
        if (!message.isEmpty()) {
            if (message.length() > 128 || !message.startsWith(ERROR_PREFIX) || !dimensions.isEmpty() || !values.isEmpty() || !revision.isEmpty())
                throw new IllegalArgumentException("Invalid scoped data error");
        } else if (action == PacketScopedDataRequest.Action.CATALOG) {
            if (!values.isEmpty() || !revision.isEmpty()) throw new IllegalArgumentException("Invalid catalog body");
        } else if (!dimensions.isEmpty() || !revision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid data body");
    }
    public static PacketScopedDataResult error(PacketScopedDataRequest request, String reason) {
        return new PacketScopedDataResult(request.requestId(), request.action(), request.target(), List.of(), Map.of(), "", ERROR_PREFIX + reason);
    }
    /** Preflight the complete response envelope before owner commit, using the actual codec. */
    public void ensureEncodable(RegistryAccess registries) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_PACKET_BYTES), registries);
        try { STREAM_CODEC.encode(buffer, this); }
        catch (IndexOutOfBoundsException limit) { throw new IllegalArgumentException("Scoped data result exceeds byte limit", limit); }
        finally { buffer.release(); }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
