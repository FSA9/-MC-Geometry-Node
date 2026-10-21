package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.metadata.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.RuntimeMetadataTargets;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import com.mine.geometry_node.core.engine.resource.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntries;
import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

final class DebugSourceStore {
    private final Map<ServerLevel, LevelSources> levels = new IdentityHashMap<>();
    private final int idleExpiryTicks;
    private final BiFunction<ServerLevel, UUID, Vec3> anchorPositions;

    DebugSourceStore(int idleExpiryTicks) {
        this(idleExpiryTicks, (level, uuid) -> {
            if (!ResourceResidency.isEntityResident(level, uuid)) return null;
            var anchor = level.getEntity(uuid);
            return anchor == null ? null : anchor.position();
        });
    }

    DebugSourceStore(int idleExpiryTicks, BiFunction<ServerLevel, UUID, Vec3> anchorPositions) {
        this.idleExpiryTicks = idleExpiryTicks;
        this.anchorPositions = anchorPositions;
    }

    boolean replace(ServerLevel level, DebugSourceId sourceId, List<GeometryDebugElement> meshes,
                    long seenTick, long expiresAt, long signature) {
        LevelSources levelSources = levels.computeIfAbsent(level, ignored -> new LevelSources());
        Source current = levelSources.sources.get(sourceId);
        if (current != null && current.isExpired(seenTick, idleExpiryTicks)) current = null;
        if (current != null && current.signature == signature && current.expiresAt == expiresAt) {
            current.lastSeenTick = seenTick;
            refreshAnchors(level, current);
            return false;
        }
        Source next = new Source(current == null ? new RuntimeMetadata() : current.customMetadata, List.copyOf(meshes), seenTick, signature, expiresAt,
                sourceId.channel() == DebugRenderChannel.GEOMETRY
                        ? meshes.stream().map(mesh -> WorldObjectEntries.geometry(level, sourceId, mesh)).toList()
                        : List.of(), current == null ? Map.of() : current.lastAnchorPositions);
        refreshAnchors(level, next);
        levelSources.sources.put(sourceId, next);
        levelSources.publish();
        return true;
    }

    boolean remove(ServerLevel level, DebugSourceId sourceId) {
        LevelSources levelSources = levels.get(level);
        if (levelSources == null || levelSources.sources.remove(sourceId) == null) return false;
        if (levelSources.sources.isEmpty()) levels.remove(level);
        else levelSources.publish();
        return true;
    }

    boolean removeGraphResources(GraphResourceRelease release) {
        boolean changed = false;
        var levelsIterator = levels.entrySet().iterator();
        while (levelsIterator.hasNext()) {
            LevelSources levelSources = levelsIterator.next().getValue();
            boolean levelChanged = levelSources.sources.entrySet().removeIf(entry ->
                    entry.getKey().owner() instanceof DebugSourceId.Owner.Graph graph
                            && (release.matches(graph.resourceId())
                            // The rendered anchor need not be encoded as the resource's target.
                            || release instanceof GraphResourceRelease.Entity entity
                            && entity.dimension().equals(graph.resourceId().scope().dimension())
                            && entry.getValue().meshes.stream().anyMatch(mesh ->
                                    entity.entityId().equals(mesh.anchorUuid()))));
            changed |= levelChanged;
            if (levelSources.sources.isEmpty()) levelsIterator.remove();
            else if (levelChanged) levelSources.publish();
        }
        return changed;
    }

    boolean pruneExpired(ServerLevel level, long tick) {
        LevelSources levelSources = levels.get(level);
        if (levelSources == null) return false;
        // Missing anchors are dormant. Permanent removal comes through lifecycle releases.
        boolean changed = levelSources.sources.values().removeIf(source -> source.isExpired(tick, idleExpiryTicks));
        if (levelSources.sources.isEmpty()) levels.remove(level);
        else if (changed) levelSources.publish();
        return changed;
    }

    List<VisibleSource> sources(ServerLevel level) {
        LevelSources levelSources = levels.get(level);
        return levelSources != null ? levelSources.snapshot : List.of();
    }

