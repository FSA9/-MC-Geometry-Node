package com.mine.geometry_node.client.integration.ai.graph;

import com.mine.geometry_node.client.ui.document.GraphSessionViewState;

import com.mine.geometry_node.client.feature.ai.command.CommandResult;
import com.mine.geometry_node.client.feature.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.feature.ai.command.UiSurfaceQueryTarget;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.List;

/** Graph-independent node catalog target available even when no viewport is open. */
public final class NodeCatalogQueryTarget implements GraphQueryTarget, UiSurfaceQueryTarget, com.mine.geometry_node.client.feature.ai.command.HistoryQueryTarget {
    private final GraphQueryService queries = new GraphQueryService((NodeGraph) null);
    private final ViewportGraphContextService graphContexts = ViewportGraphContextService.INSTANCE;

    @Override public CommandResult getHistoryState(String surfaceRef) {
        return com.mine.geometry_node.client.integration.ai.history.HistorySurfaceQueries.get(surfaceRef);
    }

    @Override public boolean hasGraph() { return false; }
    @Override public CommandResult searchNodeTypes(String query, String path, boolean recursive, int offset, int limit) {
        return queries.searchNodeTypes(query, path, recursive, offset, limit);
    }
    @Override public CommandResult browseNodeCatalog(String path, boolean recursive, int offset, int limit) {
        return queries.browseNodeCatalog(path, recursive, offset, limit);
    }
    @Override public CommandResult getNodeTypeDetails(String typeId) { return queries.getNodeTypeDetails(typeId); }
    @Override public CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        return queries.getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }
    @Override public CommandResult queryGraphNodes(java.util.List<String> nodeIds, java.util.List<String> typeIds,
                                                   String directory, String query, String commentFilter,
                                                   String connectionState, java.util.List<String> select,
                                                   String connectionDirection, java.util.List<String> connectionKinds,
                                                   int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult getGraphMetadata(List<String> select) { return unavailable(); }
    @Override public CommandResult queryGraphFrames(List<String> frameIds, String query, List<String> tags,
                                                    String parentFrame, List<String> select, int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult validateGraph(int offset, int limit) { return unavailable(); }
    @Override public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return unavailable();
    }

    @Override
    public CommandResult getUiContext() {
        ViewportGraphContextService.UiContext context = graphContexts.captureUiContext();
        JsonArray surfaces = new JsonArray();
        for (UiSurfaceRegistry.Snapshot snapshot : context.surfaces()) {
            surfaces.add(surfaceData(snapshot.id(), snapshot.visible()));
        }
        JsonObject data = new JsonObject();
        data.addProperty("default_viewport", context.defaultViewport());
        data.add("surfaces", surfaces);
        return CommandResult.success("UI_CONTEXT", "已读取 GeometryNode 界面上下文", data);
    }

    @Override
    public CommandResult getSurfaceContext(String surfaceRef) {
        ViewportGraphContextService.SurfaceResolution resolution = graphContexts.resolveSurface(surfaceRef);
        if (!resolution.ok()) {
            return CommandResult.failure(resolution.failure().code(), resolution.failure().message());
        }
        ViewportGraphContextService.SurfaceContext context = resolution.context();
        UiSurfaceId id = context.snapshot().id();
        UiSurfaceRegistry.Snapshot snapshot = context.snapshot();
        JsonObject data = surfaceData(id, snapshot.visible());
        if (id.type() == UiSurfaceType.VIEWPORT) {
            GraphSession session = context.currentSession();
            data.addProperty("has_graph", session != null);
            JsonArray tabs = new JsonArray();
            for (GraphSession openSession : context.sessions()) {
                BoundGraphScope openScope = BoundGraphScope.capture(openSession.editorContext);
                JsonObject tab = new JsonObject();
                tab.addProperty("tab_name", GraphSessionViewState.of(openSession).tabName);
                tab.addProperty("session_id", openSession.sessionId().toString());
                tab.addProperty("scope_id", openScope.id());
                tab.addProperty("revision", openSession.revision());
                tab.addProperty("current", openSession == session);
                tabs.add(tab);
            }
            data.add("tabs", tabs);
            if (session != null) {
                BoundGraphScope scope = BoundGraphScope.capture(session.editorContext);
                data.addProperty("tab_name", GraphSessionViewState.of(session).tabName);
                data.addProperty("session_id", session.sessionId().toString());
                data.addProperty("scope_id", scope.id());
                data.addProperty("revision", session.revision());
            }
        }
        return CommandResult.success("SURFACE_CONTEXT", "已读取界面 " + id.ref(), data);
    }

    private static JsonObject surfaceData(UiSurfaceId id, boolean visible) {
        JsonObject data = new JsonObject();
        data.addProperty("surface_ref", id.ref());
        data.addProperty("type", id.type().name());
        data.addProperty("visible", visible);
        return data;
    }

    private static CommandResult unavailable() {
        return CommandResult.failure("NO_ACTIVE_GRAPH", "当前没有可用的蓝图 Viewport");
    }
}
