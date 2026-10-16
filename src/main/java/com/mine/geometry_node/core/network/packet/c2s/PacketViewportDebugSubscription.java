package com.mine.geometry_node.core.network.packet.c2s;

import com.mine.geometry_node.core.engine.graph.debug.ViewportDebugLayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketViewportDebugSubscription(Identifier dimension, int mask) implements CustomPacketPayload {
    public PacketViewportDebugSubscription {
        if (dimension == null || (mask & ~ViewportDebugLayer.validMask()) != 0) throw new IllegalArgumentException("Invalid debug subscription");
    }
    public static final Type<PacketViewportDebugSubscription> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "viewport_debug_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportDebugSubscription> STREAM_CODEC = StreamCodec.of(
        (buf,p) -> { buf.writeIdentifier(p.dimension); buf.writeVarInt(p.mask); },
        buf -> new PacketViewportDebugSubscription(buf.readIdentifier(),buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
