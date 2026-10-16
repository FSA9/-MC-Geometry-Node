package com.mine.geometry_node.client.ui.UICommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mine.geometry_node.client.ui.UICommand.history.GraphHistorySnapshot;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-document history. Commands perform the initial mutation; history stores detached before/after
 * states, so restoring a group or replacing a graph never replays a command holding stale objects.
 */
public class CommandManager {
    private static final int MAX_HISTORY_STEPS = 50;
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

    private final EditorContext context;
    private final LinkedList<HistoryEntry> mUndoStack = new LinkedList<>();
    private final LinkedList<HistoryEntry> mRedoStack = new LinkedList<>();
    private GraphHistorySnapshot currentSnapshot;
    private boolean executing;
    private Runnable mDirtyListener;
    private long mRevision;
    private String mLastChangeId = "";

    public CommandManager(EditorContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void setDirtyListener(Runnable dirtyListener) { mDirtyListener = dirtyListener; }
    public long revision() { return mRevision; }
    public String lastChangeId() { return mLastChangeId; }

    public boolean execute(ICommand command) { return execute(command, false); }

    /** Explicit document reset only; ordinary UI edits and MCP patches must use {@link #execute}. */
    public boolean executeAsNewBaseline(ICommand command) { return execute(command, true); }

    private boolean execute(ICommand command, boolean replaceHistory) {
        requireIdle();
        if (command == null || !command.canExecute()) return false;
        requireRevisionCapacity();
        GraphHistorySnapshot before = GraphHistorySnapshot.capture(context.getGraph(), currentSnapshot);
        List<String> beforeScope = context.currentGroupPath();
        GraphHistorySnapshot after;
        String changeId = changeId(command);
        executing = true;
        try {
            command.execute();
            after = GraphHistorySnapshot.capture(context.getGraph(), before);
        } catch (RuntimeException | Error failure) {
            try {
                context.replaceGraphState(before.restore(), beforeScope);
                currentSnapshot = before;
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            executing = false;
        }
        currentSnapshot = after;
        if (after == before) return false;

        HistoryEntry entry = new HistoryEntry(before, after, changeId);
        if (replaceHistory) mUndoStack.clear();
        mUndoStack.push(entry);
        if (mUndoStack.size() > MAX_HISTORY_STEPS) mUndoStack.removeLast();
        mRedoStack.clear();
        recordMutation(entry.changeId());
        return true;
    }

    public void undo() {
        requireIdle();
        if (mUndoStack.isEmpty()) return;
        requireRevisionCapacity();
        HistoryEntry entry = mUndoStack.peek();
        executing = true;
        try {
            restore(entry.before());
            mUndoStack.pop();
            mRedoStack.push(entry);
        } finally {
            executing = false;
        }
        recordMutation(entry.changeId());
    }

    public void redo() {
        requireIdle();
        if (mRedoStack.isEmpty()) return;
        requireRevisionCapacity();
        HistoryEntry entry = mRedoStack.peek();
        executing = true;
        try {
            restore(entry.after());
            mRedoStack.pop();
            mUndoStack.push(entry);
        } finally {
            executing = false;
        }
        recordMutation(entry.changeId());
    }

    private void restore(GraphHistorySnapshot snapshot) {
        context.replaceGraphState(snapshot.restore());
        currentSnapshot = snapshot;
    }

    public void clearHistory() {
        requireIdle();
        mUndoStack.clear();
        mRedoStack.clear();
        currentSnapshot = null;
    }

    /**
     * Starts a new baseline after an explicitly irreversible external mutation. Both stacks must be
     * discarded: restoring an older document snapshot would also discard that untracked mutation.
     * Normal edits, scope navigation and MCP patches must not use this escape hatch.
     */
    public void recordExternalMutation() {
        requireIdle();
        requireRevisionCapacity();
        mUndoStack.clear();
        mRedoStack.clear();
        currentSnapshot = null;
        recordMutation(UUID.randomUUID().toString());
    }

    private void markAsDirty() {
        if (mDirtyListener != null) {
            try {
                mDirtyListener.run();
            } catch (RuntimeException failure) {
                LOGGER.error("Command dirty listener failed", failure);
            }
        }
    }

    private void recordMutation(String changeId) {
        mRevision++;
        mLastChangeId = Objects.requireNonNullElse(changeId, "");
        markAsDirty();
    }

    private void requireIdle() {
        if (executing) throw new IllegalStateException("Cannot change history while a command is executing");
    }

    private void requireRevisionCapacity() {
        if (mRevision == Long.MAX_VALUE) throw new IllegalStateException("graph revision overflow");
    }

    private static String changeId(ICommand command) {
        return command instanceof IdentifiedCommand identified ? identified.changeId() : UUID.randomUUID().toString();
    }

    private record HistoryEntry(GraphHistorySnapshot before, GraphHistorySnapshot after, String changeId) {}
}
