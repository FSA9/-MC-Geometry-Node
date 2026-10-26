package com.mine.geometry_node.client.integration.ai.graph;

import com.mine.geometry_node.client.feature.ai.command.CommandResult;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.ViewportSurface;
import icyllis.modernui.core.Core;

import java.util.HashMap;
import java.util.Map;

/** Resolves a viewport for one MCP call and caches fixed transaction targets for the run. */
public final class ViewportGraphTargetResolver implements AutoCloseable {
    private final Map<TargetKey, BoundGraphTarget> targets = new HashMap<>();
    private final GraphPatchIdempotencyStore idempotencyStore = new GraphPatchIdempotencyStore();
    private final ViewportGraphContextService graphContexts = ViewportGraphContextService.INSTANCE;

    public ViewportGraphTargetResolver() {}

    public Resolution resolve(String surfaceRef, String sessionId) {
        requireUiThread();
        ViewportGraphContextService.GraphResolution resolution = graphContexts.resolveGraph(surfaceRef, sessionId);
        if (!resolution.ok()) {
            return Resolution.failure(resolution.failure().code(), resolution.failure().message());
        }
        UiSurfaceRegistry.Lease<ViewportSurface> selected = resolution.context().viewport();
        ViewportSurface viewport = selected.owner();
        GraphSession session = resolution.context().session();
        BoundGraphScope scope = BoundGraphScope.capture(session.editorContext);
        TargetKey key = new TargetKey(selected.id(), selected.generation(), session.sessionId().toString(), scope.id());
        UiSurfaceRegistry.Lease<ViewportSurface> fixedLease = selected;
        String fixedSurfaceRef = selected.id().ref();
        BoundGraphTarget target = targets.computeIfAbsent(key, ignored -> new BoundGraphTarget(
                session, scope, fixedSurfaceRef,
                () -> fixedLease.isCurrent()
                        && fixedLease.owner().openGraphSessions().contains(session)
                        && BoundGraphScope.capture(session.editorContext).equals(scope), idempotencyStore));
        targets.entrySet().removeIf(entry -> !entry.getKey().equals(key) && !entry.getValue().isTargetCurrent());
        return Resolution.success(target);
    }

    @Override
    public void close() {
        requireUiThread();
        targets.clear();
    }

    private static void requireUiThread() {
        if (!Core.isOnUiThread()) {
            throw new IllegalStateException("Viewport graph targets must be accessed on the ModernUI thread");
        }
    }

    private record TargetKey(UiSurfaceId id, long generation, String sessionId, String scopeId) {}

    public record Resolution(BoundGraphTarget target, CommandResult failure) {
        static Resolution success(BoundGraphTarget target) { return new Resolution(target, null); }
        static Resolution failure(String code, String message) {
            return new Resolution(null, CommandResult.failure(code, message));
        }
        public boolean ok() { return target != null; }
    }
}
