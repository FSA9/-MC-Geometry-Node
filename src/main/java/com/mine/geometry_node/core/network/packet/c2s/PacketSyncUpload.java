package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * [C2S] 客户端请求上传/发布蓝图到服务器
 */
public record PacketSyncUpload(String graphId, String jsonContent) implements CustomPacketPayload {

    // 1. 定义数据包的唯一标识 (ID)
    public static final Type<PacketSyncUpload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "sync_upload"));

    // 2. 定义序列化与反序列化器 (StreamCodec)
    public static final StreamCodec<FriendlyByteBuf, PacketSyncUpload> STREAM_CODEC = StreamCodec.composite(
            // graphId 长度限制为标准 32767 即可
            ByteBufCodecs.stringUtf8(32767), PacketSyncUpload::graphId,
            // jsonContent 限制放宽到 262144 (256KB)，防止大型图纸导致发包崩溃
            ByteBufCodecs.stringUtf8(262144), PacketSyncUpload::jsonContent,
            PacketSyncUpload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}