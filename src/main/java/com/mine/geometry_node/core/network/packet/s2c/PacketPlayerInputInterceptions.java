package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes the input-event interception mask for the local player. */
public record PacketPlayerInputInterceptions(int mask) implements CustomPacketPayload {
    public static final Type<PacketPlayerInputInterceptions> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "player_input_interceptions")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketPlayerInputInterceptions> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeInt(packet.mask),
            buf -> new PacketPlayerInputInterceptions(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
