package com.mine.geometry_node.core.engine.system.marker;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common registry for server-valid marker types.
 */
public final class MarkerTypeRegistry {
    public static final String DYNAMIC_REGISTRY_ID = "geometry_node:marker_types";
    public static final String DEFAULT_TYPE_ID = "geometry_node:default";
    public static final String DEFAULT_RENDERER_ID = "geometry_node:default";
    public static final MarkerTypeRegistry INSTANCE = new MarkerTypeRegistry();

    private final Map<String, MarkerType> types = new LinkedHashMap<>();

    private MarkerTypeRegistry() {
    }

    public synchronized void register(MarkerType type) {
        if (type == null) {
            throw new IllegalArgumentException("marker type must not be null");
        }
        MarkerType existing = types.get(type.id());
        if (existing != null && !existing.equals(type)) {
            throw new IllegalStateException("Duplicate marker type: " + type.id());
        }
        types.put(type.id(), type);
    }

    @Nullable
    public synchronized MarkerType get(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return types.get(DEFAULT_TYPE_ID);
        }
        try {
            return types.get(MarkerType.normalizeId(id));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public synchronized Collection<MarkerType> all() {
        return Collections.unmodifiableList(new ArrayList<>(types.values()));
    }

    public synchronized List<String> allIds() {
        return types.keySet().stream().toList();
    }

    public synchronized Map<String, String> translationKeys() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        for (MarkerType type : types.values()) {
            labels.put(type.id(), type.translationKey());
        }
        return Collections.unmodifiableMap(labels);
    }
}
