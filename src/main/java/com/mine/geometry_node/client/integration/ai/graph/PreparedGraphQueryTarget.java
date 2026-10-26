package com.mine.geometry_node.client.integration.ai.graph;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.feature.ai.command.CommandResult;
import com.mine.geometry_node.client.feature.ai.command.GraphQueryTarget;
import net.minecraft.core.RegistryAccess;

import java.util.List;
import java.util.Objects;

/** Read-only graph target containing no references to mutable editor state. */
public final class PreparedGraphQueryTarget implements GraphQueryTarget {
    private final GraphReadSnapshotCache.Handle snapshot;
    private final String sessionId;
    private final String scopeId;
    private final String surfaceRef;
    private final long revision;
    private final RegistryAccess registryAccess;

    PreparedGraphQueryTarget(GraphReadSnapshotCache.Handle snapshot, String sessionId, String scopeId,
                             String surfaceRef, long revision, RegistryAccess registryAccess) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
        this.surfaceRef = surfaceRef == null ? "" : surfaceRef;
        this.revision = revision;
        this.registryAccess = registryAccess;
    }

    @Override public boolean hasGraph() { return true; }

    @Override public CommandResult searchNodeTypes(String query, String path, boolean recursive, int offset, int limit) {
        return queries().searchNodeTypes(query, path, recursive, offset, limit);
    }

    @Override public CommandResult browseNodeCatalog(String path, boolean recursive, int offset, int limit) {
        return queries().browseNodeCatalog(path, recursive, offset, limit);
    }

    @Override public CommandResult getNodeTypeDetails(String typeId) {
        return queries().getNodeTypeDetails(typeId);
    }

    @Override public CommandResult getNodeTypePortOptions(String typeId, String portId, String query,
                                                          int offset, int limit) {
        return queries().getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }

    @Override public CommandResult queryGraphNodes(List<String> nodeIds, List<String> typeIds, String directory,
                                                   String query, String commentFilter, String connectionState,
                                                   List<String> select, String connectionDirection,
                                                   List<String> connectionKinds, int offset, int limit) {
        return withScope(queries().queryGraphNodes(nodeIds, typeIds, directory, query, commentFilter,
                connectionState, select, connectionDirection, connectionKinds, offset, limit));
    }

    @Override public CommandResult getGraphMetadata(List<String> select) {
        return withScope(queries().getGraphMetadata(select));
    }

    @Override public CommandResult queryGraphFrames(List<String> frameIds, String query, List<String> tags,
                                                    String parentFrame, List<String> select, int offset, int limit) {
        return withScope(queries().queryGraphFrames(frameIds, query, tags, parentFrame, select, offset, limit));
    }

    @Override public CommandResult getGraphStats(String typeId, String category, String groupBy,
                                                 int offset, int limit) {
        return withScope(queries().getGraphStats(typeId, category, groupBy, offset, limit));
    }

    @Override public CommandResult validateGraph(int offset, int limit) {
        return withScope(queries().validateGraph(offset, limit));
    }

    @Override public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return withScope(queries().getPortOptions(nodeId, portId, query, offset, limit));
    }

    private GraphQueryService queries() {
        return GraphQueryService.forSnapshot(snapshot.resolve(), registryAccess);
    }

    public long revision() {
        return revision;
    }

    private CommandResult withScope(CommandResult result) {
        JsonObject data = result.data();
        data.addProperty("session_id", sessionId);
        data.addProperty("scope_id", scopeId);
        data.addProperty("revision", revision);
        if (!surfaceRef.isEmpty()) data.addProperty("surface_ref", surfaceRef);
        return new CommandResult(result.ok(), result.code(), result.message(), data, result.diagnostics(),
                revision, result.changeId(), result.clientAction());
    }
}
