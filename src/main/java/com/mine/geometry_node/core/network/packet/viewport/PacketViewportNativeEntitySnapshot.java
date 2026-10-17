package com.mine.geometry_node.core.network.packet.viewport;

import com.mine.geometry_node.core.network.packet.scene.PacketSceneSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

public record PacketViewportNativeEntitySnapshot(PacketSceneSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<PacketViewportNativeEntitySnapshot> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "viewport_native_entity_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportNativeEntitySnapshot> STREAM_CODEC =
            PacketSceneSnapshot.STREAM_CODEC.map(PacketViewportNativeEntitySnapshot::new, PacketViewportNativeEntitySnapshot::snapshot);
    public PacketViewportNativeEntitySnapshot { Objects.requireNonNull(snapshot); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
