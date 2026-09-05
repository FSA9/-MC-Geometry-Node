package com.mine.geometry_node.client.ui.editor.terminal.command;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.client.ui.workspace.surface.ViewportSurface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves a viewport for one MCP call and caches fixed transaction targets for the run. */
public final class ViewportGraphTargetResolver implements AutoCloseable {
    private final Map<TargetKey, BoundGraphQueryTarget> targets = new HashMap<>();
    private final GraphPatchIdempotencyStore idempotencyStore = new GraphPatchIdempotencyStore();

    public ViewportGraphTargetResolver() {}

    public Resolution resolve(String surfaceRef, String sessionId) {
        List<UiSurfaceRegistry.Lease<ViewportSurface>> candidates = UiSurfaceRegistry.INSTANCE
                .leases(UiSurfaceType.VIEWPORT, ViewportSurface.class).stream()
                .filter(lease -> lease.visible() && !lease.owner().openGraphSessions().isEmpty())
                .toList();

        UiSurfaceRegistry.Lease<ViewportSurface> selected;
        if (surfaceRef != null && !surfaceRef.isBlank()) {
            UiSurfaceId id;
            try {
                id = UiSurfaceId.parse(surfaceRef);
            } catch (IllegalArgumentException failure) {
                return Resolution.failure("SURFACE_REF_INVALID", "无效的界面引用: " + surfaceRef);
            }
            if (id.type() != UiSurfaceType.VIEWPORT) {
                return Resolution.failure("SURFACE_TYPE_INVALID", "图工具目标必须是 Viewport，例如 V1");
            }
            selected = candidates.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
            if (selected == null) {
                return Resolution.failure("VIEWPORT_UNAVAILABLE", "Viewport 不存在、不可见或未打开蓝图: " + id.ref());
            }
        } else {
            if (candidates.isEmpty()) return Resolution.failure("NO_ACTIVE_GRAPH", "当前没有可用的蓝图 Viewport");
            selected = candidates.stream().filter(candidate -> candidate.interactionSerial() > 0)
                    .max(java.util.Comparator.comparingLong(UiSurfaceRegistry.Lease::interactionSerial)).orElse(null);
            if (selected == null) {
                if (candidates.size() != 1) {
                    return Resolution.failure("VIEWPORT_AMBIGUOUS", "存在多个 Viewport，请指定 surface_ref，例如 V1");
                }
                selected = candidates.getFirst();
            }
        }

        ViewportSurface viewport = selected.owner();
        GraphSession session;
        if (sessionId != null && !sessionId.isBlank()) {
            UUID requestedSessionId;
            try {
                requestedSessionId = UUID.fromString(sessionId.strip());
            } catch (IllegalArgumentException failure) {
                return Resolution.failure("SESSION_ID_INVALID", "无效的 Graph Session ID: " + sessionId);
            }
            session = viewport.openGraphSessions().stream()
                    .filter(candidate -> candidate.sessionId().equals(requestedSessionId))
                    .findFirst().orElse(null);
            if (session == null) {
                return Resolution.failure("GRAPH_SESSION_UNAVAILABLE",
                        "指定 Graph Session 不在目标 Viewport 的 Tab 中: " + sessionId);
            }
        } else {
            session = viewport.currentGraphSession();
        }
        if (session == null) return Resolution.failure("NO_ACTIVE_GRAPH", "目标 Viewport 未打开蓝图");
        BoundGraphScope scope = BoundGraphScope.capture(session.editorContext);
        TargetKey key = new TargetKey(selected.id(), selected.generation(), session.sessionId().toString(), scope.id());
        UiSurfaceRegistry.Lease<ViewportSurface> fixedLease = selected;
        String fixedSurfaceRef = selected.id().ref();
        BoundGraphQueryTarget target = targets.computeIfAbsent(key, ignored -> new BoundGraphQueryTarget(
                session, scope, fixedSurfaceRef,
                () -> fixedLease.isCurrent()
                        && fixedLease.owner().openGraphSessions().contains(session)
                        && BoundGraphScope.capture(session.editorContext).equals(scope), idempotencyStore));
        targets.entrySet().removeIf(entry -> !entry.getKey().equals(key) && !entry.getValue().isTargetCurrent());
        return Resolution.success(target);
    }

    @Override
    public void close() {
        targets.clear();
    }

    private record TargetKey(UiSurfaceId id, long generation, String sessionId, String scopeId) {}

    public record Resolution(BoundGraphQueryTarget target, CommandResult failure) {
        static Resolution success(BoundGraphQueryTarget target) { return new Resolution(target, null); }
        static Resolution failure(String code, String message) {
            return new Resolution(null, CommandResult.failure(code, message));
        }
        public boolean ok() { return target != null; }
    }
}
