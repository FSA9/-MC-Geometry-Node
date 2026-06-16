package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PacketShopTradeRequest(UUID sessionId, String offerId) implements CustomPacketPayload {
    public static final Type<PacketShopTradeRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "shop_trade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketShopTradeRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.sessionId);
                buf.writeUtf(packet.offerId == null ? "" : packet.offerId, 32767);
            },
            buf -> new PacketShopTradeRequest(buf.readUUID(), buf.readUtf(32767))
    );

    public PacketShopTradeRequest {
        offerId = offerId == null ? "" : offerId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
