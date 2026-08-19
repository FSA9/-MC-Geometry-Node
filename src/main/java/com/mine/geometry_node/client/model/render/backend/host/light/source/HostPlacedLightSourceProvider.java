package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Render-thread owned catalog for placed block lights.  The catalog stores no
 * world references; callers submit complete immutable source values and workers
 * consume snapshots produced by {@link #snapshot()}.
 */
public final class HostPlacedLightSourceProvider {
    private final ModelDimensionId dimension;
    private final Map<HostLightSectionKey, Map<HostLightSourceId, HostLightSource>> sections = new HashMap<>();
    private long revision;

    public HostPlacedLightSourceProvider(ModelDimensionId dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
    }

    public synchronized ModelDimensionId dimension() { return dimension; }
    public synchronized long revision() { return revision; }

    /** Replaces one source in its section, incrementing the catalog revision. */
    public synchronized void upsert(HostLightSource source) {
        requirePlaced(source);
        HostLightSectionKey key = source.id().sectionKey();
        Map<HostLightSourceId, HostLightSource> values = sections.computeIfAbsent(key, ignored -> new HashMap<>());
        if (source.equals(values.put(source.id(), source))) return;
        revision = nextRevision(revision);
    }

    /** Atomically replaces one fully scanned section and advances revision only when content changed. */
    public synchronized void replaceSection(HostLightSectionKey key, List<HostLightSource> sources) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sources, "sources");
        requireDimension(key.dimension());
        Map<HostLightSourceId, HostLightSource> replacement = new HashMap<>();
        for (HostLightSource source : sources) {
            requirePlaced(source);
            if (!key.equals(source.id().sectionKey())) {
                throw new IllegalArgumentException("replacement source belongs to another section");
            }
            if (replacement.put(source.id(), source) != null) {
                throw new IllegalArgumentException("duplicate source ID in section replacement");
            }
        }
        Map<HostLightSourceId, HostLightSource> previous = sections.get(key);
        if (replacement.equals(previous == null ? Map.of() : previous)) return;
        if (replacement.isEmpty()) sections.remove(key);
        else sections.put(key, replacement);
        revision = nextRevision(revision);
    }

    /** Removes a source by stable section/local identity. */
    public synchronized boolean remove(HostLightSectionKey key, String localIdentity) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(localIdentity, "localIdentity");
        requireDimension(key.dimension());
        Map<HostLightSourceId, HostLightSource> values = sections.get(key);
        if (values == null) return false;
        boolean removed = values.entrySet().removeIf(entry ->
                entry.getKey().localIdentity().equals(localIdentity));
        if (!removed) return false;
        if (values.isEmpty()) sections.remove(key);
        revision = nextRevision(revision);
        return true;
    }

    /** Drops all sources in an unloaded section. */
    public synchronized boolean unload(HostLightSectionKey key) {
        Objects.requireNonNull(key, "key");
        requireDimension(key.dimension());
        if (sections.remove(key) == null) return false;
        revision = nextRevision(revision);
        return true;
    }

    public synchronized HostLightSourceSnapshot snapshot() {
        ArrayList<HostLightSource> values = new ArrayList<>();
        sections.values().forEach(map -> values.addAll(map.values()));
        return new HostLightSourceSnapshot(dimension, revision, List.copyOf(values));
    }

    private void requirePlaced(HostLightSource source) {
        Objects.requireNonNull(source, "source");
        requireDimension(source.id().dimension());
        if (source.id().kind() != HostLightSourceKind.PLACED_BLOCK) {
            throw new IllegalArgumentException("placed provider accepts only PLACED_BLOCK sources");
        }
    }

    private void requireDimension(ModelDimensionId value) {
        if (!dimension.equals(value)) throw new IllegalArgumentException("source dimension does not match provider");
    }

    private static long nextRevision(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("source revision exhausted");
        return value + 1;
    }
}
