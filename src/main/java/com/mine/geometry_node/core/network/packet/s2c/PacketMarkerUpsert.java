package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketMarkerUpsert(MarkerPayload marker) implements CustomPacketPayload {
    public static final Type<PacketMarkerUpsert> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "marker_upsert")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketMarkerUpsert> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.marker.write(buf),
            buf -> new PacketMarkerUpsert(MarkerPayload.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
