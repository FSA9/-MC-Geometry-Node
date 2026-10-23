package com.mine.geometry_node.core.network.packet.entityeditor;

import com.mine.geometry_node.core.engine.system.worldquery.metadata.WorldObjectDataValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Success returns authoritative values and a connection-owned revision; errors contain only a translation key. */
public record PacketEntityEditorResult(long requestId, Map<String, Object> values, String revision,
                                      String message) implements CustomPacketPayload {
    public static final Type<PacketEntityEditorResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityEditorResult> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.revision(), 64); b.writeUtf(p.message(), 128);
                b.writeByteArray(WorldObjectDataValues.encode(p.values(), b.registryAccess(), WorldObjectDataValues.MAX_READ_BYTES));
            }, b -> {
                long id = b.readLong(); String revision = b.readUtf(64), message = b.readUtf(128);
                return new PacketEntityEditorResult(id, WorldObjectDataValues.decode(b.readByteArray(WorldObjectDataValues.MAX_READ_BYTES), b.registryAccess()), revision, message);
            });
    public PacketEntityEditorResult {
        values = WorldObjectDataValues.snapshot(values);
        Objects.requireNonNull(revision); Objects.requireNonNull(message);
        if (message.length() > 128 || !revision.isEmpty() && !revision.matches("[0-9a-f]{64}")
                || message.isEmpty() && revision.isEmpty() || !message.isEmpty() && (!revision.isEmpty() || !values.isEmpty()))
            throw new IllegalArgumentException("Invalid entity editor result");
    }
    public static PacketEntityEditorResult error(long requestId, String reason) {
        return new PacketEntityEditorResult(requestId, Map.of(), "", "geometry_node.entity_editor." + reason);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
