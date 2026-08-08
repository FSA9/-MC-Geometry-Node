package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * [C2S 数据包] 客户端输入状态同步
 */
public record PacketPlayerInput(
        String keyId,      // 按键标识符，如 "skill_1", "ctrl"
        String action,     // 动作类型: "PRESS", "RELEASE"
        Vec3 clientVelocity // 客户端当前 tick 物理结算后的真实速度
) implements CustomPacketPayload {

    public static final int MAX_KEY_ID_LENGTH = 32;
    public static final int MAX_ACTION_LENGTH = 16;

    public static final Type<PacketPlayerInput> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "player_input")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketPlayerInput> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.keyId, MAX_KEY_ID_LENGTH);
                buf.writeUtf(packet.action, MAX_ACTION_LENGTH);
                Vec3.STREAM_CODEC.encode(buf, packet.clientVelocity);
            },
            buf -> new PacketPlayerInput(
                    buf.readUtf(MAX_KEY_ID_LENGTH),
                    buf.readUtf(MAX_ACTION_LENGTH),
                    Vec3.STREAM_CODEC.decode(buf)
            )
    );

    public PacketPlayerInput {
        keyId = keyId == null ? "" : keyId;
        action = action == null ? "" : action;
        clientVelocity = clientVelocity == null ? Vec3.ZERO : clientVelocity;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
