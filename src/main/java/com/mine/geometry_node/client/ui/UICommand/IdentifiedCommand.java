package com.mine.geometry_node.client.ui.UICommand;

/** Command whose stable ID can be correlated with an MCP graph change and Undo/Redo. */
public interface IdentifiedCommand extends ICommand {
    String changeId();
}
