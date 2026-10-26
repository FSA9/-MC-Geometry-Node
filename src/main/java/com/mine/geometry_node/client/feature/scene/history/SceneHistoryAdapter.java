package com.mine.geometry_node.client.feature.scene.history;

import com.mine.geometry_node.client.history.HistoryOperation;
import com.mine.geometry_node.client.history.HistoryResult;
import com.mine.geometry_node.client.history.HistorySession;
import com.mine.geometry_node.client.history.HistoryState;
import com.mine.geometry_node.core.engine.system.scene.SceneChange;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;

import java.util.*;
import java.util.function.Consumer;

/** Conditional scene compensation. HistorySession is the sole owner of both stacks.
 * Callers serialize this adapter with their domain publication lock. */
public final class SceneHistoryAdapter {
    private final HistorySession<Delta> session;
    // Proofs belong to individual entries. A later local edit must not renew a proof
    // whose version chain was broken by a remote write, even if content returns to A.
    private final Map<Delta, Map<UUID, Long>> entryVersions = new IdentityHashMap<>();
    private HistorySession.Ticket<Delta> active;
    private boolean notifying, transitioning;

    public record Delta(Set<UUID> ids, Map<UUID, SceneObject> before, Map<UUID, SceneObject> after) {
        public Delta { ids = Set.copyOf(ids); before = Map.copyOf(before); after = Map.copyOf(after); }
    }

    public SceneHistoryAdapter(String scopeId) { session = new HistorySession<>(scopeId, 50, false); }
    public HistoryState state() { return session.state(); }
    public AutoCloseable subscribe(Consumer<HistoryState> listener) {
        return session.subscribe(state -> {
            boolean previous = notifying;
            notifying = true;
            try { listener.accept(state); } finally { notifying = previous; }
        });
    }
    public HistorySession.Ticket<Delta> beginEdit(String changeId, String label) {
        requireNotNotifying();
        return active = session.beginEdit(changeId, label);
    }
    public HistorySession.Ticket<Delta> beginReplay(boolean undoing) {
        requireNotNotifying();
        return active = undoing ? session.beginUndo() : session.beginRedo();
    }
    public String blockedReason(boolean undoing, Map<UUID, SceneObject> current, Map<UUID, Long> versions) {
        Delta delta = undoing ? session.peekUndo() : session.peekRedo();
        if (delta == null) return "";
        Map<UUID, SceneObject> expected = undoing ? delta.after() : delta.before();
        Map<UUID, Long> proof = entryVersions.getOrDefault(delta, Map.of());
        for (UUID id : delta.ids()) {
            if (versions.getOrDefault(id, -1L).longValue() != proof.getOrDefault(id, Long.MIN_VALUE).longValue()
                    || !same(current.get(id), expected.get(id))) return "Scene history conflicts with another edit";
        }
        return "";
    }
    public List<SceneChange> prepare(HistorySession.Ticket<Delta> ticket,
                                    Map<UUID, SceneObject> current, Map<UUID, Long> versions) {
        if (ticket == null || ticket != active || ticket.payload() == null) throw new IllegalStateException("Stale scene history ticket");
        boolean undoing = ticket.kind() == HistoryOperation.Kind.UNDO;
        String reason = blockedReason(undoing, current, versions);
        if (!reason.isEmpty()) throw new IllegalStateException(reason);
        Delta delta = ticket.payload();
        Map<UUID, SceneObject> desired = undoing ? delta.before() : delta.after();
        return delta.ids().stream().map(id -> new SceneChange(id, versions.getOrDefault(id, -1L), desired.get(id))).toList();
    }
    /** Mirror and selection must already be published when this method is called. */
    public boolean acknowledgeEdit(HistorySession.Ticket<Delta> ticket, Map<UUID, SceneObject> before,
                                   Map<UUID, SceneObject> after, Set<UUID> ids,
                                   Map<UUID, Long> beforeVersions, Map<UUID, Long> versions) {
        if (ticket == null || ticket != active || ticket.kind() != HistoryOperation.Kind.EDIT) return false;
        requireNotNotifying();
        Map<UUID, SceneObject> oldValues = subset(before, ids), newValues = subset(after, ids);
        active = null;
        if (ids.stream().noneMatch(id -> !same(oldValues.get(id), newValues.get(id)))) return transition(() -> session.noChange(ticket));
        advanceVersions(ids, beforeVersions, versions);
        Delta delta = new Delta(ids, oldValues, newValues);
        entryVersions.put(delta, versionSubset(ids, versions));
        boolean settled = transition(() -> session.commitEdit(ticket, delta));
        pruneVersions();
        return settled;
    }
    public boolean acknowledgeReplay(HistorySession.Ticket<Delta> ticket, Map<UUID, Long> versions) {
        if (ticket == null || ticket != active || ticket.payload() == null) return false;
        requireNotNotifying();
        active = null;
        advanceVersions(ticket.payload().ids(),
                Map.copyOf(entryVersions.get(ticket.payload())), versions);
        return transition(() -> session.commitReplay(ticket));
    }
    public boolean noChange(HistorySession.Ticket<Delta> ticket) {
        if (ticket == null || ticket != active) return false;
        requireNotNotifying();
        active = null;
        return transition(() -> session.noChange(ticket));
    }
    public boolean fail(HistorySession.Ticket<Delta> ticket, HistoryResult.Status status, String reason) {
        if (ticket == null || ticket != active) return false;
        requireNotNotifying();
        Objects.requireNonNull(status);
        if (status == HistoryResult.Status.APPLIED || status == HistoryResult.Status.NO_CHANGE)
            throw new IllegalArgumentException("Use acknowledge or noChange for successful results");
        active = null;
        return transition(() -> session.fail(ticket, status, reason));
    }
    public boolean markUnknown(HistorySession.Ticket<Delta> ticket, String reason) {
        return ticket != null && ticket == active && session.markUnknown(ticket, reason);
    }
    public void reset(String scopeId) {
        requireNotNotifying();
        Objects.requireNonNull(scopeId);
        active = null; entryVersions.clear();
        transition(() -> { session.reset(scopeId); return true; });
    }
    private boolean transition(java.util.function.BooleanSupplier action) {
        transitioning = true;
        try { return action.getAsBoolean(); } finally { transitioning = false; }
    }
    private void requireNotNotifying() {
        if (notifying || transitioning) throw new IllegalStateException("Scene history mutation during notification");
    }
    private void advanceVersions(Set<UUID> ids, Map<UUID, Long> before, Map<UUID, Long> after) {
        for (Map<UUID, Long> proof : entryVersions.values()) {
            for (UUID id : ids) {
                if (proof.containsKey(id) && proof.get(id).longValue() == before.getOrDefault(id, -1L).longValue())
                    proof.put(id, after.getOrDefault(id, -1L));
            }
        }
    }
    private static Map<UUID, Long> versionSubset(Set<UUID> ids, Map<UUID, Long> versions) {
        Map<UUID, Long> result = new HashMap<>();
        for (UUID id : ids) result.put(id, versions.getOrDefault(id, -1L));
        return result;
    }
    private void pruneVersions() {
        Set<Delta> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.addAll(session.retainedPayloads());
        entryVersions.keySet().retainAll(retained);
    }
    private static boolean same(SceneObject a, SceneObject b) {
        return com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.sameHistoryState(a,b);
    }
    private static Map<UUID, SceneObject> subset(Map<UUID, SceneObject> objects, Set<UUID> ids) {
        Map<UUID, SceneObject> result = new LinkedHashMap<>();
        for (UUID id : ids) if (objects.containsKey(id)) result.put(id, objects.get(id));
        return result;
    }
}
