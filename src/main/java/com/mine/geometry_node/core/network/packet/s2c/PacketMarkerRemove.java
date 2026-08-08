package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAudience;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PacketMarkerRemove(MarkerAddress address) implements CustomPacketPayload {
    public static final Type<PacketMarkerRemove> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "marker_remove")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketMarkerRemove> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketMarkerRemove::new
    );

    public PacketMarkerRemove(RegistryFriendlyByteBuf buf) {
        this(readAddress(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(address.audience());
        buf.writeBoolean(address.viewerId() != null);
        if (address.viewerId() != null) {
            buf.writeUUID(address.viewerId());
        }
        buf.writeUtf(address.key(), MarkerAddress.MAX_KEY_LENGTH);
    }

    private static MarkerAddress readAddress(RegistryFriendlyByteBuf buf) {
        MarkerAudience audience = buf.readEnum(MarkerAudience.class);
        UUID viewer = buf.readBoolean() ? buf.readUUID() : null;
        return new MarkerAddress(audience, viewer, buf.readUtf(MarkerAddress.MAX_KEY_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
