package com.mine.geometry_node.core.network.packet.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Empty changes request a fresh dimension snapshot. IDs make mutations safely retryable. */
public record PacketSceneEdit(UUID requestId, String dimension, List<SceneChange> changes) implements CustomPacketPayload {
    public static final Type<PacketSceneEdit> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "scene_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSceneEdit> STREAM_CODEC = StreamCodec.of(PacketSceneEdit::write, PacketSceneEdit::read);
    public PacketSceneEdit {
        Objects.requireNonNull(requestId); Identifier.parse(dimension); changes = List.copyOf(changes);
        if (changes.size() > SceneDocument.MAX_OBJECTS) throw new IllegalArgumentException("Scene batch too large");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf buf, PacketSceneEdit packet) {
        buf.writeUUID(packet.requestId); buf.writeUtf(packet.dimension, 256); buf.writeVarInt(packet.changes.size());
        for (SceneChange change : packet.changes) {
            buf.writeUUID(change.id()); buf.writeLong(change.expectedRevision()); buf.writeBoolean(change.replacement() != null);
            if (change.replacement() != null) SceneWire.write(buf, change.replacement());
        }
    }
    private static PacketSceneEdit read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID(); String dimension = buf.readUtf(256);
        int count = SceneWire.count(buf, SceneDocument.MAX_OBJECTS); List<SceneChange> changes = new ArrayList<>();
        for (int i = 0; i < count; i++) changes.add(new SceneChange(buf.readUUID(), buf.readLong(), buf.readBoolean() ? SceneWire.read(buf) : null));
        return new PacketSceneEdit(id, dimension, changes);
    }
}
