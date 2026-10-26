package com.mine.geometry_node.client.integration.ai.graph;

import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.client.ui.workspace.surface.ViewportSurface;
import icyllis.modernui.core.Core;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Single UI-thread authority for selecting and describing viewport graph sessions. */
public final class ViewportGraphContextService {
    public static final ViewportGraphContextService INSTANCE =
            new ViewportGraphContextService(UiSurfaceRegistry.INSTANCE);

    private final UiSurfaceRegistry surfaces;

    ViewportGraphContextService(UiSurfaceRegistry surfaces) {
        this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
    }

    public UiContext captureUiContext() {
        requireUiThread();
        List<UiSurfaceRegistry.Snapshot> snapshots = surfaces.snapshots();
        UiSurfaceRegistry.Lease<ViewportSurface> defaultViewport = selectDefault(availableViewports());
        return new UiContext(snapshots, defaultViewport == null ? "" : defaultViewport.id().ref());
    }

    public SurfaceResolution resolveSurface(String surfaceRef) {
        requireUiThread();
        if (surfaceRef == null || surfaceRef.isBlank()) {
            return SurfaceResolution.failure("SURFACE_REF_INVALID", "无效的界面引用: " + surfaceRef);
        }
        UiSurfaceId id;
        try {
            id = UiSurfaceId.parse(surfaceRef);
        } catch (IllegalArgumentException failure) {
            return SurfaceResolution.failure("SURFACE_REF_INVALID", "无效的界面引用: " + surfaceRef);
        }
        UiSurfaceRegistry.Snapshot snapshot = surfaces.snapshot(id).orElse(null);
        if (snapshot == null) {
            return SurfaceResolution.failure("SURFACE_NOT_FOUND", "界面不存在: " + id.ref());
        }
        UiSurfaceRegistry.Lease<ViewportSurface> viewport = id.type() == UiSurfaceType.VIEWPORT
                ? surfaces.lease(id, ViewportSurface.class).orElse(null)
                : null;
        List<GraphSession> sessions = viewport == null ? List.of() : availableSessions(viewport.owner());
        return SurfaceResolution.success(new SurfaceContext(snapshot, viewport, sessions,
                viewport == null ? null : currentAvailableSession(viewport.owner(), sessions)));
    }

    public GraphResolution resolveGraph(String surfaceRef, String sessionId) {
        requireUiThread();
        List<UiSurfaceRegistry.Lease<ViewportSurface>> candidates = availableViewports();
        UiSurfaceRegistry.Lease<ViewportSurface> viewport;
        if (surfaceRef != null && !surfaceRef.isBlank()) {
            UiSurfaceId id;
            try {
                id = UiSurfaceId.parse(surfaceRef);
            } catch (IllegalArgumentException failure) {
                return GraphResolution.failure("SURFACE_REF_INVALID", "无效的界面引用: " + surfaceRef);
            }
            if (id.type() != UiSurfaceType.VIEWPORT) {
                return GraphResolution.failure("SURFACE_TYPE_INVALID", "图工具目标必须是 Viewport，例如 V1");
            }
            viewport = candidates.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
            if (viewport == null) {
                return GraphResolution.failure("VIEWPORT_UNAVAILABLE",
                        "Viewport 不存在、不可见或未打开蓝图: " + id.ref());
            }
        } else {
            if (candidates.isEmpty()) {
                return GraphResolution.failure("NO_ACTIVE_GRAPH", "当前没有可用的蓝图 Viewport");
            }
            viewport = selectDefault(candidates);
            if (viewport == null) {
                return GraphResolution.failure("VIEWPORT_AMBIGUOUS",
                        "存在多个 Viewport，请指定 surface_ref，例如 V1");
            }
        }

        List<GraphSession> sessions = availableSessions(viewport.owner());
        GraphSession session;
        if (sessionId != null && !sessionId.isBlank()) {
            UUID requestedSessionId;
            try {
                requestedSessionId = UUID.fromString(sessionId.strip());
            } catch (IllegalArgumentException failure) {
                return GraphResolution.failure("SESSION_ID_INVALID", "无效的 Graph Session ID: " + sessionId);
            }
            session = sessions.stream()
                    .filter(candidate -> candidate.sessionId().equals(requestedSessionId))
                    .findFirst().orElse(null);
            if (session == null) {
                return GraphResolution.failure("GRAPH_SESSION_UNAVAILABLE",
                        "指定 Graph Session 不在目标 Viewport 的 Tab 中: " + sessionId);
            }
        } else {
            session = currentAvailableSession(viewport.owner(), sessions);
        }
        if (session == null) {
            return GraphResolution.failure("NO_ACTIVE_GRAPH", "目标 Viewport 未打开蓝图");
        }
        return GraphResolution.success(new GraphContext(viewport, session));
    }

