package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketCaptureEntityTemplateResponse(
        int requestId,
        boolean success,
        String entityTypeId,
        CompoundTag entityData,
        String messageKey
) implements CustomPacketPayload {
    public static final Type<PacketCaptureEntityTemplateResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "capture_entity_template_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCaptureEntityTemplateResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeBoolean(packet.success);
                buf.writeUtf(packet.entityTypeId, 512);
                buf.writeNbt(packet.entityData);
                buf.writeUtf(packet.messageKey, 512);
            },
            buf -> new PacketCaptureEntityTemplateResponse(
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readUtf(512),
                    readTag(buf),
                    buf.readUtf(512)
            )
    );

    public PacketCaptureEntityTemplateResponse {
        entityTypeId = entityTypeId == null ? "" : entityTypeId;
        entityData = entityData == null ? new CompoundTag() : entityData.copy();
        messageKey = messageKey == null ? "" : messageKey;
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag != null ? tag : new CompoundTag();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
