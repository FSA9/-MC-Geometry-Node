package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.google.gson.JsonObject;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.ai.command.GraphPatchCommandTarget;
import com.mine.geometry_node.client.ai.graph.GraphPatch;
import com.mine.geometry_node.client.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.Objects;

/** Graph target pinned for one resolved viewport/document/scope context. */
public final class BoundGraphQueryTarget implements GraphQueryTarget, GraphPatchCommandTarget {
    private final GraphSession session;
    private final BoundGraphScope scope;
    private final GraphPatchTransactionService transactions;
    private final String surfaceRef;
    private final java.util.function.BooleanSupplier targetValidator;

    public BoundGraphQueryTarget(GraphSession session, BoundGraphScope scope) {
        this(session, scope, "", () -> true);
    }

    public BoundGraphQueryTarget(GraphSession session, BoundGraphScope scope,
                                 String surfaceRef,
                                 java.util.function.BooleanSupplier targetValidator) {
        this(session, scope, surfaceRef, targetValidator, new GraphPatchIdempotencyStore());
    }

    BoundGraphQueryTarget(GraphSession session, BoundGraphScope scope,
                          String surfaceRef,
                          java.util.function.BooleanSupplier targetValidator,
                          GraphPatchIdempotencyStore idempotencyStore) {
        this.session = Objects.requireNonNull(session, "document");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.surfaceRef = surfaceRef == null ? "" : surfaceRef;
        this.targetValidator = Objects.requireNonNull(targetValidator, "targetValidator");
        this.transactions = new GraphPatchTransactionService(session, scope, targetValidator, idempotencyStore);
    }

    @Override public boolean hasGraph() { return DocumentManager.INSTANCE.getSessions().contains(session) && resolve() != null; }
    @Override public CommandResult applyGraphPatch(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        return transactions.apply(patch, cancellation);
    }
    @Override public CommandResult searchNodeTypes(String query, String path, boolean recursive, int offset, int limit) {
        return queries().searchNodeTypes(query, path, recursive, offset, limit);
    }
    @Override public CommandResult browseNodeCatalog(String path, boolean recursive, int offset, int limit) {
        return queries().browseNodeCatalog(path, recursive, offset, limit);
    }
    @Override public CommandResult getNodeTypeDetails(String typeId) { return queries().getNodeTypeDetails(typeId); }
    @Override public CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        return queries().getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }
    @Override public CommandResult queryGraphNodes(java.util.List<String> nodeIds, java.util.List<String> typeIds,
                                                   String directory, String query, String commentFilter,
                                                   String connectionState, java.util.List<String> select,
                                                   String connectionDirection, java.util.List<String> connectionKinds,
                                                   int offset, int limit) {
        return withScope(queries().queryGraphNodes(nodeIds, typeIds, directory, query, commentFilter,
                connectionState, select, connectionDirection, connectionKinds, offset, limit));
    }
    @Override public CommandResult getGraphStats(String typeId, String category, String groupBy,
                                                 int offset, int limit) {
        return withScope(queries().getGraphStats(typeId, category, groupBy, offset, limit));
    }
    private CommandResult withScope(CommandResult result) {
        long responseRevision = revision();
        JsonObject data = result.data();
        data.addProperty("session_id", sessionId());
        data.addProperty("scope_id", scopeId());
        data.addProperty("revision", responseRevision);
        if (!surfaceRef.isEmpty()) data.addProperty("surface_ref", surfaceRef);
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
    public boolean isTargetCurrent() { return targetValidator.getAsBoolean(); }

    private NodeGraph resolve() { return scope.resolve(session.editorContext.getGraph()); }
    private TerminalGraphQueryService queries() {
        NodeGraph graph = resolve();
        if (graph == null) throw new IllegalStateException("bound graph scope is closed");
        return new TerminalGraphQueryService(graph);
    }
}
