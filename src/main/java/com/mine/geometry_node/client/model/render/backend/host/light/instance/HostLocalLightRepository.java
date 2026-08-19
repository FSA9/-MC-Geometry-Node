package com.mine.geometry_node.client.model.render.backend.host.light.instance;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLocalLightField;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightField;
import com.mine.geometry_node.client.model.render.backend.host.light.diagnostics.HostLocalLightDiagnostics;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Render-thread owner of active, target and retiring instance-local light fields. */
public final class HostLocalLightRepository implements AutoCloseable {
    private final RenderThreadDispatcher renderThread;
    private final Retirement retirement;
    private final Map<ModelInstanceId, Entry> entries = new HashMap<>();
    private long generation;
    private boolean closed;
    private long published, staleCompletions, cancelled, transientFailures, terminalUnsupported, budgetRejected;
    private int retiringFields;
    private long retiringBytes;

    public HostLocalLightRepository(RenderThreadDispatcher renderThread, Retirement retirement) {
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.retirement = Objects.requireNonNull(retirement, "retirement");
    }

    public Target beginTarget(HostLightFieldIdentity identity) {
        renderThread.assertRenderThread();
        if (closed) throw new IllegalStateException("local light repository is closed");
        Entry entry = entries.computeIfAbsent(identity.instanceId(), ignored -> new Entry());
        cancelTarget(entry);
        Target target = new Target(++generation, identity, new AtomicBoolean());
        entry.target = target;
        return target;
    }

    /** Publishes atomically. A stale completion is rejected and owns no repository state. */
    public boolean publish(Target target, HostLocalLightField field) {
        renderThread.assertRenderThread();
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(field, "field");
        Entry entry = entries.get(target.identity().instanceId());
        if (entry == null || entry.target != target || target.cancelled()) {
            staleCompletions++;
            retire(field);
            return false;
        }
        if (!target.identity().equals(field.identity())) {
            entry.target = null;
            target.cancel();
            staleCompletions++;
            retire(field);
            removeEmpty(target.identity().instanceId(), entry);
            return false;
        }
        entry.target = null;
        HostLocalLightField previous = entry.active;
        if (previous instanceof HostScalarLightField active
                && field instanceof HostScalarLightField replacement
                && active.identity().sameProjectionDomain(replacement.identity())
                && active.sameSamples(replacement)) {
            retire(field);
            return true;
        }
        entry.active = field;
        entry.identity = target.identity();
        published++;
        if (previous != null) retire(previous);
        return true;
    }

    /** A transient solve failure leaves the complete active field untouched. */
    public boolean fail(Target target, FailureKind kind) {
        renderThread.assertRenderThread();
        Objects.requireNonNull(kind, "kind");
        Entry entry = entries.get(target.identity().instanceId());
        if (entry == null || entry.target != target || target.cancelled()) return false;
        entry.target = null;
        switch (kind) {
            case TRANSIENT -> transientFailures++;
            case TERMINAL_UNSUPPORTED -> terminalUnsupported++;
            case BUDGET_REJECTED -> budgetRejected++;
        }
        removeEmpty(target.identity().instanceId(), entry);
        return true;
    }

    public HostLocalLightField active(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        Entry entry = entries.get(instanceId);
        return entry == null ? null : entry.active;
    }

    public HostLightFieldIdentity activeIdentity(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        Entry entry = entries.get(instanceId);
        return entry == null ? null : entry.identity;
    }

    public boolean targeting(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        Entry entry = entries.get(Objects.requireNonNull(instanceId, "instanceId"));
        return entry != null && entry.target != null;
    }

    public void remove(ModelInstanceId instanceId) {
        renderThread.assertRenderThread();
        Entry entry = entries.remove(instanceId);
        if (entry == null) return;
        cancelTarget(entry);
        if (entry.active != null) retire(entry.active);
    }

    /** World unload/reset invalidates all generations and releases every instance-owned field. */
    @Override public void close() {
        renderThread.assertRenderThread();
        if (closed) return;
        closed = true;
        generation++;
        var values = entries.values().toArray(Entry[]::new);
        entries.clear();
        for (Entry entry : values) {
            cancelTarget(entry);
            if (entry.active != null) retire(entry.active);
        }
    }

    public HostLocalLightDiagnostics diagnostics() {
        renderThread.assertRenderThread();
        int activeFields = 0, targetFields = 0;
        long activeBytes = 0;
        for (Entry entry : entries.values()) {
            if (entry.active != null) {
                activeFields++;
                activeBytes = saturatedAdd(activeBytes, entry.active.residentBytes());
            }
            if (entry.target != null) targetFields++;
        }
        return new HostLocalLightDiagnostics(entries.size(), activeFields, targetFields,
                retiringFields, activeBytes, retiringBytes, published, staleCompletions,
                cancelled, transientFailures, terminalUnsupported, budgetRejected);
    }

    private void cancelTarget(Entry entry) {
        if (entry.target != null && entry.target.cancel()) cancelled++;
        entry.target = null;
    }

    private void retire(HostLocalLightField field) {
        retiringFields++;
        retiringBytes = saturatedAdd(retiringBytes, field.residentBytes());
        AtomicBoolean completed = new AtomicBoolean();
        Runnable completion = () -> {
            if (!completed.compareAndSet(false, true)) return;
            renderThread.execute(() -> {
                retiringFields--;
                retiringBytes = Math.max(0, retiringBytes - field.residentBytes());
            });
        };
        try {
            retirement.retire(field, completion);
        } catch (RuntimeException failure) {
            completion.run();
            throw failure;
        }
    }

    private void removeEmpty(ModelInstanceId id, Entry entry) {
        if (entry.active == null && entry.target == null) entries.remove(id, entry);
    }

    private static long saturatedAdd(long left, long right) {
        if (right < 0) throw new IllegalArgumentException("resident bytes must not be negative");
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    public enum FailureKind { TRANSIENT, TERMINAL_UNSUPPORTED, BUDGET_REJECTED }

    @FunctionalInterface
    public interface Retirement {
        void retire(HostLocalLightField field, Runnable completion);
    }

    public static final class Target {
        private final long generation;
        private final HostLightFieldIdentity identity;
        private final AtomicBoolean cancelled;

        private Target(long generation, HostLightFieldIdentity identity, AtomicBoolean cancelled) {
            this.generation = generation;
            this.identity = identity;
            this.cancelled = cancelled;
        }

        public long generation() { return generation; }
        public HostLightFieldIdentity identity() { return identity; }
        public boolean cancelled() { return cancelled.get(); }
        private boolean cancel() { return cancelled.compareAndSet(false, true); }
    }

    private static final class Entry {
        private HostLocalLightField active;
        private HostLightFieldIdentity identity;
        private Target target;
    }
}
