package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent before chunks for a new camera region; dimension prevents stale-world cache changes. */
public record PacketEditCameraView(Identifier dimension, boolean active, int x, int z, int radius)
        implements CustomPacketPayload {
    public static final Type<PacketEditCameraView> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "edit_camera_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEditCameraView> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeIdentifier(p.dimension); buf.writeBoolean(p.active); buf.writeInt(p.x); buf.writeInt(p.z); buf.writeVarInt(p.radius); },
            buf -> new PacketEditCameraView(buf.readIdentifier(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
