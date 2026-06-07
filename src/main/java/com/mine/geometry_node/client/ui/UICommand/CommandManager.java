package com.mine.geometry_node.client.ui.UICommand;

import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;

import java.util.LinkedList;

/**
 * 历史记录管理器
 */
public class CommandManager {

    // 设置最大历史记录步数
    private static final int MAX_HISTORY_STEPS = 50;

    private final LinkedList<ICommand> mUndoStack = new LinkedList<>();
    private final LinkedList<ICommand> mRedoStack = new LinkedList<>();

    /**
     * 提交并执行一个新命令
     */
    public void execute(ICommand command) {
        command.execute();

        mUndoStack.push(command);

        if (mUndoStack.size() > MAX_HISTORY_STEPS) {
            mUndoStack.removeLast();
        }

        mRedoStack.clear();
        markAsDirty();
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
            ICommand cmd = mRedoStack.pop();
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
        GraphSession activeSession = DocumentManager.INSTANCE.getActiveSession();
        if (activeSession != null && !activeSession.isDirty) {
            activeSession.isDirty = true;
            DocumentManager.INSTANCE.notifyTabChanged();
        }
    }
}
