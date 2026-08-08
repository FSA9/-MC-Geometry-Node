package com.mine.geometry_node.client.marker;

import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only renderer registry keyed by MarkerType.rendererId().
 */
public final class ClientMarkerRendererRegistry {
    private static final Map<String, ClientMarkerRenderer> RENDERERS = new ConcurrentHashMap<>();

    private ClientMarkerRendererRegistry() {
    }

    public static void register(String rendererId, ClientMarkerRenderer renderer) {
        String normalized = MarkerType.normalizeId(rendererId);
        if (renderer == null) {
            throw new IllegalArgumentException("marker renderer must not be null");
        }
        ClientMarkerRenderer previous = RENDERERS.putIfAbsent(normalized, renderer);
        if (previous != null && previous != renderer) {
            throw new IllegalStateException("Marker renderer already registered: " + normalized);
        }
    }

    @Nullable
    public static ClientMarkerRenderer get(@Nullable String rendererId) {
        if (rendererId == null || rendererId.isBlank()) {
            return null;
        }
        try {
            return RENDERERS.get(MarkerType.normalizeId(rendererId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