    private List<UiSurfaceRegistry.Lease<ViewportSurface>> availableViewports() {
        return surfaces.leases(UiSurfaceType.VIEWPORT, ViewportSurface.class).stream()
                .filter(UiSurfaceRegistry.Lease::visible)
                .filter(lease -> !availableSessions(lease.owner()).isEmpty())
                .toList();
    }

    private static UiSurfaceRegistry.Lease<ViewportSurface> selectDefault(
            List<UiSurfaceRegistry.Lease<ViewportSurface>> candidates) {
        UiSurfaceRegistry.Lease<ViewportSurface> interacted = candidates.stream()
                .filter(candidate -> candidate.interactionSerial() > 0)
                .max(Comparator.comparingLong(UiSurfaceRegistry.Lease::interactionSerial))
                .orElse(null);
        if (interacted != null) return interacted;
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static List<GraphSession> availableSessions(ViewportSurface viewport) {
        return viewport.openGraphSessions().stream()
                .filter(Objects::nonNull)
                .filter(session -> !session.fileReference().isDeleted())
                .toList();
    }

    private static GraphSession currentAvailableSession(ViewportSurface viewport, List<GraphSession> sessions) {
        GraphSession current = viewport.currentGraphSession();
        return current != null && sessions.contains(current) ? current : null;
    }

    private static void requireUiThread() {
        if (!Core.isOnUiThread()) {
            throw new IllegalStateException("Viewport graph context must be accessed on the ModernUI thread");
        }
    }

    public record UiContext(List<UiSurfaceRegistry.Snapshot> surfaces, String defaultViewport) {
        public UiContext {
            surfaces = List.copyOf(surfaces);
            defaultViewport = defaultViewport == null ? "" : defaultViewport;
        }
    }

    public record SurfaceContext(UiSurfaceRegistry.Snapshot snapshot,
                                 UiSurfaceRegistry.Lease<ViewportSurface> viewport,
                                 List<GraphSession> sessions,
                                 GraphSession currentSession) {
        public SurfaceContext {
            Objects.requireNonNull(snapshot, "snapshot");
            sessions = List.copyOf(sessions);
        }
    }

    public record GraphContext(UiSurfaceRegistry.Lease<ViewportSurface> viewport, GraphSession session) {
        public GraphContext {
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(session, "session");
        }
    }

    public record Failure(String code, String message) {}

    public record SurfaceResolution(SurfaceContext context, Failure failure) {
        static SurfaceResolution success(SurfaceContext context) { return new SurfaceResolution(context, null); }
        static SurfaceResolution failure(String code, String message) {
            return new SurfaceResolution(null, new Failure(code, message));
        }
        public boolean ok() { return context != null; }
    }

    public record GraphResolution(GraphContext context, Failure failure) {
        static GraphResolution success(GraphContext context) { return new GraphResolution(context, null); }
        static GraphResolution failure(String code, String message) {
            return new GraphResolution(null, new Failure(code, message));
        }
        public boolean ok() { return context != null; }
    }
}
