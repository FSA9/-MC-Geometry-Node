package com.mine.geometry_node.core.network.packet.marker;

import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorCodec;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAudience;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** Client snapshot; the shared anchor is authoritative, position is the server's latest resolved sample. */
public record MarkerPayload(MarkerAddress address, String typeId, SpatialAnchor anchor,
                            Vec3 worldOffset, Vec3 localOffset, boolean active, Vec3 position, String text, boolean showDistance) {
    private static final int MAX_TYPE_LENGTH = 256;
    private static final int MAX_TEXT_LENGTH = 2048;
    private static final SpatialAnchorCodec ANCHOR_CODEC = new SpatialAnchorCodec();

    public MarkerPayload {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(anchor.dimension(), "Marker snapshots require a concrete dimension");
        Objects.requireNonNull(worldOffset, "worldOffset");
        Objects.requireNonNull(localOffset, "localOffset");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(worldOffset.x) || !Double.isFinite(worldOffset.y) || !Double.isFinite(worldOffset.z)
                || !Double.isFinite(localOffset.x) || !Double.isFinite(localOffset.y) || !Double.isFinite(localOffset.z)
                || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Nonfinite marker position or offset");
        }
        typeId = typeId == null ? "" : typeId;
        text = text == null ? "" : text;
    }

    public String dimension() { return anchor.dimension().identifier().toString(); }
    public UUID entityId() { return anchor.entityId(); }
    public SpatialAnchor.Origin origin() { return anchor.origin(); }

    public static MarkerPayload read(RegistryFriendlyByteBuf buf) {
        MarkerAudience audience = buf.readEnum(MarkerAudience.class);
        UUID viewerId = buf.readBoolean() ? buf.readUUID() : null;
        MarkerAddress address = new MarkerAddress(audience, viewerId, buf.readUtf(MarkerAddress.MAX_KEY_LENGTH));
        String typeId = buf.readUtf(MAX_TYPE_LENGTH);
        SpatialAnchor anchor = ANCHOR_CODEC.deserialize(buf.readNbt());
        Vec3 worldOffset = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 localOffset = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        boolean active = buf.readBoolean();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        String text = buf.readUtf(MAX_TEXT_LENGTH);
        boolean showDistance = buf.readBoolean();
        return new MarkerPayload(address, typeId, anchor, worldOffset, localOffset, active, position, text, showDistance);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(address.audience());
        buf.writeBoolean(address.viewerId() != null);
        if (address.viewerId() != null) buf.writeUUID(address.viewerId());
        buf.writeUtf(address.key(), MarkerAddress.MAX_KEY_LENGTH);
        buf.writeUtf(typeId, MAX_TYPE_LENGTH);
        buf.writeNbt(ANCHOR_CODEC.serialize(anchor));
        buf.writeDouble(worldOffset.x);
        buf.writeDouble(worldOffset.y);
        buf.writeDouble(worldOffset.z);
        buf.writeDouble(localOffset.x);
        buf.writeDouble(localOffset.y);
        buf.writeDouble(localOffset.z);
        buf.writeBoolean(active);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeUtf(text, MAX_TEXT_LENGTH);
        buf.writeBoolean(showDistance);
    }
}
