package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * [S2C] 服务端向客户端返回图纸操作(如上传/绑定)的结果状态
 */
public record PacketSyncResponse(boolean success, String graphId, String message) implements CustomPacketPayload {

    public static final Type<PacketSyncResponse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "sync_response"));

    public static final StreamCodec<FriendlyByteBuf, PacketSyncResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PacketSyncResponse::success,
            ByteBufCodecs.stringUtf8(32767), PacketSyncResponse::graphId,
            ByteBufCodecs.stringUtf8(32767), PacketSyncResponse::message,
            PacketSyncResponse::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}