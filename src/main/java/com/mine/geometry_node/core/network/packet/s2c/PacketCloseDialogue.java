package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PacketCloseDialogue(UUID sessionId, String reason) implements CustomPacketPayload {
    public static final Type<PacketCloseDialogue> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "close_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCloseDialogue> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.sessionId);
                buf.writeUtf(packet.reason, 32767);
            },
            buf -> new PacketCloseDialogue(buf.readUUID(), buf.readUtf(32767))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
