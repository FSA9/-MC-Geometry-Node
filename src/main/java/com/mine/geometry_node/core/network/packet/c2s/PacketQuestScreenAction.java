package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketQuestScreenAction(String action, String taskKey, String instanceId)
        implements CustomPacketPayload {
    public static final String OPEN = "open";
    public static final String CLOSE = "close";
    public static final String ACCEPT = "accept";
    public static final String SUBMIT = "submit";
    public static final String ABANDON = "abandon";

    public static final Type<PacketQuestScreenAction> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "quest_screen_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketQuestScreenAction> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.action(), 64);
                buf.writeUtf(packet.taskKey(), 32767);
                buf.writeUtf(packet.instanceId(), 64);
            },
            buf -> new PacketQuestScreenAction(
                    buf.readUtf(64),
                    buf.readUtf(32767),
                    buf.readUtf(64)
            )
    );

    public PacketQuestScreenAction {
        action = action == null ? "" : action.trim();
        taskKey = taskKey == null ? "" : taskKey.trim();
        instanceId = instanceId == null ? "" : instanceId.trim();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
