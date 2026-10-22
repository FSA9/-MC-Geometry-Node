package com.mine.geometry_node.core.engine.system.entityeditor;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectDataValues;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.LongSupplier;

/** Server-thread only. One instance per connection; bounded baselines never retain a loaded entity. */
public final class EntityEditorSessions {
    static final int MAX_SNAPSHOTS = 4;
    static final long LEASE_NANOS = 300_000_000_000L;
    public record Snapshot(String target, WeakReference<Object> incarnation, Map<String, Object> values, long created) {}
    public record Read(Map<String, Object> values, String revision) {}
    private final LinkedHashMap<String, Snapshot> snapshots = new LinkedHashMap<>();
    private final LongSupplier clock;
    private long lastRequest = Long.MIN_VALUE;
    public EntityEditorSessions() { this(System::nanoTime); }
    EntityEditorSessions(LongSupplier clock) { this.clock = clock; }

    public boolean acquire() {
        long now = clock.getAsLong();
        if (lastRequest != Long.MIN_VALUE && now - lastRequest < 100_000_000L) return false;
        lastRequest = now; return true;
    }

    public Read remember(String target, Object incarnation, Map<String, Object> values) {
        long now = clock.getAsLong();
        snapshots.values().removeIf(snapshot -> now - snapshot.created() >= LEASE_NANOS);
        while (snapshots.size() >= MAX_SNAPSHOTS) snapshots.remove(snapshots.keySet().iterator().next());
        String revision = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> detached = WorldObjectDataValues.snapshot(values);
        snapshots.put(revision, new Snapshot(target, new WeakReference<>(Objects.requireNonNull(incarnation)), detached, now));
        return new Read(WorldObjectDataValues.snapshot(detached), revision);
    }

    public Snapshot find(String revision, String target, Object incarnation) {
        Snapshot snapshot = snapshots.get(revision);
        if (snapshot == null || !snapshot.target().equals(target) || snapshot.incarnation().get() != incarnation
                || clock.getAsLong() - snapshot.created() >= LEASE_NANOS) return null;
        return new Snapshot(snapshot.target(), snapshot.incarnation(), WorldObjectDataValues.snapshot(snapshot.values()), snapshot.created());
    }
    public void consume(String revision) { snapshots.remove(revision); }
}
