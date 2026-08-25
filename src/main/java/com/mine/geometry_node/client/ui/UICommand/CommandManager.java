package com.mine.geometry_node.client.ui.UICommand;

import com.mine.geometry_node.GeometryNode;

import java.util.LinkedList;
import java.util.Objects;
import java.util.UUID;

/**
 * 历史记录管理器
 */
public class CommandManager {

    // 设置最大历史记录步数
    private static final int MAX_HISTORY_STEPS = 50;

    private final LinkedList<HistoryEntry> mUndoStack = new LinkedList<>();
    private final LinkedList<HistoryEntry> mRedoStack = new LinkedList<>();
    private Runnable mDirtyListener;
    private long mRevision;
    private String mLastChangeId = "";

    public void setDirtyListener(Runnable dirtyListener) {
        mDirtyListener = dirtyListener;
    }

    public long revision() {
        return mRevision;
    }

    public String lastChangeId() {
        return mLastChangeId;
    }

    /**
     * 提交并执行一个新命令
     */
    public boolean execute(ICommand command) {
        if (command == null || !command.canExecute()) {
            return false;
        }
        requireRevisionCapacity();
        HistoryEntry entry = new HistoryEntry(command, changeId(command));
        command.execute();

        mUndoStack.push(entry);

        if (mUndoStack.size() > MAX_HISTORY_STEPS) {
            mUndoStack.removeLast();
        }

        mRedoStack.clear();
        recordMutation(entry.changeId());
        return true;
    }

    /**
     * Executes a command and, only after it succeeds, replaces older undo/redo history with it.
     */
    public boolean executeAsNewBaseline(ICommand command) {
        if (command == null || !command.canExecute()) {
            return false;
        }
        requireRevisionCapacity();
        HistoryEntry entry = new HistoryEntry(command, changeId(command));
        command.execute();

        mUndoStack.clear();
        mRedoStack.clear();
        mUndoStack.push(entry);
        recordMutation(entry.changeId());
        return true;
    }

    public void undo() {
        if (!mUndoStack.isEmpty()) {
            requireRevisionCapacity();
            HistoryEntry entry = mUndoStack.peek();
            entry.command().undo();
            mUndoStack.pop();
            mRedoStack.push(entry);
            recordMutation(entry.changeId());
        }
    }

    public void redo() {
        if (!mRedoStack.isEmpty()) {
            HistoryEntry entry = mRedoStack.peek();
            if (!entry.command().canExecute()) {
                return;
            }
            requireRevisionCapacity();
            entry.command().execute();
            mRedoStack.pop();
            mUndoStack.push(entry);
            recordMutation(entry.changeId());
        }
    }

    public void clearHistory() {
        mUndoStack.clear();
        mRedoStack.clear();
    }

    /** Records a mutation performed by legacy initialization code that has no reversible command. */
    public void recordExternalMutation() {
        requireRevisionCapacity();
        mRedoStack.clear();
        recordMutation(UUID.randomUUID().toString());
    }

    private void markAsDirty() {
        if (mDirtyListener != null) {
            try {
                mDirtyListener.run();
            } catch (RuntimeException failure) {
                GeometryNode.LOGGER.error("Command dirty listener failed", failure);
            }
        }
    }

    private void recordMutation(String changeId) {
        mRevision++;
        mLastChangeId = Objects.requireNonNullElse(changeId, "");
        markAsDirty();
    }

    private void requireRevisionCapacity() {
        if (mRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("graph revision overflow");
        }
    }

    private static String changeId(ICommand command) {
        if (command instanceof IdentifiedCommand identified) {
            return identified.changeId();
        }
        return UUID.randomUUID().toString();
    }

    private record HistoryEntry(ICommand command, String changeId) {}
}
