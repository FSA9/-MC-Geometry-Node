package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketCaptureEntityTemplateRequest(int requestId, int entityId) implements CustomPacketPayload {
    public static final Type<PacketCaptureEntityTemplateRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "capture_entity_template_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCaptureEntityTemplateRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeInt(packet.entityId);
            },
            buf -> new PacketCaptureEntityTemplateRequest(buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
