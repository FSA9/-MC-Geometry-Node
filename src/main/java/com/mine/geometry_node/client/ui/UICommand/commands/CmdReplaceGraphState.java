package com.mine.geometry_node.client.ui.UICommand.commands;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.IdentifiedCommand;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;

import java.util.Objects;

/** One atomic Undo entry for an approved graph transaction. */
public final class CmdReplaceGraphState implements IdentifiedCommand {
    private final EditorContext context;
    private final String beforeJson;
    private final String afterJson;
    private final String changeId;

    public CmdReplaceGraphState(EditorContext context, String beforeJson, String afterJson, String changeId) {
        this.context = Objects.requireNonNull(context, "context");
        this.beforeJson = Objects.requireNonNull(beforeJson, "beforeJson");
        this.afterJson = Objects.requireNonNull(afterJson, "afterJson");
        this.changeId = Objects.requireNonNull(changeId, "changeId");
    }

    @Override public boolean canExecute() { return !beforeJson.equals(afterJson); }
    @Override public void execute() { context.replaceGraphState(GraphJsonIO.fromJson(afterJson)); }
    @Override public void undo() { context.replaceGraphState(GraphJsonIO.fromJson(beforeJson)); }
    @Override public String changeId() { return changeId; }
}
