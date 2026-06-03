package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PacketDialogueChoice(UUID sessionId, String action, String choiceId) implements CustomPacketPayload {
    public static final String ACTION_CHOOSE = "choose";
    public static final String ACTION_CLOSE = "close";

    public static final Type<PacketDialogueChoice> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "dialogue_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketDialogueChoice> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.sessionId);
                buf.writeUtf(packet.action, 64);
                buf.writeUtf(packet.choiceId, 32767);
            },
            buf -> new PacketDialogueChoice(buf.readUUID(), buf.readUtf(64), buf.readUtf(32767))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
