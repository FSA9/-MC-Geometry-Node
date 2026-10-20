package com.mine.geometry_node.client.ui.document.edit;

import com.mine.geometry_node.client.history.HistoryOperation;
import com.mine.geometry_node.client.history.HistoryResult;
import com.mine.geometry_node.client.history.HistorySession;
import com.mine.geometry_node.client.history.HistoryState;
import com.mine.geometry_node.client.history.HistoryTarget;
import com.mine.geometry_node.client.ui.document.edit.history.GraphHistorySnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Commands mutate once; the shared session owns detached snapshots for all replay. */
public class CommandManager implements HistoryTarget, AutoCloseable {
    private static final int MAX_HISTORY_STEPS = 50;
    private final EditorContext context;
    private final HistorySession<HistoryEntry> history;
    private GraphHistorySnapshot currentSnapshot;
    // Covers domain work, core notifications, completion callbacks and deferred editor events.
    private boolean executing;
    private boolean notifying;

    public CommandManager(EditorContext context) {
        this(context, UUID.randomUUID().toString());
    }

    public CommandManager(EditorContext context, String scopeId) {
        this.context = Objects.requireNonNull(context, "context");
        history = new HistorySession<>(scopeId, MAX_HISTORY_STEPS, true);
    }

    @Override public HistoryState historyState() { return history.state(); }
    @Override public AutoCloseable subscribeHistory(Consumer<HistoryState> listener) {
        Objects.requireNonNull(listener, "listener");
        return history.subscribe(state -> {
            boolean previous = notifying;
            notifying = true;
            try { listener.accept(state); }
            finally { notifying = previous; }
        });
    }
    public long revision() { return historyState().revision(); }
    public String lastChangeId() { return historyState().lastChangeId(); }
    public boolean canUndo() { return historyState().canUndo(); }
    public boolean canRedo() { return historyState().canRedo(); }
    public boolean canSave() {
        return !executing && !notifying && historyState().phase() == HistoryState.Phase.IDLE;
    }

    /**
     * Prepares a detached edit without opening history or emitting notifications. The shared owner
     * rejects nested commands, saves, replay, and other preparations while user transformation runs.
     */
    public <T> T prepareEdit(Supplier<T> preparation) {
        Objects.requireNonNull(preparation, "preparation");
        requireIdle();
        if (!canSave()) throw new IllegalStateException("Graph owner is not available for edit preparation");
        executing = true;
        try { return preparation.get(); }
        finally { executing = false; }
    }

    /** Synchronous domain API for UI commands and MCP commits. Domain failures propagate. */
    public boolean execute(ICommand command) {
        return execute(command, true).completion().toCompletableFuture().join().applied();
    }

    /** Returns a settled result for domain rejection or failure; misuse still throws. */
    public HistoryOperation executeOperation(ICommand command) {
        return execute(command, false);
    }

    private HistoryOperation execute(ICommand command, boolean propagateFailure) {
        requireIdle();
        executing = true;
        try {
            var ticket = history.beginEdit(changeId(command), command == null ? "geometry_node.history.graph.edit" : command.historyLabel());
            context.beginHistoryNotifications();
            GraphHistorySnapshot before = null;
            List<String> beforeScope = null;
            GraphHistorySnapshot after;
            try {
                if (command == null || !command.canExecute()) {
                    history.fail(ticket, HistoryResult.Status.REJECTED, "Command cannot execute");
                    return ticket.operation();
                }
                beforeScope = context.currentGroupPath();
                before = GraphHistorySnapshot.capture(context.getGraph(), currentSnapshot);
                command.execute();
                after = GraphHistorySnapshot.capture(context.getGraph(), before);
            } catch (RuntimeException | Error failure) {
                rollback(ticket, before, beforeScope, failure);
                if (!propagateFailure && failure instanceof RuntimeException) return ticket.operation();
                throw failure;
            }
            currentSnapshot = after;
            if (after == before) history.noChange(ticket);
            else history.commitEdit(ticket, new HistoryEntry(before, after));
            return ticket.operation();
        } finally {
            try { context.finishHistoryNotifications(); }
            finally { executing = false; }
        }
    }

    @Override public HistoryOperation undo() { return replay(true); }
    @Override public HistoryOperation redo() { return replay(false); }

    private HistoryOperation replay(boolean undo) {
        requireIdle();
        executing = true;
        try {
            var ticket = undo ? history.beginUndo() : history.beginRedo();
            if (ticket == null) {
                var state = historyState();
                return HistoryOperation.completed(undo ? HistoryOperation.Kind.UNDO : HistoryOperation.Kind.REDO,
                        new HistoryResult(HistoryResult.Status.NO_CHANGE, "", state.lastChangeId(), state.revision()));
            }
            context.beginHistoryNotifications();
            GraphHistorySnapshot before = null;
            List<String> beforeScope = null;
            GraphHistorySnapshot target = undo ? ticket.payload().before() : ticket.payload().after();
            try {
                beforeScope = context.currentGroupPath();
                before = GraphHistorySnapshot.capture(context.getGraph(), currentSnapshot);
                context.replaceGraphState(target.restore());
            } catch (RuntimeException | Error failure) {
                rollback(ticket, before, beforeScope, failure);
                if (failure instanceof RuntimeException) return ticket.operation();
                throw failure;
            }
            currentSnapshot = target;
            history.commitReplay(ticket);
            return ticket.operation();
        } finally {
            try { context.finishHistoryNotifications(); }
            finally { executing = false; }
        }
    }

    private void rollback(HistorySession.Ticket<HistoryEntry> ticket, GraphHistorySnapshot before,
                          List<String> beforeScope, Throwable failure) {
        context.discardHistoryNotifications();
        if (before != null) {
            try {
                context.replaceGraphState(before.restore(), beforeScope);
                currentSnapshot = before;
            } catch (RuntimeException | Error rollbackFailure) {
                if (rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
                currentSnapshot = null;
                history.invalidate(ticket, "Graph rollback failed: " + rollbackFailure.getMessage());
                return;
            }
        }
        history.fail(ticket, HistoryResult.Status.FAILED, Objects.toString(failure.getMessage(), "Graph operation failed"));
    }

    /** Discards replay entries while preserving revision, document identity and savepoint. */
    public void clearHistory() {
        requireIdle();
        executing = true;
        try {
            currentSnapshot = null;
            history.clear();
        } finally { executing = false; }
    }

    /** An irreversible external edit starts a new dirty baseline and discards both replay stacks. */
    public void recordExternalMutation() {
        requireIdle();
        executing = true;
        try {
            currentSnapshot = null;
            history.newBaseline(UUID.randomUUID().toString());
        } finally { executing = false; }
    }

    public void markSaved() {
        requireIdle();
        executing = true;
        try { history.markSaved(); }
        finally { executing = false; }
    }

    @Override public void close() {
        requireIdle();
        executing = true;
        try {
            currentSnapshot = null;
            history.close();
        } finally { executing = false; }
    }

    private void requireIdle() {
        if (executing || notifying) throw new IllegalStateException("Cannot change graph history during an operation or notification");
    }

    private static String changeId(ICommand command) {
        return command instanceof IdentifiedCommand identified ? identified.changeId() : UUID.randomUUID().toString();
    }

    private record HistoryEntry(GraphHistorySnapshot before, GraphHistorySnapshot after) {}
}
