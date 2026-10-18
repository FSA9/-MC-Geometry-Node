package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectPage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

public record PacketWorldObjectPage(WorldObjectPage page) implements CustomPacketPayload {
    public static final Type<PacketWorldObjectPage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectPage> STREAM_CODEC = StreamCodec.of(
            (b, p) -> WorldObjectPacketCodecs.page(b, p.page), b -> new PacketWorldObjectPage(WorldObjectPacketCodecs.page(b)));
    public PacketWorldObjectPage { Objects.requireNonNull(page); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
