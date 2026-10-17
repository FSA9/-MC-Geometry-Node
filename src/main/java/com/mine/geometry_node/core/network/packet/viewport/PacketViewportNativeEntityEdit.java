package com.mine.geometry_node.core.network.packet.viewport;

import com.mine.geometry_node.core.network.packet.scene.PacketSceneEdit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;

/** Native entity transactions share the scene wire contract, but never its saved document. */
public record PacketViewportNativeEntityEdit(PacketSceneEdit edit) implements CustomPacketPayload {
    public static final Type<PacketViewportNativeEntityEdit> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "viewport_native_entity_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportNativeEntityEdit> STREAM_CODEC =
            PacketSceneEdit.STREAM_CODEC.map(PacketViewportNativeEntityEdit::new, PacketViewportNativeEntityEdit::edit);
    public PacketViewportNativeEntityEdit { Objects.requireNonNull(edit); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
