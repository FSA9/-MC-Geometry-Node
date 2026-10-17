package com.mine.geometry_node.client.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The sole owner of one scope's history stacks. Payloads belong to the adapter and should
 * be immutable. This class neither executes domain changes nor schedules retries.
 *
 * <p>Reserve before invoking domain validation, preparation or capture. A begin notification
 * describes that reservation; it runs before begin returns, so {@link #state()} is authoritative
 * for busy state even before the adapter has stored its ticket. Publish all domain changes
 * (including ACK metadata) before settling the ticket. Observers and completion callbacks run
 * synchronously after the core transition, outside its lock. Mutations during this publication
 * are rejected, including mutations from another thread; reads and unsubscribe remain safe.
 * Observer exceptions are isolated so they cannot interrupt a committed transition.
 */
public final class HistorySession<P> implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(HistorySession.class.getName());
    /** Immutable reservation, valid only for its owning session and epoch. */
    public static final class Ticket<P> {
        private final Object owner;
        private final long epoch;
        private final HistoryOperation operation;
        private final Entry<P> entry;
        private final String changeId;
        private final String label;

        private Ticket(Object owner, long epoch, HistoryOperation.Kind kind,
                       Entry<P> entry, String changeId, String label) {
            this.owner = owner;
            this.epoch = epoch;
            this.operation = new HistoryOperation(kind);
            this.entry = entry;
            this.changeId = changeId;
            this.label = label;
        }

        public HistoryOperation operation() { return operation; }
        public HistoryOperation.Kind kind() { return operation.kind(); }
        public P payload() { return entry == null ? null : entry.payload; }
        public String changeId() { return changeId; }
        public String label() { return label; }
    }

    private record Entry<P>(P payload, String changeId, String label,
                            Object beforeState, Object afterState) {}

    private final Object lock = new Object();
    private final Object owner = new Object();
    private final int limit;
    private final boolean savepoints;
    private final Deque<Entry<P>> undo = new ArrayDeque<>();
    private final Deque<Entry<P>> redo = new ArrayDeque<>();
    private final List<Subscription> listeners = new ArrayList<>();
    private String scopeId;
    private long epoch;
    private long revision;
    private String lastChangeId = "";
    private Object currentState = new Object();
    private Object savedState = currentState;
    private HistoryState.Phase phase = HistoryState.Phase.IDLE;
    private String blockedReason = "";
    private Ticket<P> pending;
    private boolean notifying;

    public HistorySession(String scopeId, int limit, boolean savepoints) {
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
        if (limit < 0) throw new IllegalArgumentException("limit must be nonnegative");
        this.limit = limit;
        this.savepoints = savepoints;
    }

    public Ticket<P> beginEdit(String changeId, String label) {
        Ticket<P> ticket;
        Publication publication;
        synchronized (lock) {
            requireIdle();
            requireRevisionCapacity();
            ticket = new Ticket<>(owner, epoch, HistoryOperation.Kind.EDIT, null,
                    Objects.requireNonNull(changeId, "changeId"), label == null ? "" : label);
            reserve(ticket);
            publication = publication(null, null);
        }
        publication.publish();
        return ticket;
    }

    public Ticket<P> beginUndo() { return beginReplay(HistoryOperation.Kind.UNDO); }
    public Ticket<P> beginRedo() { return beginReplay(HistoryOperation.Kind.REDO); }

    private Ticket<P> beginReplay(HistoryOperation.Kind kind) {
        Ticket<P> ticket;
        Publication publication;
        synchronized (lock) {
            requireIdle();
            Entry<P> entry = (kind == HistoryOperation.Kind.UNDO ? undo : redo).peekFirst();
            if (entry == null) return null;
            requireRevisionCapacity();
            ticket = new Ticket<>(owner, epoch, kind, entry, entry.changeId, entry.label);
            reserve(ticket);
            publication = publication(null, null);
        }
        publication.publish();
        return ticket;
    }

    private void reserve(Ticket<P> ticket) {
        pending = ticket;
        phase = HistoryState.Phase.PENDING;
        blockedReason = "History operation pending";
    }

    public boolean commitEdit(Ticket<P> ticket, P payload) {
        Publication publication;
        synchronized (lock) {
            if (!isCurrent(ticket)) return false;
            requireNotNotifying();
            requireKind(ticket, HistoryOperation.Kind.EDIT);
            Objects.requireNonNull(payload, "payload");
            Object nextState = new Object();
            undo.addFirst(new Entry<>(payload, ticket.changeId, ticket.label, currentState, nextState));
            while (undo.size() > limit) undo.removeLast();
            redo.clear();
            currentState = nextState;
            revision++;
            lastChangeId = ticket.changeId;
            publication = settle(ticket, HistoryResult.Status.APPLIED, "", HistoryState.Phase.IDLE);
        }
        publication.publish();
        return true;
    }

    public boolean commitReplay(Ticket<P> ticket) {
        Publication publication;
        synchronized (lock) {
            if (!isCurrent(ticket)) return false;
            requireNotNotifying();
            if (ticket.kind() == HistoryOperation.Kind.EDIT)
                throw new IllegalArgumentException("An edit ticket cannot commit a replay");
            boolean undoing = ticket.kind() == HistoryOperation.Kind.UNDO;
            Deque<Entry<P>> from = undoing ? undo : redo;
            Deque<Entry<P>> to = undoing ? redo : undo;
            // A reservation never removes its entry until acknowledgement succeeds.
            Entry<P> entry = from.removeFirst();
            to.addFirst(entry);
            currentState = undoing ? entry.beforeState : entry.afterState;
            revision++;
            lastChangeId = ticket.changeId;
            publication = settle(ticket, HistoryResult.Status.APPLIED, "", HistoryState.Phase.IDLE);
        }
        publication.publish();
        return true;
    }

    public boolean noChange(Ticket<P> ticket) {
        return finish(ticket, HistoryResult.Status.NO_CHANGE, "", HistoryState.Phase.IDLE);
    }

    public boolean fail(Ticket<P> ticket, HistoryResult.Status status, String message) {
        Publication publication;
        synchronized (lock) {
            if (!isCurrent(ticket)) return false;
            requireNotNotifying();
            Objects.requireNonNull(status, "status");
            if (status == HistoryResult.Status.APPLIED || status == HistoryResult.Status.NO_CHANGE)
                throw new IllegalArgumentException("Use commit or noChange for successful results");
            publication = settle(ticket, status, message, HistoryState.Phase.IDLE);
        }
        publication.publish();
        return true;
    }

    /** Unknown means the result may still arrive; the same reservation remains active. */
    public boolean markUnknown(Ticket<P> ticket, String reason) {
        Publication publication;
        synchronized (lock) {
            if (!isCurrent(ticket)) return false;
            requireNotNotifying();
            if (phase == HistoryState.Phase.UNKNOWN) return false;
            phase = HistoryState.Phase.UNKNOWN;
            blockedReason = reason == null ? "History outcome unknown" : reason;
            ticket.operation.setPhase(HistoryOperation.Phase.UNKNOWN);
            publication = publication(null, null);
        }
        publication.publish();
        return true;
    }

    /** Failed rollback leaves the scope unusable until reset or close. */
    public boolean invalidate(Ticket<P> ticket, String reason) {
        return finish(ticket, HistoryResult.Status.FAILED, reason, HistoryState.Phase.INVALID);
    }

    private boolean finish(Ticket<P> ticket, HistoryResult.Status status, String message,
                           HistoryState.Phase nextPhase) {
        Publication publication;
        synchronized (lock) {
            if (!isCurrent(ticket)) return false;
            requireNotNotifying();
            publication = settle(ticket, status, message, nextPhase);
        }
        publication.publish();
        return true;
    }

    private Publication settle(Ticket<P> ticket, HistoryResult.Status status, String message,
                               HistoryState.Phase nextPhase) {
        pending = null;
        phase = nextPhase;
        blockedReason = nextPhase == HistoryState.Phase.INVALID
                ? (message == null || message.isEmpty() ? "History scope invalid" : message) : "";
        ticket.operation.setPhase(HistoryOperation.Phase.SETTLED);
        return publication(ticket.operation, new HistoryResult(status, message, ticket.changeId, revision));
    }

    public HistoryState state() {
        synchronized (lock) { return snapshot(); }
    }

    /** Immediately supplies the current snapshot, then each completed core transition. */
    public AutoCloseable subscribe(Consumer<HistoryState> listener) {
        Objects.requireNonNull(listener, "listener");
        Subscription subscription = new Subscription(listener);
        Publication publication;
        synchronized (lock) {
            requireNotNotifying();
            if (phase != HistoryState.Phase.CLOSED) listeners.add(subscription);
            notifying = true;
            publication = new Publication(snapshot(), List.of(subscription), null, null);
        }
        publication.publish();
        return subscription;
    }

    public P peekUndo() {
        synchronized (lock) { return undo.isEmpty() ? null : undo.peekFirst().payload; }
    }

    public P peekRedo() {
        synchronized (lock) { return redo.isEmpty() ? null : redo.peekFirst().payload; }
    }

    /** Immutable snapshot of retained payloads, undo then redo, newest first in each stack. */
    public List<P> retainedPayloads() {
        synchronized (lock) {
            List<P> payloads = new ArrayList<>(undo.size() + redo.size());
            for (Entry<P> entry : undo) payloads.add(entry.payload);
            for (Entry<P> entry : redo) payloads.add(entry.payload);
            return List.copyOf(payloads);
        }
    }

    /** Drops replay data without changing document identity, revision or savepoint. */
    public void clear() {
        Publication publication;
        synchronized (lock) {
            requireIdle();
            undo.clear();
            redo.clear();
            publication = publication(null, null);
        }
        publication.publish();
    }

    /** Records an external document replacement that cannot be undone. */
    public void newBaseline(String changeId) {
        Publication publication;
        synchronized (lock) {
            requireIdle();
            requireRevisionCapacity();
            Objects.requireNonNull(changeId, "changeId");
            undo.clear();
            redo.clear();
            currentState = new Object();
            lastChangeId = changeId;
            revision++;
            publication = publication(null, null);
        }
        publication.publish();
    }

    /** Saves the current document identity. Scopes without savepoints never report dirty. */
    public void markSaved() {
        Publication publication;
        synchronized (lock) {
            requireIdle();
            if (!savepoints || savedState == currentState) return;
            savedState = currentState;
            publication = publication(null, null);
        }
        publication.publish();
    }

    /** Opens a fresh epoch; an outstanding old handle completes as SCOPE_CLOSED. */
    public void reset(String scopeId) {
        endScope(Objects.requireNonNull(scopeId, "scopeId"), false);
    }

    @Override
    public void close() { endScope(null, true); }

    private void endScope(String newScopeId, boolean closing) {
        Publication publication;
        synchronized (lock) {
            requireNotNotifying();
            if (closing && phase == HistoryState.Phase.CLOSED) return;
            HistoryOperation aborted = pending == null ? null : pending.operation;
            HistoryResult result = pending == null ? null : new HistoryResult(
                    HistoryResult.Status.SCOPE_CLOSED, "History scope closed", pending.changeId, revision);
            if (aborted != null) aborted.setPhase(HistoryOperation.Phase.SETTLED);
            pending = null;
            epoch++;
            undo.clear();
            redo.clear();
            if (!closing) {
                scopeId = newScopeId;
                revision = 0;
                lastChangeId = "";
                currentState = new Object();
                savedState = currentState;
            }
            phase = closing ? HistoryState.Phase.CLOSED : HistoryState.Phase.IDLE;
            blockedReason = closing ? "History scope closed" : "";
            publication = publication(aborted, result);
            if (closing) listeners.clear();
        }
        publication.publish();
    }

    private boolean isCurrent(Ticket<P> ticket) {
        return ticket != null && ticket.owner == owner && ticket.epoch == epoch && pending == ticket;
    }

    private void requireNotNotifying() {
        if (notifying) throw new IllegalStateException("History mutation during notification");
    }

    private void requireIdle() {
        requireNotNotifying();
        if (phase != HistoryState.Phase.IDLE)
            throw new IllegalStateException("History scope is " + phase);
    }

    private void requireRevisionCapacity() {
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("History revision exhausted");
    }

    private void requireKind(Ticket<P> ticket, HistoryOperation.Kind kind) {
        if (ticket.kind() != kind) throw new IllegalArgumentException("Wrong history ticket kind");
    }

    private HistoryState snapshot() {
        return new HistoryState(scopeId, phase, revision, lastChangeId, undo.size(), redo.size(),
                undo.isEmpty() ? "" : undo.peekFirst().label,
                redo.isEmpty() ? "" : redo.peekFirst().label,
                savepoints, savepoints && currentState != savedState, blockedReason, blockedReason);
    }

    // Called only while locked; the guard lasts through both future and listener callbacks.
    private Publication publication(HistoryOperation operation, HistoryResult result) {
        notifying = true;
        return new Publication(snapshot(), List.copyOf(listeners), operation, result);
    }

    private final class Subscription implements AutoCloseable {
        private final Consumer<HistoryState> listener;
        private volatile boolean active = true;

        private Subscription(Consumer<HistoryState> listener) { this.listener = listener; }

        @Override
        public void close() {
            synchronized (lock) {
                active = false;
                listeners.remove(this);
            }
        }
    }

    private final class Publication {
        private final HistoryState state;
        private final List<Subscription> subscriptions;
        private final HistoryOperation operation;
        private final HistoryResult result;

        private Publication(HistoryState state, List<Subscription> subscriptions,
                            HistoryOperation operation, HistoryResult result) {
            this.state = state;
            this.subscriptions = subscriptions;
            this.operation = operation;
            this.result = result;
        }

        private void publish() {
            try {
                if (operation != null) operation.complete(result);
                for (Subscription subscription : subscriptions) {
                    if (!subscription.active) continue;
                    try {
                        subscription.listener.accept(state);
                    } catch (Throwable failure) {
                        // Observer failures cannot roll back domain or core state, or skip peers.
                        try {
                            LOGGER.log(System.Logger.Level.WARNING, "History observer failed", failure);
                        } catch (Throwable ignored) {
                            // A logging provider must not interrupt publication either.
                        }
                    }
                }
            } finally {
                synchronized (lock) { notifying = false; }
            }
        }
    }
}
