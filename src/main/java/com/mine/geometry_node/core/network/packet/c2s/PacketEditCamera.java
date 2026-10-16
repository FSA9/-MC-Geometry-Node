package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketEditCamera(Identifier dimension, boolean active, double x, double y, double z)
        implements CustomPacketPayload {
    public static final Type<PacketEditCamera> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "edit_camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEditCamera> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeIdentifier(p.dimension); buf.writeBoolean(p.active); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); },
            buf -> new PacketEditCamera(buf.readIdentifier(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
    public boolean validPosition() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) < 30_000_000 && Math.abs(y) < 30_000_000 && Math.abs(z) < 30_000_000;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
