package com.mine.geometry_node.core.network.packet.marker;

import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAudience;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-facing marker snapshot. Entity runtime positions are carried only while active.
 */
public record MarkerPayload(
        MarkerAddress address,
        String typeId,
        String dimension,
        AnchorKind anchorKind,
        @Nullable UUID entityId,
        boolean active,
        Vec3 position,
        String text,
        boolean showDistance
) {
    private static final int MAX_TYPE_LENGTH = 256;
    private static final int MAX_DIMENSION_LENGTH = 256;
    private static final int MAX_TEXT_LENGTH = 2048;

    public MarkerPayload {
        if (address == null) throw new IllegalArgumentException("address must not be null");
        typeId = typeId == null ? "" : typeId;
        dimension = dimension == null ? "" : dimension;
        anchorKind = anchorKind == null ? AnchorKind.COORDINATE : anchorKind;
        position = position == null ? Vec3.ZERO : position;
        text = text == null ? "" : text;
    }

    public static MarkerPayload read(RegistryFriendlyByteBuf buf) {
        MarkerAudience audience = buf.readEnum(MarkerAudience.class);
        UUID viewerId = buf.readBoolean() ? buf.readUUID() : null;
        MarkerAddress address = new MarkerAddress(audience, viewerId, buf.readUtf(MarkerAddress.MAX_KEY_LENGTH));
        String typeId = buf.readUtf(MAX_TYPE_LENGTH);
        String dimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        AnchorKind anchorKind = buf.readEnum(AnchorKind.class);
        UUID entityId = buf.readBoolean() ? buf.readUUID() : null;
        boolean active = buf.readBoolean();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        String text = buf.readUtf(MAX_TEXT_LENGTH);
        boolean showDistance = buf.readBoolean();
        return new MarkerPayload(address, typeId, dimension, anchorKind, entityId, active, position, text, showDistance);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(address.audience());
        buf.writeBoolean(address.viewerId() != null);
        if (address.viewerId() != null) {
            buf.writeUUID(address.viewerId());
        }
        buf.writeUtf(address.key(), MarkerAddress.MAX_KEY_LENGTH);
        buf.writeUtf(typeId, MAX_TYPE_LENGTH);
        buf.writeUtf(dimension, MAX_DIMENSION_LENGTH);
        buf.writeEnum(anchorKind);
        buf.writeBoolean(entityId != null);
        if (entityId != null) {
            buf.writeUUID(entityId);
        }
        buf.writeBoolean(active);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeUtf(text, MAX_TEXT_LENGTH);
        buf.writeBoolean(showDistance);
    }

    public enum AnchorKind {
        COORDINATE,
        ENTITY
    }
}
