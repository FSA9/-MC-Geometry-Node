package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.ai.command.GraphPatchCommandTarget;
import com.mine.geometry_node.client.ai.graph.GraphPatch;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.Objects;

/** Read-only target pinned to the graph scope selected at Agent run start. */
public final class BoundGraphQueryTarget implements GraphQueryTarget, GraphPatchCommandTarget {
    private final GraphSession session;
    private final BoundGraphScope scope;
    private final GraphPatchTransactionService transactions;

    public BoundGraphQueryTarget(GraphSession session, BoundGraphScope scope,
                                 GraphPatchApprovalPresenter approvalPresenter) {
        this.session = Objects.requireNonNull(session, "session");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.transactions = new GraphPatchTransactionService(session, scope, approvalPresenter);
    }

    @Override public boolean hasGraph() { return DocumentManager.INSTANCE.getSessions().contains(session) && resolve() != null; }
    @Override public CommandResult applyGraphPatch(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        return transactions.apply(patch, cancellation);
    }
    @Override public CommandResult searchNodeTypes(String query, int offset, int limit) { return queries().searchNodeTypes(query, offset, limit); }
    @Override public CommandResult getNodeTypeDetails(String typeId) { return queries().getNodeTypeDetails(typeId); }
    @Override public CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        return queries().getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }
    @Override public CommandResult searchGraphNodes(String query, String typeId, String category,
                                                    String commentFilter, String connectionState,
                                                    int offset, int limit) {
        return queries().searchGraphNodes(query, typeId, category, commentFilter, connectionState, offset, limit);
    }
    @Override public CommandResult getGraphStats(String typeId, String category, String groupBy,
                                                 int offset, int limit) {
        return withScope(queries().getGraphStats(typeId, category, groupBy, offset, limit));
    }
    @Override public CommandResult getNodeDetails(String nodeId) { return queries().getNodeDetails(nodeId); }
    @Override public CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit) {
        return queries().getNodeConnections(nodeId, direction, depth, offset, limit);
    }
    @Override public CommandResult getGraphContext(String focusNodeId, int depth, int offset, int limit) {
        return withScope(queries().getGraphContext(focusNodeId, depth, offset, limit));
    }
    private CommandResult withScope(CommandResult result) {
        long responseRevision = revision();
        JsonObject data = result.data();
        data.addProperty("session_id", sessionId());
        data.addProperty("scope_id", scopeId());
        data.addProperty("revision", responseRevision);
        return new CommandResult(result.ok(), result.code(), result.message(), data, result.diagnostics(),
                responseRevision, result.changeId(), result.clientAction());
    }
    @Override public CommandResult validateGraph(int offset, int limit) { return queries().validateGraph(offset, limit); }
    @Override public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return queries().getPortOptions(nodeId, portId, query, offset, limit);
    }

    public String sessionId() { return session.sessionId().toString(); }
    public String scopeId() { return scope.id(); }
    public long revision() { return session.revision(); }

    private NodeGraph resolve() { return scope.resolve(session.editorContext.getGraph()); }
    private TerminalGraphQueryService queries() {
        NodeGraph graph = resolve();
        if (graph == null) throw new IllegalStateException("bound graph scope is closed");
        return new TerminalGraphQueryService(graph);
    }
}
