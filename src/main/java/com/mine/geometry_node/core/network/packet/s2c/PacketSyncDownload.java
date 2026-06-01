package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketSyncDownload(String graphId, String jsonContent) implements CustomPacketPayload {

    public static final Type<PacketSyncDownload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "sync_download"));

    public static final StreamCodec<FriendlyByteBuf, PacketSyncDownload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32767), PacketSyncDownload::graphId,
            ByteBufCodecs.stringUtf8(262144), PacketSyncDownload::jsonContent, // 256KB 限制
            PacketSyncDownload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}