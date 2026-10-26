package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.feature.entity.edit.ClientEntityEditor;

import java.util.*;
import java.util.function.Consumer;

/** UI-thread controller. Completed edits outlive presentation; no View or form is retained here. */
final class EntityEditSession {
    interface Gateway {
        ClientEntityEditor.Request read(String dimension, UUID entity);
        ClientEntityEditor.Request apply(String dimension, UUID entity, String revision, Map<String, Object> values);
    }
    interface Scheduler { void schedule(Runnable task, long delayMillis); }
    enum FailureKind { REJECTED, UNKNOWN }
    record Failure(FailureKind kind, String message) {}

    private final Gateway gateway;
    private final Scheduler scheduler;
    private final Consumer<Runnable> queue;
    private final Runnable applied;
    private final Consumer<Failure> failures;
    private final String dimension;
    private final UUID entity;
    private Runnable listener = () -> {};
    private EntityEditDraft draft;
    private ClientEntityEditor.Request request;
    private String revision = "", error = "";
    private long generation, scheduled, acknowledged;
    private boolean loading, saving, blocked, closed, started, reconciling;
    private Failure failure;

    EntityEditSession(String dimension, UUID entity, Gateway gateway, Scheduler scheduler,
                      Consumer<Runnable> queue, Runnable applied, Consumer<Failure> failures) {
        this.dimension = dimension; this.entity = entity; this.gateway = gateway;
        this.scheduler = scheduler; this.queue = queue; this.applied = applied; this.failures = failures;
    }
    EntityEditDraft draft() { return draft; }
    boolean loading() { return loading; }
    boolean saving() { return saving; }
    boolean blocked() { return blocked; }
    boolean closed() { return closed; }
    String error() { return error; }
    Failure failure() { return failure; }
    long acknowledged() { return acknowledged; }
    boolean reconciling() { return reconciling; }
    void observe(Runnable listener) { this.listener = Objects.requireNonNull(listener); }
    void start() { if (!started && !closed) { started = true; reload(); } }

    /** Reload is recovery, never a retry of an unacknowledged command batch. */
    void reload() {
        if (closed || loading || saving || !blocked && draft != null && draft.dirty()) return;
        // Recovery reads cannot make an unacknowledged command batch safe to send again.
        // In particular, closing while this read is pending must leave that batch blocked.
        loading = true; error = ""; failure = null;
        long expected = ++generation;
        listener.run();
        try {
            var next = gateway.read(dimension, entity);
            request = next;
            next.result().whenComplete((reply, failed) -> queue.accept(() -> {
                if (closed || generation != expected) return;
                request = null; loading = false;
                if (failed != null || reply == null || !reply.success()) {
                    blocked = true; error = reason(reply, failed);
                } else {
                    revision = reply.revision();
                    if (draft == null) draft = new EntityEditDraft(normalize(reply.values()), this::changed);
                    else {
                        // Reuse the parent binding and controls. Only completed state is refreshed;
                        // raw attribute/Data forms belong to their existing presentation models.
                        reconciling = true;
                        try { draft.acknowledge(normalize(reply.values()), draft.snapshot()); }
                        finally { reconciling = false; }
                        acknowledged++;
                    }
                    blocked = false;
                }
                listener.run();
            }));
        } catch (RuntimeException failed) {
            loading = false; blocked = true; error = reason(null, failed); listener.run();
        }
    }
    private static Map<String, Object> normalize(Map<String, Object> values) {
        var snapshot = new LinkedHashMap<>(values);
        snapshot.putIfAbsent("equipmentCreative", false);
        snapshot.putIfAbsent("equipmentDrops", List.of());
        return snapshot;
    }
    private void changed() {
        requestCommit();
        listener.run();
    }
    private void requestCommit() {
        if (blocked || loading || saving || draft == null || !draft.dirty()) return;
        long token = ++scheduled;
        // This scheduler must belong to the session, never View.postDelayed/removeCallbacks.
        scheduler.schedule(() -> { if (scheduled == token) sendPending(); }, 125);
    }
    void sendPending() {
        if (loading || saving || blocked || draft == null || revision.isEmpty() || !draft.dirty()) return;
        Map<String, Object> sentSnapshot = draft.snapshot();
        Map<String, Object> changes = draft.changes();
        saving = true; error = "";
        listener.run();
        try {
            var next = gateway.apply(dimension, entity, revision, changes);
            request = next;
            next.result().whenComplete((reply, failed) -> queue.accept(() -> complete(reply, failed, sentSnapshot)));
        } catch (RuntimeException failed) { complete(null, failed, sentSnapshot); }
    }
    private void complete(ClientEntityEditor.Reply reply, Throwable failed, Map<String, Object> sentSnapshot) {
        request = null; saving = false;
        if (failed != null || reply == null || !reply.success()) {
            blocked = true; error = reason(reply, failed);
            FailureKind kind = failed != null || error.endsWith(".timeout") || error.endsWith(".disconnected")
                    || error.endsWith(".failed") ? FailureKind.UNKNOWN : FailureKind.REJECTED;
            failure = new Failure(kind, error);
            listener.run();
            // Also report while visible: a later close must not hide the only failure result.
            failures.accept(failure);
            return;
        }
        revision = reply.revision();
        boolean unavailableAttributeEdit;
        reconciling = true;
        try { unavailableAttributeEdit = draft.acknowledge(normalize(reply.values()), sentSnapshot); }
        finally { reconciling = false; }
        acknowledged++;
        if (unavailableAttributeEdit) {
            blocked = true;
            error = "geometry_node.entity_editor.error.capability_changed";
            failure = new Failure(FailureKind.REJECTED, error);
        }
        // Schedule before invoking presentation or external refresh callbacks.
        requestCommit();
        listener.run();
        applied.run();
        if (unavailableAttributeEdit) failures.accept(failure);
    }
    private static String reason(ClientEntityEditor.Reply reply, Throwable failed) {
        return failed == null && reply != null ? reply.message() : "geometry_node.entity_editor.error.failed";
    }
    /** Detach once. Reads may be cancelled; queued writes and their acknowledgements must drain. */
    void close() {
        if (closed) return;
        closed = true; listener = () -> {};
        if (loading) {
            generation++; loading = false;
            if (request != null) { request.close(); request = null; }
        }
        requestCommit();
    }
}
