package com.mine.geometry_node.core.network.packet.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public record PacketSceneSnapshot(UUID requestId, String dimension, boolean success, String message,
                                  boolean canEdit, long revision, List<SceneObject> objects,
                                  Map<UUID, Long> deletedVersions) implements CustomPacketPayload {
    public static final UUID BROADCAST = new UUID(0, 0);
    public static final Type<PacketSceneSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "scene_snapshot_v5"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSceneSnapshot> STREAM_CODEC = StreamCodec.of(PacketSceneSnapshot::write, PacketSceneSnapshot::read);
    public PacketSceneSnapshot {
        Objects.requireNonNull(requestId); Identifier.parse(dimension); Objects.requireNonNull(message);
        objects = List.copyOf(objects); deletedVersions = Map.copyOf(deletedVersions);
        if (revision < 0 || objects.size() > SceneDocument.MAX_OBJECTS || deletedVersions.size() > SceneDocument.MAX_TOMBSTONES || message.length() > 256)
            throw new IllegalArgumentException("Invalid scene snapshot");
        SceneDocument.requireMetadataBudget(objects);
        Set<UUID> ids = new HashSet<>();
        for (SceneObject object : objects) {
            com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.validate(object);
            if (object.revision() > revision || !ids.add(object.id()) || deletedVersions.containsKey(object.id()))
                throw new IllegalArgumentException("Inconsistent scene object identity/revision");
        }
        for (long version : deletedVersions.values()) if (version < 0 || version > revision)
            throw new IllegalArgumentException("Invalid scene deletion version");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf buf, PacketSceneSnapshot packet) {
        buf.writeUUID(packet.requestId); buf.writeUtf(packet.dimension, 256); buf.writeBoolean(packet.success);
        buf.writeUtf(packet.message, 256); buf.writeBoolean(packet.canEdit); buf.writeLong(packet.revision);
        buf.writeVarInt(packet.objects.size()); for (SceneObject object : packet.objects) SceneWire.write(buf, object);
        buf.writeVarInt(packet.deletedVersions.size()); packet.deletedVersions.forEach((id, version) -> { buf.writeUUID(id); buf.writeLong(version); });
    }
    private static PacketSceneSnapshot read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID(); String dimension = buf.readUtf(256); boolean success = buf.readBoolean();
        String message = buf.readUtf(256); boolean canEdit = buf.readBoolean(); long revision = buf.readLong();
        int count = SceneWire.count(buf, SceneDocument.MAX_OBJECTS); List<SceneObject> objects = new ArrayList<>();
        int metadataBytes = 0;
        for (int i = 0; i < count; i++) {
            SceneObject object = SceneWire.read(buf);
            metadataBytes += object.metadata().encodedSize();
            if (metadataBytes > SceneDocument.MAX_METADATA_BYTES) throw new IllegalArgumentException("Scene metadata exceeds budget");
            objects.add(object);
        }
        int deletedCount = SceneWire.count(buf, SceneDocument.MAX_TOMBSTONES); Map<UUID, Long> deleted = new LinkedHashMap<>();
        for (int i = 0; i < deletedCount; i++) {
            UUID objectId = buf.readUUID(); long version = buf.readLong();
            if (version < 0 || version > revision || deleted.putIfAbsent(objectId, version) != null)
                throw new IllegalArgumentException("Invalid scene deletion version");
        }
        return new PacketSceneSnapshot(id, dimension, success, message, canEdit, revision, objects, deleted);
    }
}
