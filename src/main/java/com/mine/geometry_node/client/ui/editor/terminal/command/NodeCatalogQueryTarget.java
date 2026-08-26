package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.GraphQueryTarget;
import com.mine.geometry_node.client.ai.command.UiSurfaceQueryTarget;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.surface.UiSurfaceType;
import com.mine.geometry_node.client.ui.surface.ViewportSurface;

import java.util.List;

/** Graph-independent node catalog target available even when no viewport is open. */
final class NodeCatalogQueryTarget implements GraphQueryTarget, UiSurfaceQueryTarget {
    private final TerminalGraphQueryService queries = new TerminalGraphQueryService(null);

    @Override public boolean hasGraph() { return false; }
    @Override public CommandResult searchNodeTypes(String query, int offset, int limit) {
        return queries.searchNodeTypes(query, offset, limit);
    }
    @Override public CommandResult getNodeTypeDetails(String typeId) { return queries.getNodeTypeDetails(typeId); }
    @Override public CommandResult getNodeTypePortOptions(String typeId, String portId, String query, int offset, int limit) {
        return queries.getNodeTypePortOptions(typeId, portId, query, offset, limit);
    }
    @Override public CommandResult searchGraphNodes(String query, String typeId, String category,
                                                    String commentFilter, String connectionState, int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult getGraphStats(String typeId, String category, String groupBy, int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult getNodeDetails(String nodeId) { return unavailable(); }
    @Override public CommandResult getNodeConnections(String nodeId, String direction, int depth, int offset, int limit) {
        return unavailable();
    }
    @Override public CommandResult getGraphContext(String focusNodeId, int depth, int offset, int limit) { return unavailable(); }
    @Override public CommandResult validateGraph(int offset, int limit) { return unavailable(); }
    @Override public CommandResult getPortOptions(String nodeId, String portId, String query, int offset, int limit) {
        return unavailable();
    }

    @Override
    public CommandResult getUiContext() {
        JsonArray surfaces = new JsonArray();
        for (UiSurfaceRegistry.Snapshot snapshot : UiSurfaceRegistry.INSTANCE.snapshots()) {
            surfaces.add(surfaceData(snapshot.id(), snapshot.visible()));
        }
        List<UiSurfaceRegistry.Lease<ViewportSurface>> viewports = UiSurfaceRegistry.INSTANCE
                .leases(UiSurfaceType.VIEWPORT, ViewportSurface.class).stream()
                .filter(lease -> lease.visible() && lease.owner().currentGraphSession() != null).toList();
        String defaultViewport = viewports.stream().filter(lease -> lease.interactionSerial() > 0)
                .max(java.util.Comparator.comparingLong(UiSurfaceRegistry.Lease::interactionSerial))
                .or(() -> viewports.size() == 1 ? java.util.Optional.of(viewports.getFirst())
                        : java.util.Optional.empty())
                .map(lease -> lease.id().ref()).orElse("");
        JsonObject data = new JsonObject();
        data.addProperty("default_viewport", defaultViewport);
        data.add("surfaces", surfaces);
        return CommandResult.success("UI_CONTEXT", "已读取 GeometryNode 界面上下文", data);
    }

    @Override
    public CommandResult getSurfaceContext(String surfaceRef) {
        UiSurfaceId id;
        try {
            id = UiSurfaceId.parse(surfaceRef);
        } catch (IllegalArgumentException failure) {
            return CommandResult.failure("SURFACE_REF_INVALID", "无效的界面引用: " + surfaceRef);
        }
        UiSurfaceRegistry.Snapshot snapshot = UiSurfaceRegistry.INSTANCE.snapshot(id).orElse(null);
        if (snapshot == null) return CommandResult.failure("SURFACE_NOT_FOUND", "界面不存在: " + id.ref());
        JsonObject data = surfaceData(id, snapshot.visible());
        if (id.type() == UiSurfaceType.VIEWPORT) {
            UiSurfaceRegistry.Lease<ViewportSurface> lease = UiSurfaceRegistry.INSTANCE
                    .lease(id, ViewportSurface.class).orElse(null);
            GraphSession session = lease == null ? null : lease.owner().currentGraphSession();
            data.addProperty("has_graph", session != null);
            if (session != null) {
                BoundGraphScope scope = BoundGraphScope.capture(session.editorContext);
                data.addProperty("tab_name", session.tabName);
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
