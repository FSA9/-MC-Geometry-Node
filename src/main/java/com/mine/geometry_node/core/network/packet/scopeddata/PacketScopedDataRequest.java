package com.mine.geometry_node.core.network.packet.scopeddata;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Explicit catalog/read/CAS requests for the current save's PUBLIC dictionaries. */
public record PacketScopedDataRequest(long requestId, Action action, String target,
                                      String expectedRevision, Map<String, Object> replacement) implements CustomPacketPayload {
    public enum Action { CATALOG, READ, REPLACE }
    public static final String GLOBAL = "GLOBAL";
    public static final int MAX_TARGET_LENGTH = 256;
    public static final Type<PacketScopedDataRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "scoped_data_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketScopedDataRequest> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeEnum(p.action()); b.writeUtf(p.target(), MAX_TARGET_LENGTH);
                if (p.action() == Action.REPLACE) {
                    b.writeUtf(p.expectedRevision(), 64);
                    b.writeByteArray(GraphMetadataDataValues.encode(p.replacement(), b.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES));
                }
            }, b -> {
                long id = b.readLong(); Action action = b.readEnum(Action.class); String target = b.readUtf(MAX_TARGET_LENGTH);
                String revision = action == Action.REPLACE ? b.readUtf(64) : "";
                Map<String, Object> values = action == Action.REPLACE
                        ? GraphMetadataDataValues.decode(b.readByteArray(GraphMetadataDataValues.MAX_WRITE_BYTES), b.registryAccess()) : Map.of();
                return new PacketScopedDataRequest(id, action, target, revision, values);
            });
    public PacketScopedDataRequest {
        validateTarget(action, target);
        Objects.requireNonNull(expectedRevision);
        replacement = GraphMetadataDataValues.snapshot(replacement);
        if (action == Action.REPLACE ? !expectedRevision.matches("[0-9a-f]{64}")
                : !expectedRevision.isEmpty() || !replacement.isEmpty())
            throw new IllegalArgumentException("Invalid scoped data request body");
    }
    public static void validateTarget(Action action, String target) {
        Objects.requireNonNull(action); Objects.requireNonNull(target);
        if (action == Action.CATALOG) {
            if (!target.isEmpty()) throw new IllegalArgumentException("Catalog target must be empty");
        } else if (!GLOBAL.equals(target)) validateDimension(target);
    }
    public static void validateDimension(String target) {
        Identifier id = Identifier.tryParse(target);
        if (target.isEmpty() || target.length() > MAX_TARGET_LENGTH || id == null || !id.toString().equals(target))
            throw new IllegalArgumentException("Expected a canonical dimension ID");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
