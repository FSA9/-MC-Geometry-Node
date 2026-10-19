package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.metadata.RuntimeMetadata;
import com.mine.geometry_node.core.engine.metadata.RuntimeMetadataTargets;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceRelease;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class DebugSourceStore {
    private final Map<ServerLevel, LevelSources> levels = new IdentityHashMap<>();
    private final int idleExpiryTicks;

    DebugSourceStore(int idleExpiryTicks) {
        this.idleExpiryTicks = idleExpiryTicks;
    }

    boolean replace(ServerLevel level, DebugSourceId sourceId, List<GeometryDebugElement> meshes,
                    long seenTick, long expiresAt, long signature) {
        LevelSources levelSources = levels.computeIfAbsent(level, ignored -> new LevelSources());
        Source current = levelSources.sources.get(sourceId);
        if (current != null && !live(level, current)) current = null;
        if (current != null && current.signature == signature && current.expiresAt == expiresAt) {
            current.lastSeenTick = seenTick;
            return false;
        }
        levelSources.sources.put(sourceId, new Source(current == null ? new RuntimeMetadata() : current.customMetadata, List.copyOf(meshes), seenTick, signature, expiresAt,
                sourceId.channel() == DebugRenderChannel.GEOMETRY
                        ? meshes.stream().map(mesh -> com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntries.geometry(level, sourceId, mesh)).toList()
                        : List.of()));
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
            boolean levelChanged = levelSources.sources.keySet().removeIf(sourceId ->
                    sourceId.owner() instanceof DebugSourceId.Owner.Graph graph
                            && release.matches(graph.resourceId()));
            changed |= levelChanged;
            if (levelSources.sources.isEmpty()) levelsIterator.remove();
            else if (levelChanged) levelSources.publish();
        }
        return changed;
    }

    boolean pruneExpired(ServerLevel level, long tick) {
        LevelSources levelSources = levels.get(level);
        if (levelSources == null) return false;
        boolean changed = levelSources.sources.values().removeIf(source -> source.isExpired(tick, idleExpiryTicks)
                || source.meshes.stream().anyMatch(mesh -> {
                    if (mesh.anchorUuid() == null) return false;
                    var anchor = level.getEntity(mesh.anchorUuid());
                    return anchor == null || !anchor.isAlive();
                }));
        if (levelSources.sources.isEmpty()) levels.remove(level);
        else if (changed) levelSources.publish();
        return changed;
    }

    boolean hasTransientExpired(ServerLevel level, long tick) {
        LevelSources levelSources = levels.get(level);
        return levelSources != null
                && levelSources.sources.values().stream().anyMatch(source -> source.isTransientExpired(tick));
    }

    List<VisibleSource> sources(ServerLevel level) {
        LevelSources levelSources = levels.get(level);
        return levelSources != null ? levelSources.snapshot : List.of();
    }

    void visitWorldQuery(ServerLevel level,
            java.util.function.Predicate<com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry> visitor) {
        LevelSources sources = levels.get(level);
        if (sources == null) return;
        for (Source source : sources.sources.values()) {
            if (source.isExpired(level.getGameTime(), idleExpiryTicks)) continue;
            for (var entry : source.metadata) {
                if (entry == null) throw new IllegalStateException("Geometry metadata is unavailable");
                if (!visitor.test(com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntries.withTags(
                        entry, source.customMetadata.tagSnapshot()))) return;
            }
        }
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
        return !source.isExpired(level.getGameTime(), idleExpiryTicks)
                && source.meshes.stream().noneMatch(mesh -> mesh.anchorUuid() != null
                    && (level.getEntity(mesh.anchorUuid()) == null || !level.getEntity(mesh.anchorUuid()).isAlive()));
    }

    boolean removeLevel(ServerLevel level) {
        return levels.remove(level) != null;
    }

    boolean isEmpty() {
        return levels.isEmpty();
    }

    record VisibleSource(DebugSourceId id, List<GeometryDebugElement> meshes) {
    }

    private static final class LevelSources {
        private final Map<DebugSourceId, Source> sources = new HashMap<>();
        private List<VisibleSource> snapshot = List.of();

        private void publish() {
            snapshot = sources.entrySet().stream()
                    .map(entry -> new VisibleSource(entry.getKey(), entry.getValue().meshes))
                    .toList();
        }
    }

    private static final class Source {
        private final RuntimeMetadata customMetadata;
        private final List<GeometryDebugElement> meshes;
        private long lastSeenTick;
        private final long signature;
        private final long expiresAt;
        private final List<com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry> metadata;

        private Source(RuntimeMetadata customMetadata, List<GeometryDebugElement> meshes, long lastSeenTick, long signature, long expiresAt,
                       List<com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry> metadata) {
            this.customMetadata = customMetadata;
            this.metadata = metadata;
            this.meshes = meshes;
            this.lastSeenTick = lastSeenTick;
            this.signature = signature;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long tick, int idleExpiryTicks) {
            if (expiresAt == Long.MAX_VALUE) return false;
            if (expiresAt > 0L) return tick >= expiresAt;
            return tick - lastSeenTick > idleExpiryTicks;
        }

        private boolean isTransientExpired(long tick) {
            return expiresAt > 0L && expiresAt != Long.MAX_VALUE && tick >= expiresAt;
        }
    }
}
