package com.mine.geometry_node.core.engine.system.asset.preview;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible protocol id for a preview capability. */
public final class AssetPreviewKind {
    private static final Map<String, AssetPreviewKind> REGISTERED = new ConcurrentHashMap<>();

    public static final AssetPreviewKind NONE = register("none", false);
    public static final AssetPreviewKind IMAGE = register("image", true);
    public static final AssetPreviewKind SCHEMATIC = register("schematic", true);

    private final String id;
    private final boolean concrete;

    private AssetPreviewKind(String id, boolean concrete) {
        this.id = id;
        this.concrete = concrete;
    }

    public static AssetPreviewKind register(String id) {
        return register(id, true);
    }

    private static AssetPreviewKind register(String id, boolean concrete) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) throw new IllegalArgumentException("preview kind id must not be empty");
        AssetPreviewKind created = new AssetPreviewKind(normalized, concrete);
        AssetPreviewKind existing = REGISTERED.putIfAbsent(normalized, created);
        if (existing != null && existing.concrete != concrete) {
            throw new IllegalArgumentException("preview kind already registered with different semantics: " + normalized);
        }
        return existing != null ? existing : created;
    }

    public static AssetPreviewKind fromId(String id) {
        return REGISTERED.get(normalizeId(id));
    }

    public String id() {
        return id;
    }

    public boolean isConcrete() {
        return concrete;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof AssetPreviewKind other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
