package com.mine.geometry_node.core.network.packet.entityeditor;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Empty revision reads; otherwise replacement is a changeset of editable sections/attribute fields. */
public record PacketEntityEditorRequest(long requestId, String dimension, UUID entity,
                                       String expectedRevision, Map<String, Object> replacement) implements CustomPacketPayload {
    public static final Type<PacketEntityEditorRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_editor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityEditorRequest> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.dimension(), 128); b.writeUUID(p.entity());
                b.writeUtf(p.expectedRevision(), 64);
                if (p.edit()) b.writeByteArray(WorldObjectDataValues.encode(p.replacement(), b.registryAccess(), WorldObjectDataValues.MAX_WRITE_BYTES));
            }, b -> {
                long id = b.readLong(); String dimension = b.readUtf(128); UUID entity = b.readUUID();
                String revision = b.readUtf(64);
                Map<String, Object> values = revision.isEmpty() ? Map.of()
                        : WorldObjectDataValues.decode(b.readByteArray(WorldObjectDataValues.MAX_WRITE_BYTES), b.registryAccess());
                return new PacketEntityEditorRequest(id, dimension, entity, revision, values);
            });
    public PacketEntityEditorRequest {
        Objects.requireNonNull(dimension); Objects.requireNonNull(entity); Objects.requireNonNull(expectedRevision);
        if (dimension.isEmpty() || dimension.length() > 128 || Identifier.tryParse(dimension) == null
                || !expectedRevision.isEmpty() && !expectedRevision.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid entity editor request identity");
        replacement = WorldObjectDataValues.snapshot(replacement);
        if (expectedRevision.isEmpty() && !replacement.isEmpty()) throw new IllegalArgumentException("Read cannot carry changes");
    }
    public boolean edit() { return !expectedRevision.isEmpty(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
