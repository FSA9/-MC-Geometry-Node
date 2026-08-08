package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketMarkerSnapshot(List<MarkerPayload> markers) implements CustomPacketPayload {
    private static final int MAX_MARKERS = 4096;

    public static final Type<PacketMarkerSnapshot> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "marker_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketMarkerSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketMarkerSnapshot::new
    );

    public PacketMarkerSnapshot {
        markers = markers == null ? List.of() : List.copyOf(markers);
        if (markers.size() > MAX_MARKERS) {
            throw new IllegalArgumentException("Too many markers in snapshot: " + markers.size());
        }
    }

    public PacketMarkerSnapshot(RegistryFriendlyByteBuf buf) {
        this(readMarkers(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(markers.size());
        for (MarkerPayload marker : markers) {
            marker.write(buf);
        }
    }

    private static List<MarkerPayload> readMarkers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid marker snapshot size: " + count);
        }
        ArrayList<MarkerPayload> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            markers.add(MarkerPayload.read(buf));
        }
        return List.copyOf(markers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
