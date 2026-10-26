package com.mine.geometry_node.core.engine.system.marker;

import com.mine.geometry_node.core.engine.system.marker.type.MarkerTypeRegistry;
import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAnchor;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAudience;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerInstance;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Persistent server-wide marker storage.
 */
public final class MarkerStorage extends SavedData {
    private static final int VERSION = 2;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_MARKERS = "Markers";

    private static final Codec<MarkerStorage> CODEC = CompoundTag.CODEC.xmap(
            MarkerStorage::load,
            storage -> storage.saveToTag(new CompoundTag())
    );

    public static final SavedDataType<MarkerStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "markers"),
            MarkerStorage::new,
            CODEC
    );

    private final Map<MarkerAddress, MarkerInstance> markers = new LinkedHashMap<>();
    private final Thread ownerThread = Thread.currentThread();
    private boolean metadataUpdating;

    private void requireMutation() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Marker mutations require the owner thread");
        if (metadataUpdating) throw new IllegalStateException("Reentrant marker mutation during metadata update");
    }

    public static MarkerStorage get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public synchronized Collection<MarkerInstance> all() {
        return List.copyOf(markers.values());
    }

    public synchronized Optional<MarkerInstance> get(MarkerAddress address) {
        return Optional.ofNullable(markers.get(address));
    }

    public synchronized Optional<MarkerInstance> put(MarkerInstance marker) {
        requireMutation();
        MarkerInstance current = markers.get(marker.address());
        // Updating presentation or anchor does not replace the metadata owner.
        if (current != null) marker = marker.withMetadata(current.metadata(), current.incarnation(), current.metadataRevision());
        else marker = marker.withMetadata(marker.metadata(), UUID.randomUUID(), marker.metadataRevision());
        MarkerInstance previous = markers.put(marker.address(), marker);
        setDirty();
        return Optional.ofNullable(previous);
    }

    public synchronized Optional<MarkerInstance> remove(MarkerAddress address) {
        requireMutation();
        MarkerInstance removed = markers.remove(address);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public synchronized List<MarkerInstance> removeExpired(long gameTime) {
        requireMutation();
        ArrayList<MarkerInstance> removed = new ArrayList<>();
        markers.values().removeIf(marker -> {
            if (!marker.isExpired(gameTime)) {
                return false;
            }
            removed.add(marker);
            return true;
        });
        if (!removed.isEmpty()) {
            setDirty();
        }
        return List.copyOf(removed);
    }

    public synchronized boolean updateMetadata(MarkerAddress address, UUID incarnation,
                                                UnaryOperator<OwnerMetadata> change) {
        requireMutation();
        MarkerInstance current = get(address).orElseThrow(() -> new IllegalStateException("Marker no longer exists"));
        if (!current.incarnation().equals(incarnation)) throw new IllegalStateException("Stale marker metadata target");
        OwnerMetadata next;
        metadataUpdating = true;
        try { next = Objects.requireNonNull(change.apply(current.metadata())); }
        finally { metadataUpdating = false; }
        if (markers.get(address) != current) throw new IllegalStateException("Marker changed during metadata update");
        if (current.metadata().equals(next)) return false;
        markers.put(address, current.withMetadata(next, incarnation, Math.incrementExact(current.metadataRevision())));
        setDirty();
        return true;
    }

    public CompoundTag saveToTag(CompoundTag root) {
        root.putInt(TAG_VERSION, VERSION);
        ListTag list = new ListTag();
        synchronized (this) {
            for (MarkerInstance marker : markers.values()) {
                list.add(writeMarker(marker));
            }
        }
        root.put(TAG_MARKERS, list);
        return root;
    }

    public static MarkerStorage load(CompoundTag root) {
        MarkerStorage storage = new MarkerStorage();
        ListTag list = root.getListOrEmpty(TAG_MARKERS);
        for (int i = 0; i < list.size(); i++) {
            readMarker(list.getCompoundOrEmpty(i)).ifPresent(marker -> storage.markers.put(marker.address(), marker));
        }
        return storage;
    }

    private static CompoundTag writeMarker(MarkerInstance marker) {
        CompoundTag tag = new CompoundTag();
        MarkerAddress address = marker.address();
        tag.putString("Audience", address.audience().name());
        if (address.viewerId() != null) {
            tag.putString("Viewer", address.viewerId().toString());
        }
        tag.putString("Key", address.key());
        tag.putString("Type", marker.typeId());
        tag.putString("Text", marker.text());
        tag.putBoolean("ShowDistance", marker.showDistance());
        tag.putLong("CreatedGameTime", marker.createdGameTime());
        tag.putLong("ExpiresAtGameTime", marker.expiresAtGameTime());
        tag.put("Metadata", marker.metadata().save());
        tag.putString("Incarnation", marker.incarnation().toString());
        tag.putLong("MetadataRevision", marker.metadataRevision());

        MarkerAnchor anchor = marker.anchor();
        tag.putString("Dimension", anchor.dimension().identifier().toString());
        if (anchor instanceof MarkerAnchor.Coordinate coordinate) {
            tag.putString("Anchor", "coordinate");
            tag.putDouble("X", coordinate.position().x);
            tag.putDouble("Y", coordinate.position().y);
            tag.putDouble("Z", coordinate.position().z);
        } else if (anchor instanceof MarkerAnchor.Entity entity) {
            tag.putString("Anchor", "entity");
            tag.putString("Entity", entity.entityId().toString());
        }
        return tag;
    }

    private static Optional<MarkerInstance> readMarker(CompoundTag tag) {
        try {
            MarkerAudience audience = MarkerAudience.valueOf(tag.getStringOr("Audience", ""));
            UUID viewer = audience == MarkerAudience.SELF
                    ? UUID.fromString(tag.getStringOr("Viewer", ""))
                    : null;
            MarkerAddress address = new MarkerAddress(audience, viewer, tag.getStringOr("Key", ""));
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.parse(tag.getStringOr("Dimension", ""))
            );

            MarkerAnchor anchor;
            if ("entity".equals(tag.getStringOr("Anchor", ""))) {
                anchor = new MarkerAnchor.Entity(
                        dimension,
                        UUID.fromString(tag.getStringOr("Entity", ""))
                );
            } else {
                anchor = new MarkerAnchor.Coordinate(
                        dimension,
                        new Vec3(
                                tag.getDoubleOr("X", 0.0D),
                                tag.getDoubleOr("Y", 0.0D),
                                tag.getDoubleOr("Z", 0.0D)
                        )
                );
            }

            return Optional.of(new MarkerInstance(
                    address,
                    tag.getStringOr("Type", MarkerTypeRegistry.DEFAULT_TYPE_ID),
                    anchor,
                    tag.getStringOr("Text", ""),
                    tag.getBooleanOr("ShowDistance", true),
                    tag.getLongOr("CreatedGameTime", 0L),
                    tag.getLongOr("ExpiresAtGameTime", MarkerInstance.NEVER_EXPIRES),
                    OwnerMetadata.load(tag.getCompoundOrEmpty("Metadata")),
                    tag.contains("Incarnation") ? UUID.fromString(tag.getStringOr("Incarnation", "")) : UUID.randomUUID(),
                    tag.getLongOr("MetadataRevision", 0)
            ));
        } catch (IllegalArgumentException exception) {
            GeometryNode.LOGGER.warn("Skipping invalid persisted marker: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
