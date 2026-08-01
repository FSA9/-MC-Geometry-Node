package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * [C2S 数据包] 客户端输入状态同步
 */
public record PacketPlayerInput(
        String keyId,      // 按键标识符，如 "skill_1", "ctrl"
        String action,     // 动作类型: "PRESS", "RELEASE"
        int durationTicks  // 按住 tick 数 (仅在 RELEASE 时有意义)
) implements CustomPacketPayload {

    public static final Type<PacketPlayerInput> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "player_input")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketPlayerInput> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.keyId);
                buf.writeUtf(packet.action);
                buf.writeVarInt(packet.durationTicks);
            },
            buf -> new PacketPlayerInput(buf.readUtf(), buf.readUtf(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
