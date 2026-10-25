package com.mine.geometry_node.core.network.packet.faction;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** An authoritative catalog accompanies successful commands and optimistic concurrency conflicts. */
public record PacketFactionResult(int requestId, boolean success, String message, String catalogJson)
        implements CustomPacketPayload {
    public static final int MAX_CATALOG_BYTES = com.mine.geometry_node.core.engine.system.faction.model.FactionRules.MAX_DOCUMENT_BYTES;
    public static final Type<PacketFactionResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "faction_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFactionResult> STREAM_CODEC = StreamCodec.of(
            (buffer, result) -> {
                buffer.writeInt(result.requestId()); buffer.writeBoolean(result.success());
                buffer.writeUtf(result.message(), 128); buffer.writeUtf(result.catalogJson(), MAX_CATALOG_BYTES);
            }, buffer -> new PacketFactionResult(buffer.readInt(), buffer.readBoolean(), buffer.readUtf(128),
                    readCatalog(buffer)));

    private static String readCatalog(RegistryFriendlyByteBuf buffer) {
        int bytes = buffer.readVarInt();
        if (bytes < 0 || bytes > MAX_CATALOG_BYTES || bytes > buffer.readableBytes())
            throw new IllegalArgumentException("Faction catalog exceeds byte limit or is truncated");
        return buffer.readCharSequence(bytes, StandardCharsets.UTF_8).toString();
    }

    public PacketFactionResult {
        Objects.requireNonNull(message); Objects.requireNonNull(catalogJson);
        if (message.length() > 128 || catalogJson.getBytes(StandardCharsets.UTF_8).length > MAX_CATALOG_BYTES)
            throw new IllegalArgumentException("Faction result exceeds transport limit");
        if (success && catalogJson.isEmpty()) throw new IllegalArgumentException("Successful faction result has no catalog");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
