package com.mine.geometry_node.client.ui.UICommand;

import java.util.LinkedList;

/**
 * 历史记录管理器
 */
public class CommandManager {

    // 设置最大历史记录步数
    private static final int MAX_HISTORY_STEPS = 50;

    private final LinkedList<ICommand> mUndoStack = new LinkedList<>();
    private final LinkedList<ICommand> mRedoStack = new LinkedList<>();
    private Runnable mDirtyListener;

    public void setDirtyListener(Runnable dirtyListener) {
        mDirtyListener = dirtyListener;
    }

    /**
     * 提交并执行一个新命令
     */
    public boolean execute(ICommand command) {
        if (command == null || !command.canExecute()) {
            return false;
        }
        command.execute();

        mUndoStack.push(command);

        if (mUndoStack.size() > MAX_HISTORY_STEPS) {
            mUndoStack.removeLast();
        }

        mRedoStack.clear();
        markAsDirty();
        return true;
    }

    public void undo() {
        if (!mUndoStack.isEmpty()) {
            ICommand cmd = mUndoStack.pop();
            cmd.undo();
            mRedoStack.push(cmd);
            markAsDirty();
        }
    }

    public void redo() {
        if (!mRedoStack.isEmpty()) {
            ICommand cmd = mRedoStack.peek();
            if (!cmd.canExecute()) {
                return;
            }
            mRedoStack.pop();
            cmd.execute();
            mUndoStack.push(cmd);
            markAsDirty();
        }
    }

    public void clearHistory() {
        mUndoStack.clear();
        mRedoStack.clear();
    }

    private void markAsDirty() {
        if (mDirtyListener != null) {
            mDirtyListener.run();
        }
    }
}