    void visitWorldQuery(ServerLevel level, long tick,
            java.util.function.Predicate<WorldObjectEntry> visitor) {
        LevelSources sources = levels.get(level);
        if (sources == null) return;
        for (Source source : sources.sources.values()) {
            if (source.isExpired(tick, idleExpiryTicks)) continue;
            refreshAnchors(level, source);
            for (int i = 0; i < source.metadata.size(); i++) {
                var entry = source.metadata.get(i);
                if (entry == null) throw new IllegalStateException("Geometry metadata is unavailable");
                UUID anchor = source.meshes.get(i).anchorUuid();
                Vec3 offset = anchor == null ? Vec3.ZERO : source.lastAnchorPositions.get(anchor);
                // A never-observed anchor has no valid world pose yet. Dormant known poses remain queryable.
                if (offset == null) continue;
                var references = new HashMap<>(entry.references());
                references.put("coordinateSpace", "world");
                references.put("anchorPose", "resolved");
                if (!visitor.test(new WorldObjectEntry(entry.key(), entry.provider(), entry.type(), entry.name(),
                        entry.dimension(), entry.position().add(offset), entry.bounds().move(offset),
                        source.customMetadata.tagSnapshot(), entry.source(), entry.lifetime(),
                        entry.sceneObjectId(), entry.entityId(), references))) return;
            }
        }
    }

    void refreshAnchors(ServerLevel level) {
        LevelSources sources = levels.get(level);
        if (sources != null) sources.sources.values().forEach(source -> refreshAnchors(level, source));
    }

    private void refreshAnchors(ServerLevel level, Source source) {
        source.lastAnchorPositions.replaceAll((uuid, lastPosition) -> {
            Vec3 position = anchorPositions.apply(level, uuid);
            return position == null ? lastPosition : position;
        });
    }

    MetadataCapabilities.Access metadata(ServerLevel level, DebugSourceId id) {
        RuntimeMetadataTargets.requireThread(level.getServer());
        LevelSources sources = levels.get(level);
        Source source = sources == null ? null : sources.sources.get(id);
        if (source == null || !live(level, source)) throw new IllegalStateException("Geometry no longer exists");
        RuntimeMetadata metadata = source.customMetadata;
        return metadata.access(GraphMetadataValuePolicy.of(level.getServer().registryAccess()), () -> {
            RuntimeMetadataTargets.requireThread(level.getServer());
            LevelSources currentSources = levels.get(level);
            Source current = currentSources == null ? null : currentSources.sources.get(id);
            if (current == null || current.customMetadata != metadata || !live(level, current))
                throw new IllegalStateException("Geometry metadata handle is stale");
        });
    }

    long metadataRevision(ServerLevel level, DebugSourceId id) {
        metadata(level, id); // Checks the owner thread and lifetime.
        return levels.get(level).sources.get(id).customMetadata.revision();
    }

    private boolean live(ServerLevel level, Source source) {
        return !source.isExpired(level.getGameTime(), idleExpiryTicks);
    }

    boolean removeLevel(ServerLevel level) {
        return levels.remove(level) != null;
    }

    boolean isEmpty() {
        return levels.isEmpty();
    }

    record VisibleSource(DebugSourceId id, List<GeometryDebugElement> meshes, List<AABB> bounds) {
    }

    /** Vertices already contain the baked transform; primitive shapes retain their rotation. */
    static AABB bounds(GeometryDebugElement mesh) {
        float[] vertices = mesh.vertices();
        if (vertices.length < 3) {
            return WorldObjectEntries.rotatedBounds(mesh.center(), mesh.size(), mesh.rotation());
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i + 2 < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(mesh.center());
    }

    private static final class LevelSources {
        private final Map<DebugSourceId, Source> sources = new HashMap<>();
        private List<VisibleSource> snapshot = List.of();

        private void publish() {
            snapshot = sources.entrySet().stream()
                    .map(entry -> new VisibleSource(entry.getKey(), entry.getValue().meshes, entry.getValue().bounds))
                    .toList();
        }
    }

    private static final class Source {
        private final RuntimeMetadata customMetadata;
        private final List<GeometryDebugElement> meshes;
        private final List<AABB> bounds;
        private long lastSeenTick;
        private final long signature;
        private final long expiresAt;
        private final List<WorldObjectEntry> metadata;
        private final Map<UUID, Vec3> lastAnchorPositions = new HashMap<>();

        private Source(RuntimeMetadata customMetadata, List<GeometryDebugElement> meshes, long lastSeenTick, long signature, long expiresAt,
                       List<WorldObjectEntry> metadata, Map<UUID, Vec3> previousAnchorPositions) {
            this.customMetadata = customMetadata;
            this.metadata = metadata;
            this.meshes = meshes;
            this.bounds = meshes.stream().map(DebugSourceStore::bounds).toList();
            for (GeometryDebugElement mesh : meshes) {
                if (mesh.anchorUuid() != null) {
                    lastAnchorPositions.put(mesh.anchorUuid(), previousAnchorPositions.get(mesh.anchorUuid()));
                }
            }
            this.lastSeenTick = lastSeenTick;
            this.signature = signature;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long tick, int idleExpiryTicks) {
            if (expiresAt == Long.MAX_VALUE) return false;
            if (expiresAt > 0L) return tick >= expiresAt;
            return tick - lastSeenTick > idleExpiryTicks;
        }

    }
}
