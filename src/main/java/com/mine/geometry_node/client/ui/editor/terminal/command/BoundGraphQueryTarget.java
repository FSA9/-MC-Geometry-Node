package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.Objects;

/** Read-only target pinned to the graph scope selected at Agent run start. */
public final class BoundGraphQueryTarget implements GraphQueryTarget {
    private final GraphSession session;
    private final TerminalGraphQueryService queries;

    public BoundGraphQueryTarget(GraphSession session, NodeGraph graph) {
        this.session = Objects.requireNonNull(session, "session");
        this.queries = new TerminalGraphQueryService(Objects.requireNonNull(graph, "graph"));
    }

    @Override public boolean hasGraph() { return DocumentManager.INSTANCE.getSessions().contains(session); }
    @Override public CommandResult searchNodeTypes(String query, int offset, int limit) { return queries.searchNodeTypes(query, offset, limit); }
    @Override public CommandResult searchGraphNodes(String query, int offset, int limit) { return queries.searchGraphNodes(query, offset, limit); }
    @Override public CommandResult getNodeDetails(String nodeId) { return queries.getNodeDetails(nodeId); }
    @Override public CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit) {
        return queries.getNodeConnections(nodeId, direction, depth, offset, limit);
    }
    @Override public CommandResult getGraphContext(String focusNodeId, int depth, int offset, int limit) {
        return queries.getGraphContext(focusNodeId, depth, offset, limit);
    }
    @Override public CommandResult validateGraph(int offset, int limit) { return queries.validateGraph(offset, limit); }
    @Override public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return queries.getPortOptions(nodeId, portId, query, offset, limit);
    }
}
